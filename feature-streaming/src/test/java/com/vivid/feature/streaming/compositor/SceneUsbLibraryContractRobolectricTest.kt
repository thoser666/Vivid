package com.vivid.feature.streaming.compositor

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.serenegiant.usb.USBMonitor
import com.vivid.feature.streaming.compositor.capture.SceneUsbControlBlock
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneUsbLibraryContractRobolectricTest {
    @Test fun `pinned library supplies an authorized placeholder without registration polling dialog or open FD`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val device = mockk<UsbDevice> { every { deviceName } returns "/dev/bus/usb/001/002" }
        val manager = mockk<UsbManager> { every { hasPermission(device) } returns true }
        org.robolectric.Shadows.shadowOf(app).setSystemService(Context.USB_SERVICE, manager)
        val monitor = USBMonitor(app, mockk(relaxed = true), Handler(Looper.getMainLooper()))
        try {
            val block = SceneUsbControlBlock.authorized(monitor, device, manager)
            assertSame(device, block.device)
            assertSame(monitor, block.getUSBMonitor())
            assertNull(block.connection)
            assertFalse(monitor.isRegistered)
            assertSame(device, block.clone().device)
            verify(exactly = 0) { manager.openDevice(any()); manager.requestPermission(any<UsbDevice>(), any()) }
        } finally { monitor.destroy() }
    }
    @Test(expected = SceneProducerPermissionException::class)
    fun `the private constructor bridge never substitutes for Android USB consent`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val device = mockk<UsbDevice>()
        val manager = mockk<UsbManager> { every { hasPermission(device) } returns false }
        org.robolectric.Shadows.shadowOf(app).setSystemService(Context.USB_SERVICE, manager)
        val monitor = USBMonitor(app, mockk(relaxed = true), Handler(Looper.getMainLooper()))
        try { SceneUsbControlBlock.authorized(monitor, device, manager) }
        finally { monitor.destroy() }
    }
}
