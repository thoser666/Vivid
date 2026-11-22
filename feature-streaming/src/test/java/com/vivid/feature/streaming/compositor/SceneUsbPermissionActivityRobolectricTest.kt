package com.vivid.feature.streaming.compositor

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import com.vivid.feature.streaming.compositor.capture.SceneUsbPermissionActivity
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneUsbPermissionActivityRobolectricTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var granted = false
    private val device = mockk<UsbDevice> {
        every { deviceName } returns "/dev/bus/usb/001/002"
        every { deviceClass } returns UsbConstants.USB_CLASS_VIDEO
        every { interfaceCount } returns 0
        every { vendorId } returns 1
        every { productId } returns 2
    }
    private val callback = slot<PendingIntent>()
    private val manager = mockk<UsbManager> {
        every { deviceList } returns hashMapOf(device.deviceName to device)
        every { hasPermission(any<UsbDevice>()) } answers { granted }
        every { requestPermission(any<UsbDevice>(), capture(callback)) } just Runs
    }
    private var controller: ActivityController<SceneUsbPermissionActivity>? = null
    @Before fun setup() {
        // AndroidX caches the first Application; Robolectric replaces it between test cases.
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
        shadowOf(app).setSystemService(Context.USB_SERVICE, manager)
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA,
            "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }
    @After fun close() {
        controller?.close()
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }
    private fun start(state: Bundle? = null, name: String? = device.deviceName): SceneUsbPermissionActivity {
        val intent = Intent(app, SceneUsbPermissionActivity::class.java)
        name?.let { intent.putExtra(SceneUsbPermissionActivity.EXTRA_DEVICE_NAME, it) }
        controller = Robolectric.buildActivity(SceneUsbPermissionActivity::class.java, intent).create(state).start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(controller).get()
    }
    private fun simulateProcessLoss(): Bundle {
        val state = Bundle()
        requireNotNull(controller).saveInstanceState(state).pause().stop().destroy()
        controller = null
        return state
    }
    @Test fun `missing device requests finish without showing either permission dialog`() {
        val activity = start(name = null)
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `existing real grants return a standard ActivityResult with the selected device name`() {
        granted = true
        val activity = start()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(device.deviceName, shadowOf(activity).resultIntent.getStringExtra(SceneUsbPermissionActivity.EXTRA_DEVICE_NAME))
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `CAMERA denial returns cancellation before any USB request`() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        val activity = start()
        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), request.requestedPermissions)
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_DENIED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `rotation retains one USB request and delivers its result to the replacement Activity`() {
        start()
        requireNotNull(controller).recreate()
        val current = requireNotNull(controller).get()
        verify(exactly = 1) { manager.requestPermission(device, any()) }
        granted = true
        callback.captured.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(current.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(current).resultCode)
    }
    @Test fun `process loss while requesting CAMERA cannot leave the transparent Activity waiting forever`() {
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        start()
        val state = simulateProcessLoss()
        val restored = start(state)
        assertTrue(restored.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(restored).resultCode)
        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }
    @Test fun `process loss during USB consent cancels a missing grant without another dialog`() {
        start()
        val state = simulateProcessLoss()
        val restored = start(state)
        assertTrue(restored.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(restored).resultCode)
        verify(exactly = 1) { manager.requestPermission(device, any()) }
    }
    @Test fun `process restoration retains a USB grant already issued by Android`() {
        start()
        val state = simulateProcessLoss()
        granted = true
        val restored = start(state)
        assertTrue(restored.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(restored).resultCode)
        verify(exactly = 1) { manager.requestPermission(device, any()) }
    }
}
