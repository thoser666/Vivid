package com.vivid.feature.streaming.compositor.capture

import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import java.util.UUID

/** Resolves editor selections against current real grants, never a permission-result extra alone. */
internal class SceneUsbSources(private val devices: SceneUsbDevices) {
    fun permissionDevice(source: SceneSource): String? {
        val locator = source.locator.takeIf { source.type == SceneSourceType.UVC }
            ?.let(SceneUsbLocator::parse) ?: return null
        return (devices.resolve(locator) as? SceneUsbSelection.Selected)?.device?.deviceName
    }

    fun matchesGrantedDevice(source: SceneSource, deviceName: String): Boolean {
        val locator = source.locator.takeIf { source.type == SceneSourceType.UVC }
            ?.let(SceneUsbLocator::parse) ?: return false
        val selected = devices.resolve(locator) as? SceneUsbSelection.Selected ?: return false
        return selected.permissionGranted && selected.device.deviceName == deviceName
    }

    fun sourceForGrantedDevice(deviceName: String, known: List<SceneSource>): SceneSource? {
        val device = devices.deviceNamed(deviceName) ?: return null
        if (!devices.hasPermission(device)) return null
        return known.firstOrNull { matchesGrantedDevice(it, deviceName) }
            ?: SceneSource(UUID.randomUUID().toString(), SceneSourceType.UVC, devices.identity(device).encode(),
                device.productName?.take(80).orEmpty().ifBlank { deviceName })
    }
}
