package com.vivid.feature.streaming.source.uvc

import android.hardware.usb.UsbDevice
import android.view.Surface
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Injectable boundary over UVCAndroid 1.0.13 (Apache-2.0). No device is auto-selected. */
interface UvcCameraGateway {
    fun setListener(listener: Listener?)
    fun deviceList(): List<UsbDevice>
    fun select(device: UsbDevice)
    fun supportedFormats(): List<UvcFormatInfo>
    fun actualMode(): UvcCaptureMode?
    /** Null opens the helper's default camera so it can expose native formats. */
    fun open(mode: UvcCaptureMode?)
    fun closeCamera()
    fun addSurface(surface: Surface): Boolean
    fun removeSurface(surface: Surface): Boolean
    fun startPreview(): Boolean
    fun stopPreview()
    fun release()

    interface Listener {
        fun onAttach(device: UsbDevice)
        fun onDeviceOpen(device: UsbDevice, firstOpen: Boolean)
        fun onCameraOpen(device: UsbDevice)
        fun onFirstFrame(device: UsbDevice)
        fun onCameraClose(device: UsbDevice)
        fun onDeviceClose(device: UsbDevice)
        fun onDetach(device: UsbDevice)
        fun onCancel(device: UsbDevice)
        fun onError(device: UsbDevice, failure: UvcFailure)
    }
}

/** Helper calls are queued; descriptor reads and native initialization run off the UI thread. */
class HerohanUvcCameraGateway : UvcCameraGateway {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var listener: UvcCameraGateway.Listener? = null
    @Volatile private var helper: ICameraHelper? = null
    @Volatile private var formats: List<UvcFormatInfo> = emptyList()
    @Volatile private var supportedSizes: List<Size> = emptyList()
    @Volatile private var mode: UvcCaptureMode? = null
    private val released = AtomicBoolean(false)
    private val firstFrame = AtomicBoolean(false)

    private val callback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) { listener?.onAttach(device) }
        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            execute(device) {
                listener?.onDeviceOpen(device, isFirstOpen)
            }
        }
        override fun onCameraOpen(device: UsbDevice) {
            execute(device) {
                // UVCAndroid exposes supportedSizeList only after native
                // openCamera() completes; onDeviceOpen is too early.
                supportedSizes = helper?.supportedSizeList.orEmpty().toList()
                formats = supportedSizes.flatMap { size ->
                    size.fpsList.orEmpty().ifEmpty { listOf(size.fps) }.map { fps ->
                        UvcFormatInfo(size.type, size.width, size.height, fps)
                    }
                }
                mode = helper?.previewSize?.let { size ->
                    mapUvcModes(listOf(UvcFormatInfo(size.type, size.width, size.height, size.fps))).singleOrNull()
                }
                firstFrame.set(false)
                // RAW callback only observes delivery; frames stay on the native Surface/GPU path.
                helper?.setFrameCallback({ frame ->
                    if (frame != null && frame.remaining() > 0 && firstFrame.compareAndSet(false, true)) {
                        listener?.onFirstFrame(device)
                    }
                }, UVCCamera.PIXEL_FORMAT_RAW)
                listener?.onCameraOpen(device)
            }
        }
        override fun onCameraClose(device: UsbDevice) { listener?.onCameraClose(device) }
        override fun onDeviceClose(device: UsbDevice) { listener?.onDeviceClose(device) }
        override fun onDetach(device: UsbDevice) { listener?.onDetach(device) }
        override fun onCancel(device: UsbDevice) { listener?.onCancel(device) }
        override fun onError(device: UsbDevice, e: CameraException) {
            listener?.onError(device, if (e.code == CameraException.CAMERA_OPEN_ERROR_BUSY) UvcFailure.DeviceBusy else UvcFailure.OpenFailed)
        }
    }

    private fun execute(device: UsbDevice, action: () -> Unit) {
        if (released.get()) return
        try { worker.execute {
            if (!released.get()) {
                try { action() }
                catch (_: LinkageError) { listener?.onError(device, UvcFailure.NativeLibraryMissing) }
                catch (_: Exception) { listener?.onError(device, UvcFailure.OpenFailed) }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            if (!released.get()) listener?.onError(device, UvcFailure.OpenFailed)
        }
    }

    override fun setListener(listener: UvcCameraGateway.Listener?) { this.listener = listener }
    override fun deviceList(): List<UsbDevice> = emptyList() // Enumeration belongs to UsbManager.
    override fun select(device: UsbDevice) = execute(device) {
        Class.forName("com.serenegiant.usb.UVCCamera") // Load native ABI here, inside the guarded worker.
        val camera = CameraHelper()
        helper = camera
        camera.setStateCallback(callback)
        camera.selectDevice(device)
    }
    override fun supportedFormats(): List<UvcFormatInfo> = formats
    override fun actualMode(): UvcCaptureMode? = mode
    override fun open(mode: UvcCaptureMode?) {
        val camera = checkNotNull(helper)
        if (mode == null) {
            this.mode = null
            camera.openCamera()
            return
        }
        val type = if (mode.format == UvcPixelFormat.MJPEG) UVCCamera.UVC_VS_FRAME_MJPEG else UVCCamera.UVC_VS_FRAME_UNCOMPRESSED
        val advertised = supportedSizes.firstOrNull {
            it.type == type && it.width == mode.width && it.height == mode.height &&
                (it.fpsList.orEmpty().contains(mode.fps) || (it.fpsList.isNullOrEmpty() && it.fps == mode.fps))
        }
        checkNotNull(advertised) { "Selected USB mode is no longer advertised" }
        val fpsList = advertised.fpsList.orEmpty().ifEmpty { listOf(mode.fps) }
        camera.previewSize = Size(type, mode.width, mode.height, mode.fps, fpsList)
        this.mode = mode
    }
    override fun closeCamera() { helper?.closeCamera() }
    override fun addSurface(surface: Surface): Boolean {
        val camera = helper ?: return false
        camera.addSurface(surface, false)
        return true
    }
    override fun removeSurface(surface: Surface): Boolean {
        val camera = helper ?: return false
        camera.removeSurface(surface)
        return true
    }
    override fun startPreview(): Boolean {
        val camera = helper ?: return false
        firstFrame.set(false)
        camera.startPreview()
        return true
    }
    override fun stopPreview() { helper?.stopPreview() }
    override fun release() {
        if (!released.compareAndSet(false, true)) return
        listener = null
        worker.execute {
            helper?.setStateCallback(null)
            helper?.release()
            helper = null
        }
        worker.shutdown()
    }
}

class UvcNativeLibraryException(cause: UnsatisfiedLinkError) : RuntimeException(cause)
