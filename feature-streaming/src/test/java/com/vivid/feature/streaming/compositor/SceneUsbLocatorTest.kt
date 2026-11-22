package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.capture.SceneUsbLocator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneUsbLocatorTest {
    @Test fun `USB identity round trips reserved characters Unicode and spaces`() {
        val locator = SceneUsbLocator(0x1234, 0xabcd, " Camera:+/% cámara 空 ", "/dev/bus/usb/001/002")
        assertEquals(locator, SceneUsbLocator.parse(locator.encode()))
        assertTrue(locator.encode().startsWith("usb:v1:1234:abcd:"))
    }
    @Test fun `serial identity is independent of the current bus path`() {
        val locator = SceneUsbLocator(0, 0xffff, "serial")
        assertEquals(locator, SceneUsbLocator.parse(locator.encode()))
        assertTrue(locator.encode().endsWith(":"))
    }
    @Test fun `serial-less identity preserves its path hint`() {
        val locator = SceneUsbLocator(1, 2, deviceNameHint = "/dev/bus/usb/001/003")
        assertEquals(locator, SceneUsbLocator.parse(locator.encode()))
    }
    @Test fun `unsupported versions malformed fields and camera HAL IDs are rejected`() {
        listOf("0", "external:1", "usb:v2:0001:0002::", "usb:v1:1:0002::", "usb:v1:gggg:0002::",
            "usb:v1:0001:0002:%xx:", "usb:v1:0001:0002:%20:", "usb:v1:0001:0002:", "x".repeat(8193))
            .forEach { assertNull(SceneUsbLocator.parse(it), it) }
    }
    @Test fun `invalid device identities cannot be persisted`() {
        assertThrows(IllegalArgumentException::class.java) { SceneUsbLocator(-1, 1) }
        assertThrows(IllegalArgumentException::class.java) { SceneUsbLocator(1, 65536) }
        assertThrows(IllegalArgumentException::class.java) { SceneUsbLocator(1, 2, " ") }
        assertThrows(IllegalArgumentException::class.java) { SceneUsbLocator(1, 2, "x".repeat(257)) }
        assertThrows(IllegalArgumentException::class.java) { SceneUsbLocator(1, 2, deviceNameHint = "x".repeat(1025)) }
    }
    @Test fun `raw USB namespace does not consume manufacturer Camera2 IDs`() {
        assertTrue(SceneUsbLocator.isRawLocator("usb:v1:0001:0002::"))
        assertTrue(SceneUsbLocator.isRawLocator("usb:v2:future"))
        assertFalse(SceneUsbLocator.isRawLocator("usb:video0"))
        assertFalse(SceneUsbLocator.isRawLocator("external:usb:0"))
    }

}
