package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import androidx.core.net.toUri
import android.os.Build
import android.util.Size
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.SceneProducer
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.CameraHelper
import kotlin.math.abs

/** Encoder-free capture; UVC is supported through Android's external-camera HAL. */
class AndroidSceneCaptureBackend(
    private val context: Context,
    private val projections: SceneProjectionRegistry,
    private val cameraFactory: () -> Camera2ApiManager = { Camera2ApiManager(context) },
) : SceneCaptureBackend {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameras = ConcurrentHashMap<String, Camera2ApiManager>()

    override fun camera(sourceId: String): Camera2ApiManager? = cameras[sourceId]
    private var requestedSources: List<SceneSource> = emptyList()
    private var cameraRequests: Set<String> = emptySet()
    private var duplicateCameraIds: Set<String> = emptySet()

    override fun configure(sources: List<SceneSource>) {
        requestedSources = sources.distinctBy { it.id }
    }

    /** Refresh on open/retry so hotplug works without querying camera metadata every frame. */
    private fun refreshCameraRequests() {
        val cameras = requestedSources.filter { it.type == SceneSourceType.CAMERA || it.type == SceneSourceType.UVC }
            .mapNotNull { runCatching { cameraId(it) }.getOrNull() }
        cameraRequests = cameras.toSet()
        duplicateCameraIds = cameras.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }

    override fun create(source: SceneSource, target: SceneCaptureTarget,
        failed: (Boolean) -> Unit): SceneProducer = when (source.type) {
        SceneSourceType.CAMERA, SceneSourceType.UVC -> {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw SceneProducerPermissionException()
            }
            refreshCameraRequests()
            val cameraId = cameraId(source)
            check(cameraId !in duplicateCameraIds) { "Duplicate camera capture; duplicate the source layer instead" }
            if (cameraRequests.size > 1) {
                check(Build.VERSION.SDK_INT >= 30 && cameraManager.concurrentCameraIds.any { it.containsAll(cameraRequests) }) {
                    "Requested concurrent cameras are unsupported on this device"
                }
            }
            val sizes = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val size = selectSize(sizes, target.width, target.height)
            target.texture.setDefaultBufferSize(size.width, size.height)
            CameraProducer(source.id, cameraId, target, failed)
        }
        SceneSourceType.SCREEN_CAPTURE -> object : SceneProducer {
            private var lease: Closeable? = null
            override fun start() {
                lease = projections.acquire(source.id, target.surface, target.width, target.height,
                    context.resources.displayMetrics.densityDpi) { failed(true) }
            }
            override fun stop() { lease?.close(); lease = null }
            override fun release() = stop()
        }
        SceneSourceType.VIDEO -> VideoProducer(context, source.locator, target, failed)
        else -> error("Source does not capture a Surface")
    }

    private fun cameraId(source: SceneSource): String {
        val cameras = cameraManager.cameraIdList
        if (source.locator.isNotBlank()) {
            check(source.locator in cameras) { "Camera is unavailable" }
            if (source.type == SceneSourceType.UVC) {
                check(cameraManager.getCameraCharacteristics(source.locator)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    "UVC source requires an external-camera HAL device"
                }
            }
            return source.locator
        }
        val facing = if (source.type == SceneSourceType.UVC) CameraCharacteristics.LENS_FACING_EXTERNAL
            else CameraCharacteristics.LENS_FACING_BACK
        return cameras.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: error("Requested camera is unavailable")
    }

    override fun release() {
        cameras.values.toList().forEach { runCatching { it.closeCamera(false) } }
        cameras.clear()
    }

    /** Reuse the same Camera2 implementation and controls as the existing preview. */
    private inner class CameraProducer(
        private val sourceId: String,
        private val cameraId: String,
        private val target: SceneCaptureTarget,
        private val failed: (Boolean) -> Unit,
    ) : SceneProducer {
        private val closed = AtomicBoolean(false)
        private val started = AtomicBoolean(false)
        private val camera = cameraFactory()

        init {
            cameras[sourceId] = camera
            camera.setCameraCallbacks(object : CameraCallbacks {
                override fun onCameraOpened() = Unit // Health is acknowledged by the first texture frame.
                override fun onCameraChanged(facing: CameraHelper.Facing) = Unit
                override fun onCameraError(error: String) = reportFailure()
                override fun onCameraDisconnected() = reportFailure()
            })
        }

        private fun reportFailure() {
            if (!closed.get()) failed(context.checkSelfPermission(Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
        }

        override fun start() {
            check(!closed.get() && started.compareAndSet(false, true))
            // The bounded buffer dimensions were selected before creating the producer.
            // Surface preparation creates no encoder and retains the provider's ownership.
            camera.prepareCamera(target.surface, 30)
            camera.openCameraId(cameraId)
        }

        override fun stop() {
            if (!closed.compareAndSet(false, true)) return
            cameras.remove(sourceId, camera)
            try { camera.setCameraCallbacks(null) }
            finally { camera.closeCamera(false) }
        }
        override fun release() = stop()
    }

    private class VideoProducer(
        private val context: Context,
        private val locator: String,
        private val target: SceneCaptureTarget,
        private val failed: (Boolean) -> Unit,
    ) : SceneProducer {
        private val lock = Any()
        private var player: MediaPlayer? = null
        override fun start() {
            val uri = locator.toUri()
            require(uri.scheme in listOf("content", "file") || uri.scheme == null) {
                "Video sources require a local document"
            }
            synchronized(lock) {
                val candidate = MediaPlayer()
                player = candidate
                candidate.setSurface(target.surface)
                candidate.isLooping = true
                candidate.setVolume(0f, 0f) // Existing audio pipeline remains the only audio source.
                candidate.setOnPreparedListener {
                    synchronized(lock) { if (player === it) it.start() }
                }
                candidate.setOnVideoSizeChangedListener { current, width, height ->
                    synchronized(lock) {
                        if (player === current && width > 0 && height > 0) {
                            target.texture.setDefaultBufferSize(width, height)
                        }
                    }
                }
                candidate.setOnErrorListener { _, _, _ -> failed(false); true }
                candidate.setDataSource(context, uri)
                candidate.prepareAsync()
            }
        }
        override fun stop() {
            synchronized(lock) { player?.release(); player = null }
        }
        override fun release() = stop()
    }

    companion object {
        /** Prefer 720p-or-smaller secondary captures and a matching canvas aspect ratio. */
        internal fun selectSize(sizes: Array<out Size>, width: Int, height: Int): Size {
            require(sizes.isNotEmpty()) { "Camera has no SurfaceTexture output sizes" }
            val ratio = width.toDouble() / height
            val bounded = sizes.filter { it.width <= 1280 && it.height <= 1280 }.ifEmpty { sizes.toList() }
            return bounded.minBy { size ->
                abs(size.width.toDouble() / size.height - ratio) * 1000 +
                    abs(size.width.toLong() * size.height - minOf(width, 1280).toLong() * minOf(height, 720)) / 1_000_000.0
            }
        }
    }
}
