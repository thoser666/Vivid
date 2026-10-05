package com.vivid.feature.streaming

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface ExperimentalCapture : AutoCloseable {
    /** Wait for decoded camera frames, before any MP4/RTMP consumer starts. Runs off the UI thread. */
    fun prepare(): Boolean
}

/** High-speed encoder Surface -> AVC -> hardware decoder -> Vivid's existing GL input. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class HighSpeedGlBridge(
    private val manager: CameraManager,
    private val cameraId: String,
    private val profile: ExperimentalCameraProfile,
    private val glOutput: Surface,
    private val onFailure: (Throwable) -> Unit,
) : ExperimentalCapture {
    private val thread = HandlerThread("VividHighSpeedCapture")
    private lateinit var handler: Handler
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private var input: Surface? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private val closed = AtomicBoolean(false)
    private val failure = AtomicReference<Throwable?>(null)
    private val ready = CountDownLatch(1)
    private val released = CountDownLatch(1)
    private var pending = -1
    private val pendingInfo = MediaCodec.BufferInfo()
    private val decodedInfo = MediaCodec.BufferInfo()
    private var decodedFrames = 0

    override fun prepare(): Boolean {
        thread.start()
        handler = Handler(thread.looper)
        handler.post { runCatching { open() }.onFailure(::fail) }
        // This also verifies decoder preparation and camera delivery, not just session acceptance.
        return ready.await(8, TimeUnit.SECONDS) && failure.get() == null && !closed.get()
    }

    @SuppressLint("MissingPermission") // Runtime permission is checked by the recording/stream start flow.
    private fun open() {
        if (closed.get()) return
        val preset = profile.output.preset
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, preset.width, preset.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 18_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 60f)
        }
        val codec = MediaCodec.createByCodecName(profile.captureEncoder).also { encoder = it }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        input = codec.createInputSurface()
        codec.start()
        handler.post(pump)
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if (closed.get()) { device.close(); return }
                camera = device
                createSession(device)
            }
            override fun onDisconnected(device: CameraDevice) {
                device.close()
                if (!closed.get()) fail(IllegalStateException("High-speed camera disconnected"))
            }
            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                if (!closed.get()) fail(IllegalStateException("High-speed camera error $error"))
            }
        }, handler)
    }

    @Suppress("DEPRECATION") // minSdk path; single encoder surface is required for full-rate capture.
    private fun createSession(device: CameraDevice) {
        runCatching {
            device.createConstrainedHighSpeedCaptureSession(listOf(requireNotNull(input)),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        if (closed.get()) { configured.close(); return }
                        session = configured
                        runCatching {
                            val highSpeed = configured as CameraConstrainedHighSpeedCaptureSession
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(requireNotNull(input))
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(profile.captureFps, profile.captureFps))
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }.build()
                            highSpeed.setRepeatingBurst(highSpeed.createHighSpeedRequestList(request), null, handler)
                        }.onFailure(::fail)
                    }
                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        configured.close()
                        fail(IllegalStateException("High-speed session preparation failed"))
                    }
                }, handler)
        }.onFailure(::fail)
    }

    // Bounded nonblocking input/output steps prevent decoder backpressure from stopping the drain.
    private val pump = object : Runnable {
        override fun run() {
            if (closed.get() || failure.get() != null) return
            runCatching { pumpOnce() }.onFailure(::fail)
            if (!closed.get() && failure.get() == null) handler.postDelayed(this, 1)
        }
    }

    private fun pumpOnce() {
        val source = encoder ?: return
        if (pending < 0) {
            pending = source.dequeueOutputBuffer(pendingInfo, 0)
            if (pending == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                check(decoder == null) { "Unexpected bridge format change" }
                val format = source.outputFormat
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                decoder = MediaCodec.createByCodecName(profile.decoder).also {
                    it.configure(format, glOutput, null, 0)
                    it.start()
                }
            }
        }
        if (pending >= 0) {
            if (pendingInfo.size == 0 || pendingInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                source.releaseOutputBuffer(pending, false)
                pending = -1
            } else {
                val target = requireNotNull(decoder)
                val index = target.dequeueInputBuffer(0)
                if (index >= 0) {
                    val data = requireNotNull(source.getOutputBuffer(pending)).duplicate().apply {
                        position(pendingInfo.offset)
                        limit(pendingInfo.offset + pendingInfo.size)
                    }
                    requireNotNull(target.getInputBuffer(index)).apply { clear(); put(data) }
                    target.queueInputBuffer(index, 0, pendingInfo.size, pendingInfo.presentationTimeUs, 0)
                    source.releaseOutputBuffer(pending, false)
                    pending = -1
                }
            }
        }
        decoder?.let { target ->
            val index = target.dequeueOutputBuffer(decodedInfo, 0)
            if (index >= 0) {
                target.releaseOutputBuffer(index, true)
                if (++decodedFrames >= 3) ready.countDown()
            }
        }
    }

    private fun fail(error: Throwable) {
        if (closed.get() || !failure.compareAndSet(null, error)) return
        ready.countDown()
        onFailure(error)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        ready.countDown()
        if (!::handler.isInitialized) return
        handler.removeCallbacks(pump)
        val release = Runnable {
            try {
                runCatching { session?.stopRepeating() }
                runCatching { session?.close() }
                runCatching { camera?.close() }
                listOf(decoder, encoder).forEach { codec ->
                    runCatching { codec?.stop() }
                    runCatching { codec?.release() }
                }
                runCatching { input?.release() }
            } finally {
                released.countDown()
                thread.quitSafely()
            }
        }
        if (Thread.currentThread() === thread) release.run() else {
            handler.post(release)
            check(released.await(3, TimeUnit.SECONDS)) { "High-speed capture did not release" }
            thread.join(500)
        }
    }
}
