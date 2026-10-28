package com.vivid.feature.streaming.compositor.capture

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Serial identity survives bus renumbering; the path disambiguates serial-less or duplicate-serial devices. */
data class SceneUsbLocator(
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String? = null,
    val deviceNameHint: String? = null,
) {
    init {
        require(vendorId in 0..0xffff && productId in 0..0xffff)
        require(serialNumber == null || (serialNumber.isNotBlank() && serialNumber.length <= 256))
        require(deviceNameHint == null || (deviceNameHint.isNotBlank() && deviceNameHint.length <= 1024))
    }

    fun encode(): String = "usb:v1:" + String.format(Locale.ROOT, "%04x:%04x:", vendorId, productId) +
        escape(serialNumber) + ":" + escape(deviceNameHint)

    companion object {
        private val hexId = Regex("[0-9a-fA-F]{4}")
        private val rawPrefix = Regex("^usb:v[0-9]+:")
        fun isRawLocator(value: String): Boolean = rawPrefix.containsMatchIn(value)
        fun parse(value: String): SceneUsbLocator? = runCatching {
            if (value.length > 8192) return null
            val parts = value.split(':', limit = 6)
            if (parts.size != 6 || parts[0] != "usb" || parts[1] != "v1" ||
                !hexId.matches(parts[2]) || !hexId.matches(parts[3])) return null
            SceneUsbLocator(parts[2].toInt(16), parts[3].toInt(16), unescape(parts[4]), unescape(parts[5]))
        }.getOrNull()

        private fun escape(value: String?) = URLEncoder.encode(value.orEmpty(), "UTF-8")
        private fun unescape(value: String) = URLDecoder.decode(value, "UTF-8").takeIf { it.isNotEmpty() }
    }
}
