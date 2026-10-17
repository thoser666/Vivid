package com.vivid.feature.streaming.source.uvc

import android.app.Application
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.vivid.core.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Zustandsmaschinen-Tests für [UvcConnectionManager] (#305, Phasen B/C/G).
 *
 * Der Fake-Gateway spielt Helper-Callbacks skriptgesteuert ein — inklusive
 * verspäteter Callbacks (Generationszähler), wiederholtem Attach/Detach und
 * verweigerter Berechtigung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UvcConnectionManagerTest {

    private class FakeUvcCameraGateway(
        var formats: List<UvcFormatInfo> = listOf(
            UvcFormatInfo(type = 7, width = 1280, height = 720, fps = 30),
            UvcFormatInfo(type = 5, width = 640, height = 480, fps = 30),
        ),
    ) : UvcCameraGateway {
        var callback: UvcCameraGateway.Listener? = null
        val selected = mutableListOf<UsbDevice>()
        var openedMode: UvcCaptureMode? = null
        var nativeCameraOpenRequested = false
        var nativeCameraOpened = false
        val openRequests = mutableListOf<UvcCaptureMode?>()
        var previewStarted = false
        var released = false
        var failSelect = false
        var reportActualMode = true
        var autoConfirmMode = true

        override fun setListener(listener: UvcCameraGateway.Listener?) {
            this.callback = listener
        }

        override fun deviceList(): List<UsbDevice> = emptyList()

        override fun select(device: UsbDevice) {
            if (failSelect) throw IllegalStateException("select failed")
            selected += device
        }

        // UVCAndroid does not expose supportedSizeList until openCamera() has
        // opened its native camera. Model that contract so querying too early
        // fails the regression test instead of returning plausible fake data.
        override fun supportedFormats(): List<UvcFormatInfo> = if (nativeCameraOpened) formats else emptyList()
        override fun actualMode(): UvcCaptureMode? = if (reportActualMode && nativeCameraOpened) {
            openedMode ?: selectDefaultUvcMode(mapUvcModes(formats))
        } else null

        override fun open(mode: UvcCaptureMode?) {
            openRequests += mode
            if (mode == null) {
                nativeCameraOpenRequested = true
            } else {
                openedMode = mode
                if (autoConfirmMode) {
                    callback?.onModeConfigured(selected.last(), mode)
                }
            }
        }

        override fun closeCamera() = Unit
        override fun addSurface(surface: Surface): Boolean = true
        override fun removeSurface(surface: Surface): Boolean = true
        override fun startPreview(): Boolean {
            previewStarted = true
            return true
        }

        override fun stopPreview() {
            previewStarted = false
        }

        override fun release() {
            released = true
        }

        fun emitDeviceOpen(device: UsbDevice) = callback?.onDeviceOpen(device, true)
        fun emitCameraOpen(device: UsbDevice) {
            if (nativeCameraOpenRequested) nativeCameraOpened = true
            callback?.onCameraOpen(device)
        }
        fun emitDetach(device: UsbDevice) = callback?.onDetach(device)
        fun emitError(device: UsbDevice, failure: UvcFailure) =
            callback?.onError(device, failure)
    }

    private class FakeUsbHostGateway(
        var devices: List<UsbDevice> = emptyList(),
        var granted: Boolean = false,
    ) : UsbHostGateway {
        override fun attachedDevices(): List<UsbDevice> = devices
        override fun hasPermission(device: UsbDevice): Boolean = granted
        override fun hasUsbHostFeature(): Boolean = true
    }

    private class FakeUsbPermissionGateway(var granted: Boolean = false) : UsbPermissionGateway {
        val requests = mutableListOf<UsbDevice>()
        override fun hasPermission(device: UsbDevice): Boolean = granted
        override fun requestPermission(device: UsbDevice, intent: android.app.PendingIntent) {
            requests += device
        }
    }

    private lateinit var hostGateway: FakeUsbHostGateway
    private lateinit var permissionGateway: FakeUsbPermissionGateway
    private lateinit var permissionController: UvcPermissionController
    private lateinit var gateways: MutableList<FakeUvcCameraGateway>
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var manager: UvcConnectionManager

    private fun usbDevice(name: String): UsbDevice {
        val device = mockk<UsbDevice>()
        every { device.deviceName } returns name
        every { device.vendorId } returns 0x046d
        every { device.productId } returns 0x0825
        every { device.deviceClass } returns 0
        every { device.productName } returns "TestCam"
        every { device.manufacturerName } returns "TestVendor"
        every { device.interfaceCount } returns 1
        val usbInterface = mockk<UsbInterface>()
        every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_VIDEO
        every { device.getInterface(0) } returns usbInterface
        return device
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.CAMERA)
        hostGateway = FakeUsbHostGateway()
        permissionGateway = FakeUsbPermissionGateway()
        gateways = mutableListOf()
        settingsRepository = mockk()
        every { settingsRepository.uvcPreferenceFlow() } returns flowOf(null to null)
        coEvery { settingsRepository.updateUvcPreference(any(), any()) } just runs
        permissionController = UvcPermissionController(context, permissionGateway)
        manager = UvcConnectionManager(
            context,
            UvcDeviceRepository(hostGateway),
            permissionController,
            UvcGatewayFactory { FakeUvcCameraGateway().also { gateways += it } },
            settingsRepository,
        )
    }

    private fun descriptorFor(device: UsbDevice): UvcDeviceDescriptor {
        hostGateway.devices = listOf(device)
        manager.refresh()
        return manager.devices.value.single()
    }

    private fun connectReady(device: UsbDevice): UvcDeviceDescriptor {
        hostGateway.granted = true
        permissionGateway.granted = true
        val descriptor = descriptorFor(device)
        manager.connect(descriptor)
        gateways.single().emitDeviceOpen(device)
        gateways.single().emitCameraOpen(device)
        return (manager.connectionState.value as UvcConnectionState.Ready).device
    }

    @Test
    fun `no device at launch stays disconnected without prompting`() {
        assertEquals(UvcConnectionState.Disconnected, manager.connectionState.value)
        assertTrue(permissionGateway.requests.isEmpty())
        assertTrue(manager.devices.value.isEmpty())
    }

    @Test
    fun `connect with granted permission opens and negotiates default mode`() {
        val device = usbDevice("dev1")
        val descriptor = descriptorFor(device)
        hostGateway.granted = true
        permissionGateway.granted = true

        manager.connect(descriptor)

        assertEquals(UvcConnectionState.Opening(descriptor), manager.connectionState.value)
        gateways.single().emitDeviceOpen(device)
        assertEquals(listOf(null), gateways.single().openRequests)
        // Formats remain unavailable until native camera-open completes.
        assertTrue(gateways.single().supportedFormats().isEmpty())
        gateways.single().emitCameraOpen(device)
        assertTrue(gateways.single().supportedFormats().isNotEmpty())
        // Moduswahl: MJPEG-Default, kein erfundener Modus.
        assertEquals(
            UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
            (manager.connectionState.value as UvcConnectionState.Ready).mode,
        )
        assertEquals(
            UvcConnectionState.Ready(
                descriptor.copy(modes = mapUvcModes(gateways.single().formats)),
                UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
            ),
            manager.connectionState.value,
        )
        assertTrue(manager.isCaptureReady)
    }

    @Test
    fun `connect without permission waits for explicit grant`() {
        val device = usbDevice("dev2")
        val descriptor = descriptorFor(device)

        manager.connect(descriptor)

        assertEquals(UvcConnectionState.PermissionRequired(descriptor), manager.connectionState.value)
        assertEquals(listOf(device), permissionGateway.requests)
        // Grant über den Broadcast-Pfad setzt das Öffnen fort.
        permissionGateway.granted = true
        hostGateway.granted = true
        permissionController.onResult(device, true)
        gateways.single().emitDeviceOpen(device)
        gateways.single().emitCameraOpen(device)
        assertTrue(manager.isCaptureReady)
    }

    @Test
    fun `denial returns to safe error state with retry path`() {
        val device = usbDevice("dev3")
        val descriptor = descriptorFor(device)
        manager.connect(descriptor)
        assertEquals(UvcConnectionState.PermissionRequired(descriptor), manager.connectionState.value)

        // Verweigerung direkt über den Controller (Broadcast-Äquivalent).
        permissionController.onResult(device, false)

        val error = manager.connectionState.value as UvcConnectionState.Error
        assertEquals(UvcFailure.PermissionDenied, error.reason)
        assertEquals(descriptor, error.device)
        assertFalse(manager.isCaptureReady)
    }

    @Test
    fun `Android camera runtime permission is checked before requesting USB access`() {
        val device = usbDevice("dev13")
        val descriptor = descriptorFor(device)
        val context = ApplicationProvider.getApplicationContext<Application>()
        org.robolectric.Shadows.shadowOf(context).denyPermissions(android.Manifest.permission.CAMERA)

        manager.connect(descriptor)

        assertEquals(UvcFailure.CameraPermissionDenied, (manager.connectionState.value as UvcConnectionState.Error).reason)
        assertTrue(permissionGateway.requests.isEmpty())
    }

    @Test
    fun `stale callbacks after disconnect are ignored`() {
        val device = usbDevice("dev4")
        connectReady(device)
        val gateway = gateways.single()

        manager.disconnect()
        assertEquals(UvcConnectionState.Disconnected, manager.connectionState.value)

        gateway.emitDeviceOpen(device)
        gateway.emitCameraOpen(device)

        assertEquals(UvcConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun `reconnect releases the previous gateway exactly once`() {
        val device = usbDevice("dev5")
        connectReady(device)
        val first = gateways.single()

        manager.connect(manager.devices.value.single())

        assertTrue(first.released)
        assertEquals(2, gateways.size)
    }

    @Test
    fun `attach surface starts capture only from ready`() {
        val surface = mockk<Surface>()
        // Ohne Ready kein Capture.
        assertFalse(manager.attachCaptureSurface(surface))

        val device = usbDevice("dev6")
        val descriptor = connectReady(device)
        assertTrue(manager.attachCaptureSurface(surface))
        assertTrue(manager.connectionState.value is UvcConnectionState.Starting)
        gateways.single().callback?.onFirstFrame(device)
        assertEquals(
            UvcConnectionState.Capturing(
                descriptor,
                UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
            ),
            manager.connectionState.value,
        )

        manager.detachCaptureSurface(surface)
        assertEquals(
            UvcConnectionState.Ready(
                descriptor,
                UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
            ),
            manager.connectionState.value,
        )
    }

    @Test
    fun `detach during capture reports interruption and notifies loss`() {
        val device = usbDevice("dev7")
        connectReady(device)
        manager.attachCaptureSurface(mockk())
        var lost = false
        manager.onCaptureLost = { lost = true }

        gateways.single().emitDetach(device)

        assertEquals(UvcFailure.DetachedDuringCapture, (manager.connectionState.value as UvcConnectionState.Error).reason)
        assertTrue(lost)
    }

    @Test
    fun `unreported mode is rejected with rollback`() {
        val device = usbDevice("dev8")
        val descriptor = connectReady(device)
        val bogus = UvcCaptureMode(1920, 1080, 60, UvcPixelFormat.MJPEG)

        manager.setMode(bogus)

        val state = manager.connectionState.value
        assertTrue(state is UvcConnectionState.Ready)
        assertEquals(descriptor, (state as UvcConnectionState.Ready).device)
    }

    @Test
    fun `reported mode switch reopens with new mode`() {
        val device = usbDevice("dev9")
        connectReady(device)
        val yuy2 = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)

        manager.setMode(yuy2)

        assertTrue(gateways.first().released)
        assertTrue(manager.connectionState.value is UvcConnectionState.Opening)
        gateways.last().emitDeviceOpen(device)
        gateways.last().emitCameraOpen(device)
        assertEquals(yuy2, gateways.last().openedMode)
        val state = manager.connectionState.value
        assertTrue(state is UvcConnectionState.Ready)
        assertEquals(yuy2, (state as UvcConnectionState.Ready).mode)
    }

    @Test
    fun `mode change remains opening until native readback is confirmed`() {
        val device = usbDevice("delayed-mode")
        connectReady(device)
        val requested = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        manager.setMode(requested)
        val pending = gateways.last()
        pending.autoConfirmMode = false
        pending.emitDeviceOpen(device)
        pending.emitCameraOpen(device)
        assertTrue(manager.connectionState.value is UvcConnectionState.Opening)
        pending.callback?.onModeConfigured(device, requested)
        assertEquals(requested, (manager.connectionState.value as UvcConnectionState.Ready).mode)
    }

    @Test
    fun `native rejection of advertised mode fails rather than claiming ready`() {
        val device = usbDevice("rejected-mode")
        connectReady(device)
        val requested = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        manager.setMode(requested)
        val pending = gateways.last()
        pending.autoConfirmMode = false
        pending.emitDeviceOpen(device)
        pending.emitCameraOpen(device)
        pending.emitError(device, UvcFailure.ModeNegotiationFailed)
        assertEquals(UvcFailure.ModeNegotiationFailed,
            (manager.connectionState.value as UvcConnectionState.Error).reason)
    }

    @Test
    fun `stale native mode confirmation is ignored after disconnect`() {
        val device = usbDevice("stale-mode")
        connectReady(device)
        val requested = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        manager.setMode(requested)
        val pending = gateways.last()
        pending.autoConfirmMode = false
        pending.emitDeviceOpen(device)
        pending.emitCameraOpen(device)
        val staleListener = pending.callback
        manager.disconnect()
        staleListener?.onModeConfigured(device, requested)
        assertEquals(UvcConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun `empty mode list fails closed`() {
        val device = usbDevice("dev10")
        hostGateway.granted = true
        permissionGateway.granted = true
        descriptorFor(device)
        gateways.clear()
        val noModes = object : UvcGatewayFactory {
            override fun create(): UvcCameraGateway =
                FakeUvcCameraGateway(formats = emptyList()).also { gateways += it }
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.CAMERA)
        val strict = UvcConnectionManager(
            context,
            UvcDeviceRepository(hostGateway),
            UvcPermissionController(context, permissionGateway),
            noModes,
            settingsRepository,
        )
        strict.connect(strict.devices.value.single())
        gateways.single().emitDeviceOpen(device)
        gateways.single().emitCameraOpen(device)

        val state = strict.connectionState.value
        assertTrue(state is UvcConnectionState.Error)
    }

    @Test
    fun `unavailable persisted mode falls back to a native reported mode`() {
        val device = usbDevice("dev12")
        val descriptor = descriptorFor(device)
        hostGateway.granted = true
        permissionGateway.granted = true
        val unavailable = UvcCaptureMode(3840, 2160, 30, UvcPixelFormat.MJPEG)
        manager.connect(descriptor, unavailable)
        val firstGateway = gateways.single()
        firstGateway.emitDeviceOpen(device)
        assertEquals(listOf(null), firstGateway.openRequests)

        firstGateway.reportActualMode = false
        firstGateway.emitCameraOpen(device)

        val readyWithFallback = manager.connectionState.value as UvcConnectionState.Ready
        assertEquals(UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG), readyWithFallback.mode)
        assertEquals(2, readyWithFallback.device.modes.size)
        assertEquals(UvcFailure.UnsupportedMode, manager.notice.value)

        val fallback = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        manager.setMode(fallback)
        assertTrue(firstGateway.released)
        val retryGateway = gateways.last()
        retryGateway.emitDeviceOpen(device)
        retryGateway.emitCameraOpen(device)
        val ready = manager.connectionState.value as UvcConnectionState.Ready
        assertEquals(fallback, ready.mode)
    }

    @Test
    fun `gateway error during capture tears down and notifies loss`() {
        val device = usbDevice("dev11")
        connectReady(device)
        manager.attachCaptureSurface(mockk())
        var lost = false
        manager.onCaptureLost = { lost = true }

        gateways.single().emitError(device, UvcFailure.DeviceBusy)

        assertEquals(UvcFailure.DeviceBusy, (manager.connectionState.value as UvcConnectionState.Error).reason)
        assertTrue(lost)
    }
}
