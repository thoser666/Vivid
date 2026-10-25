package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import androidx.annotation.RequiresApi
import android.media.MediaPlayer
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import com.vivid.feature.streaming.AndroidCameraCaptureCapabilities
import java.util.concurrent.atomic.AtomicInteger
import android.util.Size
import android.util.Range
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
import timber.log.Timber

/** Encoder-free Camera2, raw USB, projection and local-video capture. */
class AndroidSceneCaptureBackend(
    private val context: Context,
    private val projections: SceneProjectionRegistry,
    private val deviceRotationDegrees: (() -> Int)? = null,
    private val dispatchCameraTask: ((() -> Unit) -> Unit)? = null,
    private val cameraFactory: () -> Camera2ApiManager = { Camera2ApiManager(context) },
) : SceneCaptureBackend {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameras = ConcurrentHashMap<String, CameraProducer>()
    private var displayRotation: SceneDisplayRotation? = null
    private var usb: AndroidSceneUsbCaptureBackend? = null
    private fun usbBackend(): AndroidSceneUsbCaptureBackend = usb ?: AndroidSceneUsbCaptureBackend(context).also {
        usb = it
        it.configure(requestedSources)
    }

    override fun camera(sourceId: String): Camera2ApiManager? = cameras[sourceId]?.camera
    override fun samplingRotationDegrees(sourceId: String, timestamp: Long): Int? {
        val capture = cameras[sourceId] ?: return 0
        return capture.sampling.rotation(timestamp)
    }

    private fun currentRotationDegrees(): Int = deviceRotationDegrees?.invoke() ?: (
        displayRotation ?: SceneDisplayRotation(context).also { displayRotation = it }).degrees
    private val mainHandler by lazy { Handler(context.mainLooper) }
    private var concurrentBudget = false
    private var requestedSources: List<SceneSource> = emptyList()
    private var cameraRequests: Set<String> = emptySet()
    private var duplicateCameraIds: Set<String> = emptySet()

    override fun configure(sources: List<SceneSource>) {
        requestedSources = sources.distinctBy { it.id }
        usb?.configure(requestedSources)
        val concurrent = requestedSources.filter { it.type == SceneSourceType.CAMERA || it.type == SceneSourceType.UVC }
            .map { it.locator.ifBlank { "default:${it.type}" } }.distinct().size > 1
        if (concurrent != concurrentBudget) {
            concurrentBudget = concurrent
            cameras.values.forEach { it.updateConcurrentBudget(concurrentBudget) }
        }
    }

    override fun setTargetFrameRate(frameRate: Int) {
        require(frameRate in 1..120)
        cameras.values.forEach { it.requestFrameRate(frameRate, concurrentBudget) }
        usb?.setTargetFrameRate(frameRate)
    }

    override fun frameReady(sourceId: String) { cameras[sourceId]?.frameReady() }

    private fun dispatch(action: () -> Unit) {
        val custom = dispatchCameraTask
        if (custom != null) custom(action) else mainHandler.post(action)
    }

    /** Refresh on open/retry so hotplug works without querying camera metadata every frame. */
    private fun refreshCameraRequests() {
        val cameras = requestedSources.filter { it.type == SceneSourceType.CAMERA || (it.type == SceneSourceType.UVC && !SceneUsbLocator.isRawLocator(it.locator)) }
            .mapNotNull { runCatching { cameraId(it) }.getOrNull() }
        cameraRequests = cameras.toSet()
        duplicateCameraIds = cameras.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }

    override fun create(source: SceneSource, target: SceneCaptureTarget,
        failed: (Boolean) -> Unit): SceneProducer = if (source.type == SceneSourceType.UVC && SceneUsbLocator.isRawLocator(source.locator)) {
        usbBackend().create(source, target, failed)
    } else when (source.type) {
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
            val metadata = cameraManager.getCameraCharacteristics(cameraId)
            val sensor = metadata.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val external = source.type == SceneSourceType.UVC ||
                metadata.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
            // A USB/HDMI source is independent of the phone's physical pose.
            val displayDegrees = if (external) ({ 0 }) else ::currentRotationDegrees
            val swapped = SceneCameraGeometry.swapsBufferAxes(sensor, displayDegrees())
            val sizes = metadata.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val size = selectSize(sizes, if (swapped) target.height else target.width,
                if (swapped) target.width else target.height, concurrent = concurrentBudget)
            target.texture.setDefaultBufferSize(size.width, size.height)
            val needsRotationMetadata = Build.VERSION.SDK_INT >= 31 &&
                metadata.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)
                    ?.any { it != CaptureRequest.SCALER_ROTATE_AND_CROP_NONE } == true
            CameraProducer(source.id, cameraId, target, failed,
                SceneCameraSampling(displayDegrees, needsRotationMetadata), needsRotationMetadata, metadata,
                size, concurrentBudget)
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
        runCatching { usb?.release() }.onFailure { Timber.w(it, "Could not release scene USB capture") }
        usb = null
        cameras.values.toList().forEach { runCatching { it.stop() } }
        cameras.clear()
        displayRotation?.close()
        displayRotation = null
    }

    /** Reuse the same Camera2 implementation and controls as the existing preview. */
    private inner class CameraProducer(
        private val sourceId: String,
        private val cameraId: String,
        private val target: SceneCaptureTarget,
        private val failed: (Boolean) -> Unit,
        val sampling: SceneCameraSampling,
        private val needsRotationMetadata: Boolean,
        private val metadata: CameraCharacteristics,
        private val size: Size,
        initialConcurrent: Boolean,
    ) : SceneProducer {
        private val closed = AtomicBoolean(false)
        private val started = AtomicBoolean(false)
        val camera = cameraFactory()
        private val rawRequested = AtomicBoolean(false)
        private val capabilities = AndroidCameraCaptureCapabilities(metadata)
        @Volatile private var lastOutputRate = target.frameRate
        @Volatile private var lastConcurrent = initialConcurrent
        private val desiredFrameRate = AtomicInteger(resolveSceneCaptureFrameRate(size.width, size.height,
            target.frameRate, initialConcurrent, capabilities))
        @Volatile private var preparedFrameRate = desiredFrameRate.get()
        private val rateTaskQueued = AtomicBoolean(false)
        private val sessionReady = AtomicBoolean(false)
        private val failedRateUpdates = AtomicInteger(0)

        init {
            cameras[sourceId] = this
            camera.setCameraCallbacks(object : CameraCallbacks {
                override fun onCameraOpened() {
                    // Health still requires a texture frame with a known sampling transform.
                    sessionReady.set(false)
                    rawRequested.set(false)
                    if (needsRotationMetadata && Build.VERSION.SDK_INT >= 31) requestRawOrientation()
                    requestFrameRate(lastOutputRate, lastConcurrent, force = true)
                }
                override fun onCameraChanged(facing: CameraHelper.Facing) = Unit
                override fun onCameraError(error: String) = reportFailure()
                override fun onCameraDisconnected() = reportFailure()
            })
            if (needsRotationMetadata && Build.VERSION.SDK_INT >= 31) trackRotationMetadata()
        }

        @RequiresApi(31)
        private fun requestRawOrientation() {
            if (!closed.get() && !rawRequested.get() && camera.setCustomRequest {
                it.set(CaptureRequest.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
            }) rawRequested.set(true)
        }

        @RequiresApi(31)
        private fun trackRotationMetadata() {
            camera.setCustomOnCaptureCompletedCallback capture@{ _, request, result ->
                if (closed.get()) return@capture
                val rotation = when (result.get(CaptureResult.SCALER_ROTATE_AND_CROP)) {
                    CaptureRequest.SCALER_ROTATE_AND_CROP_NONE -> 0
                    CaptureRequest.SCALER_ROTATE_AND_CROP_90 -> 90
                    CaptureRequest.SCALER_ROTATE_AND_CROP_180 -> 180
                    CaptureRequest.SCALER_ROTATE_AND_CROP_270 -> 270
                    else -> null
                }
                val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                if (rotation != null && timestamp != null) sampling.record(timestamp, rotation,
                    request.get(CaptureRequest.SCALER_ROTATE_AND_CROP) == CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
                requestRawOrientation() // The capture session is now configured; retry an early request.
            }
        }

        fun updateConcurrentBudget(concurrent: Boolean) = requestFrameRate(lastOutputRate, concurrent)

        @Synchronized
        fun requestFrameRate(outputRate: Int, concurrent: Boolean, force: Boolean = false) {
            if (closed.get()) return
            if (force || outputRate != lastOutputRate || concurrent != lastConcurrent) {
                lastOutputRate = outputRate
                lastConcurrent = concurrent
                desiredFrameRate.set(resolveSceneCaptureFrameRate(size.width, size.height,
                    outputRate, concurrent, capabilities))
                failedRateUpdates.set(0)
            }
            scheduleFrameRateUpdate()
        }

        fun frameReady() {
            if (!closed.get() && sessionReady.compareAndSet(false, true)) scheduleFrameRateUpdate()
        }

        private fun scheduleFrameRateUpdate() {
            if (closed.get() || !started.get() || !sessionReady.get() || desiredFrameRate.get() == preparedFrameRate ||
                !rateTaskQueued.compareAndSet(false, true)) return
            dispatch {
                try {
                    if (!closed.get() && started.get() && sessionReady.get()) {
                        val wanted = desiredFrameRate.get()
                        if (wanted != preparedFrameRate) {
                            val range = selectSceneCaptureFpsRange(metadata.get(
                                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty(), wanted)
                            if (range == null) {
                                // No known live request is safe. Keep the existing camera settings
                                // and avoid retrying missing metadata on every frame.
                                desiredFrameRate.compareAndSet(wanted, preparedFrameRate)
                            } else if (applyFrameRateRange(range)) {
                                camera.prepareCamera(target.surface, wanted)
                                preparedFrameRate = wanted
                                failedRateUpdates.set(0)
                            } else if (desiredFrameRate.get() == wanted && failedRateUpdates.incrementAndGet() >= 3) {
                                desiredFrameRate.compareAndSet(wanted, preparedFrameRate)
                                Timber.w("Could not change scene capture frame rate; retaining %d fps", preparedFrameRate)
                            }
                        }
                    }
                } finally { rateTaskQueued.set(false) }
            }
        }

        private fun applyFrameRateRange(range: Range<Int>): Boolean {
            var previous: Range<Int>? = null
            var changed = false
            val applied = camera.setCustomRequest {
                previous = it.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
                it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                changed = true
            }
            if (!applied && changed) {
                // RootEncoder keeps the shared builder even if the request fails. Restore
                // its old value so a later focus/exposure update cannot apply a stale rate.
                camera.setCustomRequest { it.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, previous) }
            }
            return applied
        }

        private fun reportFailure() {
            if (!closed.get()) failed(context.checkSelfPermission(Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
        }

        override fun start() {
            check(!closed.get() && started.compareAndSet(false, true))
            // The bounded buffer dimensions were selected before creating the producer.
            // Surface preparation creates no encoder and retains the provider's ownership.
            val fps = desiredFrameRate.get()
            camera.prepareCamera(target.surface, fps)
            preparedFrameRate = fps
            camera.openCameraId(cameraId)
        }

        override fun stop() {
            if (!closed.compareAndSet(false, true)) return
            cameras.remove(sourceId, this)
            try {
                camera.setCameraCallbacks(null)
                camera.setCustomOnCaptureCompletedCallback(null)
            } finally {
                try { camera.closeCamera(false) }
                finally {
                    if (cameras.isEmpty()) {
                        displayRotation?.close()
                        displayRotation = null
                    }
                }
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
        /** One camera may match the output; concurrent sessions retain a bounded 720p budget. */
        internal fun selectSize(sizes: Array<out Size>, width: Int, height: Int, concurrent: Boolean = true): Size {
            require(sizes.isNotEmpty()) { "Camera has no SurfaceTexture output sizes" }
            require(width > 0 && height > 0)
            val ratio = width.toDouble() / height
            val maxPixels = if (concurrent) 1280L * 720 else minOf(width.toLong() * height, 3840L * 2160)
            val bounded = sizes.filter { it.width.toLong() * it.height <= maxPixels &&
                maxOf(it.width, it.height) <= 4096 }
            // An OEM may expose no size within our budget. Choose its smallest output instead of 4K by aspect.
            if (bounded.isEmpty()) {
                val smallest = sizes.minBy { it.width.toLong() * it.height }
                check(maxOf(smallest.width, smallest.height) <= 4096 &&
                    smallest.width.toLong() * smallest.height <= 3840L * 2160) {
                    "Camera has no output within the capture memory budget"
                }
                return smallest
            }
            return bounded.minBy { size ->
                abs(size.width.toDouble() / size.height - ratio) * 1000 +
                    abs(size.width.toLong() * size.height - maxPixels) / 1_000_000.0
            }
        }
    }
}
