package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaPlayer
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.SceneProducer
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import java.io.Closeable
import kotlin.math.abs

/** Encoder-free capture; UVC is supported through Android's external-camera HAL. */
class AndroidSceneCaptureBackend(
    private val context: Context,
    private val projections: SceneProjectionRegistry,
) : SceneCaptureBackend {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val callbacksThread = HandlerThread("VividSceneCapture").apply { start() }
    private val handler = Handler(callbacksThread.looper)
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
            CameraProducer(cameraManager, cameraId, target, handler, failed)
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

    override fun release() { callbacksThread.quitSafely() }

    private class CameraProducer(
        private val manager: CameraManager,
        private val cameraId: String,
        private val target: SceneCaptureTarget,
        private val handler: Handler,
        private val failed: (Boolean) -> Unit,
    ) : SceneProducer {
        private val lock = Any()
        private var active = false
        private var device: CameraDevice? = null
        private var session: CameraCaptureSession? = null

        override fun start() {
            synchronized(lock) {
                check(!active)
                active = true
            }
            // Permission is checked by the backend and can still be revoked between calls.
            @Suppress("MissingPermission")
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    synchronized(lock) {
                        if (!active) { camera.close(); return }
                        device = camera
                        try { createSession(camera) } catch (_: Exception) { fail(camera, false) }
                    }
                }
                override fun onDisconnected(camera: CameraDevice) = fail(camera, false)
                override fun onError(camera: CameraDevice, error: Int) = fail(camera, false)
            }, handler)
        }

        @Suppress("DEPRECATION")
        private fun createSession(camera: CameraDevice) {
            camera.createCaptureSession(listOf(target.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    synchronized(lock) {
                        if (!active || device !== camera) { configured.close(); return }
                        session = configured
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(target.surface)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            }.build()
                            configured.setRepeatingRequest(request, null, handler)
                        } catch (_: SecurityException) { fail(camera, true) }
                        catch (_: Exception) { fail(camera, false) }
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    fail(camera, false)
                }
            }, handler)
        }

        private fun fail(camera: CameraDevice, permission: Boolean) {
            synchronized(lock) {
                camera.close()
                if (!active || (device != null && device !== camera)) return
                failed(permission)
            }
        }

        override fun stop() {
            synchronized(lock) {
                active = false
                session?.close()
                session = null
                device?.close()
                device = null
            }
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
