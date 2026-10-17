package com.vivid.feature.streaming.source.uvc

import android.app.Application
import android.app.PendingIntent
import android.hardware.usb.UsbDevice
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vertrags-Tests zum USB-Berechtigungsablauf (#305, Phase B).
 *
 * Hausregel wie bei #249: Ohne explizite Nutzeraktion (Geräte-Picker) darf
 * kein Permission-Dialog angefordert werden — der Controller fragt nur auf
 * [UvcPermissionController.request] an.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UvcPermissionControllerRobolectricTest {

    private class FakeUsbPermissionGateway(
        var granted: Boolean = false,
    ) : UsbPermissionGateway {
        val requests = mutableListOf<UsbDevice>()
        val intents = mutableListOf<PendingIntent>()
        override fun hasPermission(device: UsbDevice): Boolean = granted
        override fun requestPermission(device: UsbDevice, intent: PendingIntent) {
            requests += device
            intents += intent
        }
    }

    private lateinit var gateway: FakeUsbPermissionGateway
    private lateinit var controller: UvcPermissionController

    private fun usbDevice(name: String): UsbDevice {
        val device = mockk<UsbDevice>()
        every { device.deviceName } returns name
        return device
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        gateway = FakeUsbPermissionGateway()
        controller = UvcPermissionController(context, gateway)
    }

    @Test
    fun `already granted permission needs no dialog`() {
        gateway.granted = true
        val device = usbDevice("/dev/bus/usb/001/002")

        val alreadyGranted = controller.request(device)

        assertTrue(alreadyGranted)
        assertTrue(gateway.requests.isEmpty())
        assertNull(controller.pendingDevice.value)
    }

    @Test
    fun `missing permission triggers exactly one request`() {
        val device = usbDevice("/dev/bus/usb/001/003")

        val alreadyGranted = controller.request(device)

        assertFalse(alreadyGranted)
        assertEquals(listOf(device), gateway.requests)
        assertEquals(device, controller.pendingDevice.value)
    }

    @Test
    fun `grant resolves pending and notifies listener`() {
        val device = usbDevice("/dev/bus/usb/001/004")
        var notified: Pair<UsbDevice, Boolean>? = null
        controller.resultListener = { notifiedDevice, granted ->
            notified = notifiedDevice to granted
        }
        controller.request(device)

        val consumed = controller.onResult(device, true)

        assertTrue(consumed)
        assertNull(controller.pendingDevice.value)
        assertEquals(device to true, notified)
    }

    @Test
    fun `denial resolves pending and notifies listener`() {
        val device = usbDevice("/dev/bus/usb/001/005")
        var notified: Pair<UsbDevice, Boolean>? = null
        controller.resultListener = { notifiedDevice, granted ->
            notified = notifiedDevice to granted
        }
        controller.request(device)

        val consumed = controller.onResult(device, false)

        assertTrue(consumed)
        assertNull(controller.pendingDevice.value)
        assertEquals(device to false, notified)
    }

    @Test
    fun `stale grant without pending request is ignored`() {
        val device = usbDevice("/dev/bus/usb/001/006")
        var notified = false
        controller.resultListener = { _, _ -> notified = true }

        val consumed = controller.onResult(device, true)

        assertFalse(consumed)
        assertFalse(notified)
    }

    @Test
    fun `grant for a foreign device is ignored and keeps pending`() {
        val pending = usbDevice("/dev/bus/usb/001/007")
        val foreign = usbDevice("/dev/bus/usb/001/008")
        var notified = false
        controller.resultListener = { _, _ -> notified = true }
        controller.request(pending)

        val consumed = controller.onResult(foreign, true)

        assertFalse(consumed)
        assertFalse(notified)
        assertEquals(pending, controller.pendingDevice.value)
    }

    @Test
    fun `detach while pending clears the request`() {
        val device = usbDevice("/dev/bus/usb/001/009")
        controller.request(device)

        controller.clearPending(device)

        assertNull(controller.pendingDevice.value)
        // Ein verspäteter Grant danach ist wirkungslos.
        assertFalse(controller.onResult(device, true))
    }

    @Test
    fun `repeated denial stays safe and retryable`() {
        val device = usbDevice("/dev/bus/usb/001/010")
        controller.request(device)
        controller.onResult(device, false)

        // Erneuter Versuch nach expliziter Aktion: neue Anfrage, kein Spinner.
        val alreadyGranted = controller.request(device)

        assertFalse(alreadyGranted)
        assertEquals(2, gateway.requests.size)
        assertEquals(device, controller.pendingDevice.value)
    }
    @Test
    fun `duplicate connect does not prompt twice and old mutable grant cannot open a new session`() {
        val device = usbDevice("dev1")
        controller.request(device)
        controller.request(device)
        assertEquals(1, gateway.requests.size)
        val oldIntent = org.robolectric.Shadows.shadowOf(gateway.intents.single()).savedIntent
        assertEquals(ApplicationProvider.getApplicationContext<Application>().packageName, oldIntent.`package`)
        assertFalse(gateway.intents.single().isImmutable)
        controller.clearPending(device)
        controller.request(device)
        oldIntent.putExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, true)
        assertFalse(controller.acceptBroadcast(oldIntent, device))
        assertEquals(device, controller.pendingDevice.value)
    }

    @Test
    fun `grant callback targets the non exported manifest receiver and resumes opening`() {
        val device = usbDevice("dev-explicit")
        var notified: Pair<UsbDevice, Boolean>? = null
        controller.resultListener = { notifiedDevice, granted -> notified = notifiedDevice to granted }

        controller.request(device)

        val callbackIntent = org.robolectric.Shadows.shadowOf(gateway.intents.single()).savedIntent
        assertEquals(
            UvcUsbPermissionReceiver::class.java.name,
            callbackIntent.component?.className,
        )
        callbackIntent.putExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, device)
        callbackIntent.putExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, true)

        assertTrue(controller.acceptBroadcast(callbackIntent, device))
        assertEquals(device to true, notified)
        assertNull(controller.pendingDevice.value)
    }

}
