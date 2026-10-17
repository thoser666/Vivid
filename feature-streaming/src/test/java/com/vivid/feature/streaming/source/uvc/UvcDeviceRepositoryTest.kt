package com.vivid.feature.streaming.source.uvc

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UvcDeviceRepositoryTest {

    private class FakeUsbHostGateway(
        var devices: List<UsbDevice> = emptyList(),
        var hostFeature: Boolean = true,
        var granted: Set<String> = emptySet(),
    ) : UsbHostGateway {
        override fun attachedDevices(): List<UsbDevice> = devices
        override fun hasPermission(device: UsbDevice): Boolean =
            device.deviceName in granted
        override fun hasUsbHostFeature(): Boolean = hostFeature
    }

    private fun usbDevice(
        name: String,
        vendorId: Int,
        productId: Int,
        deviceClass: Int,
        interfaceClasses: List<Int>,
        productName: String? = "TestCam",
        manufacturerName: String? = "TestVendor",
    ): UsbDevice {
        val device = mockk<UsbDevice>()
        every { device.deviceName } returns name
        every { device.vendorId } returns vendorId
        every { device.productId } returns productId
        every { device.deviceClass } returns deviceClass
        every { device.productName } returns productName
        every { device.manufacturerName } returns manufacturerName
        every { device.interfaceCount } returns interfaceClasses.size
        interfaceClasses.forEachIndexed { index, interfaceClass ->
            val usbInterface = mockk<UsbInterface>()
            every { usbInterface.interfaceClass } returns interfaceClass
            every { device.getInterface(index) } returns usbInterface
        }
        return device
    }

    @Test
    fun `empty device list enumerates nothing`() {
        val repository = UvcDeviceRepository(FakeUsbHostGateway())

        assertEquals(emptyList<UvcDeviceDescriptor>(), repository.refresh())
    }

    @Test
    fun `non-video USB devices are rejected`() {
        val mouse = usbDevice(
            name = "/dev/bus/usb/001/002",
            vendorId = 0x1234,
            productId = 0x0001,
            deviceClass = UsbConstants.USB_CLASS_HID,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_HID),
        )
        val repository = UvcDeviceRepository(FakeUsbHostGateway(listOf(mouse)))

        assertEquals(emptyList<UvcDeviceDescriptor>(), repository.refresh())
    }

    @Test
    fun `composite class-0 device with video interface is listed as verified`() {
        val webcam = usbDevice(
            name = "/dev/bus/usb/001/003",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 0,
            interfaceClasses = listOf(
                UsbConstants.USB_CLASS_VIDEO,
                UsbConstants.USB_CLASS_AUDIO,
            ),
        )
        val repository = UvcDeviceRepository(FakeUsbHostGateway(listOf(webcam)))

        val devices = repository.refresh()

        assertEquals(1, devices.size)
        assertTrue(devices.single().verifiedVideoClass)
        assertTrue(devices.single().stableSessionId.isNotBlank())
        assertEquals("TestCam", devices.single().productLabel)
    }

    @Test
    fun `vendor-specific capture card is listed as unverified candidate`() {
        val card = usbDevice(
            name = "/dev/bus/usb/001/004",
            vendorId = 0x534d,
            productId = 0x2109,
            deviceClass = 0,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VENDOR_SPEC),
        )
        val repository = UvcDeviceRepository(FakeUsbHostGateway(listOf(card)))

        val devices = repository.refresh()

        assertEquals(1, devices.size)
        assertFalse(devices.single().verifiedVideoClass)
    }

    @Test
    fun `two identical devices are disambiguated`() {
        val first = usbDevice(
            name = "/dev/bus/usb/001/005",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 239,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VIDEO),
        )
        val second = usbDevice(
            name = "/dev/bus/usb/001/006",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 239,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VIDEO),
        )
        val repository = UvcDeviceRepository(FakeUsbHostGateway(listOf(second, first)))

        val devices = repository.refresh()

        assertEquals(2, devices.map { it.stableSessionId }.distinct().size)
    }

    @Test
    fun `device handle resolves and vanishes after unplug`() {
        val gateway = FakeUsbHostGateway()
        val webcam = usbDevice(
            name = "/dev/bus/usb/001/007",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 0,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VIDEO),
        )
        gateway.devices = listOf(webcam)
        val repository = UvcDeviceRepository(gateway)
        val sessionId = repository.refresh().single().stableSessionId

        assertEquals(webcam, repository.deviceHandle(sessionId))

        gateway.devices = emptyList()

        assertNull(repository.deviceHandle(sessionId))
    }

    @Test
    fun `reused bus address cannot redirect a stale UVC session to another device`() {
        val path = "/dev/bus/usb/001/007"
        val first = usbDevice(path, 0x046d, 0x0825, 0,
            listOf(UsbConstants.USB_CLASS_VIDEO))
        val replacement = usbDevice(path, 0x1234, 0xabcd, 0,
            listOf(UsbConstants.USB_CLASS_VIDEO))
        val gateway = FakeUsbHostGateway(devices = listOf(first))
        val repository = UvcDeviceRepository(gateway)
        val oldSessionId = repository.refresh().single().stableSessionId

        gateway.devices = listOf(replacement)
        assertNull(repository.deviceHandle(oldSessionId))
    }

    @Test
    fun `permission delegates to gateway`() {
        val webcam = usbDevice(
            name = "/dev/bus/usb/001/008",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 0,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VIDEO),
        )
        val repository = UvcDeviceRepository(
            FakeUsbHostGateway(
                devices = listOf(webcam),
                granted = setOf("/dev/bus/usb/001/008"),
            ),
        )

        assertTrue(repository.hasPermission(webcam))
    }

    @Test
    fun `missing product name falls back to vid-pid label`() {
        val webcam = usbDevice(
            name = "/dev/bus/usb/001/009",
            vendorId = 0x046d,
            productId = 0x0825,
            deviceClass = 0,
            interfaceClasses = listOf(UsbConstants.USB_CLASS_VIDEO),
            productName = null,
        )
        val repository = UvcDeviceRepository(FakeUsbHostGateway(listOf(webcam)))

        assertEquals("046D:0825", repository.refresh().single().productLabel)
    }
}
