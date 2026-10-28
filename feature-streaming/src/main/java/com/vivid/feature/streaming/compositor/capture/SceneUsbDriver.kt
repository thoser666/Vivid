package com.vivid.feature.streaming.compositor.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import com.serenegiant.usb.Size
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import java.io.Closeable

/** All calls run on the producer's I/O worker, never RootEncoder's GL thread. */
internal interface SceneUsbDriver : Closeable {
    fun open(): List<SceneUsbMode>
    fun start(surface: Surface, configuration: SceneUsbConfiguration)
}

/** Version-pinned app-library bridge, not an Android hidden API. No receiver, dialog, polling or FD is created here. */
internal object SceneUsbControlBlock {
    fun authorized(monitor: USBMonitor, device: UsbDevice, manager: UsbManager): USBMonitor.UsbControlBlock {
        if (!manager.hasPermission(device)) throw SceneProducerPermissionException()
        val constructor = USBMonitor.UsbControlBlock::class.java.getDeclaredConstructor(
            USBMonitor::class.java, UsbDevice::class.java).apply { isAccessible = true }
        return constructor.newInstance(monitor, device)
    }
}

internal class AndroidSceneUsbDriver(private val context: Context, private val device: UsbDevice) : SceneUsbDriver {
    private var monitor: USBMonitor? = null
    private var camera: UVCCamera? = null
    private var running = false
    override fun open(): List<SceneUsbMode> {
        check(camera == null)
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!manager.hasPermission(device) || context.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) throw SceneProducerPermissionException()
        val owner = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice) = Unit
            override fun onDetach(device: UsbDevice) = Unit
            override fun onDeviceOpen(device: UsbDevice, block: USBMonitor.UsbControlBlock, createNew: Boolean) = Unit
            override fun onDeviceClose(device: UsbDevice, block: USBMonitor.UsbControlBlock) = Unit
            override fun onCancel(device: UsbDevice) = Unit
        }, Handler(Looper.getMainLooper()))
        monitor = owner // Deliberately never register the SDK monitor or request permission through it.
        try {
            val block = SceneUsbControlBlock.authorized(owner, device, manager)
            val capture = UVCCamera(UVCParam())
            camera = capture
            val result = capture.open(block) // SDK clones the placeholder and opens one authorized FD.
            check(result == 0) { "Could not open USB camera: $result" }
            return capture.supportedSizeList.orEmpty().mapNotNull { size ->
                val format = when (size.type) {
                    UVCCamera.UVC_VS_FRAME_MJPEG -> SceneUsbFormat.MJPEG
                    UVCCamera.UVC_VS_FRAME_UNCOMPRESSED -> SceneUsbFormat.YUYV
                    else -> return@mapNotNull null
                }
                SceneUsbMode(size.width, size.height, format,
                    (size.fpsList.orEmpty() + size.fps).filter { it in 1..120 }.toSet())
            }
        } catch (error: Throwable) {
            runCatching { close() }
            throw error
        }
    }
    override fun start(surface: Surface, configuration: SceneUsbConfiguration) {
        val capture = requireNotNull(camera)
        if (running) { capture.stopPreview(); running = false }
        val mode = configuration.mode
        val type = if (mode.format == SceneUsbFormat.MJPEG) UVCCamera.UVC_VS_FRAME_MJPEG else UVCCamera.UVC_VS_FRAME_UNCOMPRESSED
        capture.setPreviewSize(Size(type, mode.width, mode.height, configuration.frameRate, mode.rates.sorted()))
        capture.setPreviewDisplay(surface)
        capture.startPreview()
        running = true
    }
    override fun close() {
        val capture = camera; camera = null
        val owner = monitor; monitor = null
        running = false
        try { capture?.destroy(true) }
        finally { owner?.destroy() }
        // The Surface and SurfaceTexture remain owned by AndroidSceneTextureProvider.
    }
}
