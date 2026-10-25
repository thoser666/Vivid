package com.vivid.feature.streaming.compositor.capture

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/** No native library is loaded while listing devices or obtaining Android consent. */
class SceneUsbDevices(context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    fun videoDevices(): List<UsbDevice> = manager?.deviceList?.values.orEmpty()
        .filter(::isVideoDevice).sortedBy { it.deviceName }

    fun deviceNamed(name: String): UsbDevice? = videoDevices().firstOrNull { it.deviceName == name }

    fun hasPermission(device: UsbDevice): Boolean = manager?.hasPermission(device) == true

    fun identity(device: UsbDevice): SceneUsbLocator {
        val serial = if (hasPermission(device)) runCatching { device.serialNumber }.getOrNull()
            ?.takeIf { it.isNotBlank() } else null
        return SceneUsbLocator(device.vendorId, device.productId, serial, device.deviceName)
    }

    /** A permission candidate is never a verified serial match until consent is granted. */
    fun resolve(locator: SceneUsbLocator): SceneUsbSelection {
        val candidates = videoDevices().filter { it.vendorId == locator.vendorId && it.productId == locator.productId }
        if (locator.serialNumber != null) {
            val known = candidates.filter { hasPermission(it) && identity(it).serialNumber == locator.serialNumber }
            if (known.size == 1) return SceneUsbSelection.Selected(known.single(), true)
            if (known.size > 1) {
                val atPath = known.singleOrNull { it.deviceName == locator.deviceNameHint }
                return atPath?.let { SceneUsbSelection.Selected(it, true) } ?: SceneUsbSelection.Ambiguous
            }
            val unknown = candidates.filterNot(::hasPermission)
            return when (unknown.size) {
                0 -> SceneUsbSelection.Missing
                1 -> SceneUsbSelection.Selected(unknown.single(), false)
                else -> SceneUsbSelection.Ambiguous
            }
        }
        val atPath = candidates.filter { it.deviceName == locator.deviceNameHint }
        val selected = atPath.singleOrNull() ?: candidates.singleOrNull()
        return if (selected != null) SceneUsbSelection.Selected(selected, hasPermission(selected))
            else if (candidates.isEmpty()) SceneUsbSelection.Missing else SceneUsbSelection.Ambiguous
    }

    companion object {
        fun isVideoDevice(device: UsbDevice): Boolean = device.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
    }
}

sealed interface SceneUsbSelection {
    data class Selected(val device: UsbDevice, val permissionGranted: Boolean) : SceneUsbSelection
    data object Missing : SceneUsbSelection
    data object Ambiguous : SceneUsbSelection
}
