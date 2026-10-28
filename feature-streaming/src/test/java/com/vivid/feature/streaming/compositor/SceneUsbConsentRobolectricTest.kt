package com.vivid.feature.streaming.compositor

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.vivid.feature.streaming.compositor.capture.*
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneUsbConsentRobolectricTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var granted = false
    private val camera = mockk<UsbDevice> {
        every { deviceName } returns "/dev/bus/usb/001/002"
        every { vendorId } returns 1
        every { productId } returns 2
        every { deviceClass } returns UsbConstants.USB_CLASS_VIDEO
        every { interfaceCount } returns 0
    }
    private val connected = hashMapOf(camera.deviceName to camera)
    private val callback = slot<PendingIntent>()
    private val manager = mockk<UsbManager> {
        every { deviceList } answers { HashMap(connected) }
        every { hasPermission(any<UsbDevice>()) } answers { granted }
        every { requestPermission(any<UsbDevice>(), capture(callback)) } just Runs
    }
    private lateinit var consent: SceneUsbConsent
    @Before fun setup() {
        shadowOf(app).setSystemService(Context.USB_SERVICE, manager)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA,
            "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        consent = SceneUsbConsent(app)
    }
    @After fun close() { consent.close() }
    private fun dispatch(grantedExtra: Boolean = true) {
        callback.captured.send(app, 0, Intent().putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, grantedExtra))
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun `request uses an immutable package scoped callback and completes only from the real grant`() {
        consent.request(camera.deviceName)
        val pending = shadowOf(callback.captured)
        assertTrue(pending.isImmutable)
        assertEquals(app.packageName, pending.savedIntent.`package`)
        assertTrue(shadowOf(app).hasReceiverForIntent(pending.savedIntent))
        granted = true
        dispatch(grantedExtra = false) // Fill-in extras cannot override the actual UsbManager grant.
        assertEquals(SceneUsbConsentResult(camera.deviceName, true), consent.result.value)
        assertTrue(pending.isCanceled)
        assertFalse(shadowOf(app).hasReceiverForIntent(pending.savedIntent))
    }
    @Test fun `a claimed successful broadcast cannot grant access`() {
        consent.request(camera.deviceName)
        dispatch(grantedExtra = true)
        assertEquals(SceneUsbConsentResult(camera.deviceName, false), consent.result.value)
    }
    @Test fun `already granted devices complete without opening another permission dialog`() {
        granted = true
        consent.request(camera.deviceName)
        assertEquals(SceneUsbConsentResult(camera.deviceName, true), consent.result.value)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `repeated requests are coalesced`() {
        repeat(4) { consent.request(camera.deviceName) }
        verify(exactly = 1) { manager.requestPermission(camera, any()) }
    }
    @Test fun `close unregisters the receiver cancels the callback and rejects further requests`() {
        consent.request(camera.deviceName)
        val pending = shadowOf(callback.captured)
        consent.close()
        assertTrue(pending.isCanceled)
        assertFalse(shadowOf(app).hasReceiverForIntent(pending.savedIntent))
        consent.request(camera.deviceName)
        verify(exactly = 1) { manager.requestPermission(camera, any()) }
        assertNull(consent.result.value)
    }
    @Test fun `disconnect before the response cannot authorize a missing camera`() {
        consent.request(camera.deviceName)
        connected.clear(); granted = true
        dispatch()
        assertFalse(requireNotNull(consent.result.value).granted)
    }
    @Test fun `CAMERA denial prevents requesting USB camera access`() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        consent.request(camera.deviceName)
        assertFalse(requireNotNull(consent.result.value).granted)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `process restoration uses an existing grant without silently re-requesting it`() {
        consent.restore(camera.deviceName)
        assertFalse(requireNotNull(consent.result.value).granted)
        consent.close()
        granted = true
        consent = SceneUsbConsent(app)
        consent.restore(camera.deviceName)
        assertTrue(requireNotNull(consent.result.value).granted)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `a different device reusing the old bus address cannot satisfy an outstanding request`() {
        consent.request(camera.deviceName)
        val replacement = mockk<UsbDevice> {
            every { deviceName } returns camera.deviceName
            every { vendorId } returns 3
            every { productId } returns 4
            every { deviceClass } returns UsbConstants.USB_CLASS_VIDEO
            every { interfaceCount } returns 0
        }
        connected[camera.deviceName] = replacement
        granted = true
        dispatch()
        assertFalse(requireNotNull(consent.result.value).granted)
    }
    @Test fun `device detach cancels the pending request without waiting for a dialog response`() {
        consent.request(camera.deviceName)
        app.sendBroadcast(Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, camera))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(requireNotNull(consent.result.value).granted)
        assertTrue(shadowOf(callback.captured).isCanceled)
    }

}
