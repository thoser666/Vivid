package com.vivid.feature.streaming.compositor

import android.content.Context
import android.hardware.usb.*
import com.vivid.feature.streaming.compositor.capture.*
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
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
class SceneUsbDevicesRobolectricTest {
    private val connected = hashMapOf<String, UsbDevice>()
    private val granted = mutableSetOf<String>()
    private val manager = mockk<UsbManager> {
        every { deviceList } answers { HashMap(connected) }
        every { hasPermission(any<UsbDevice>()) } answers { firstArg<UsbDevice>().deviceName in granted }
    }
    private val context = mockk<Context> { every { getSystemService(Context.USB_SERVICE) } returns manager }
    private val catalog = SceneUsbDevices(context)
    private val sources = SceneUsbSources(catalog)
    private fun device(path: String, serial: String? = null, video: Boolean = true, composite: Boolean = false,
        vendor: Int = 1, product: Int = 2): UsbDevice {
        val result = mockk<UsbDevice> {
            every { deviceName } returns path
            every { productName } returns "Camera $path"
            every { vendorId } returns vendor
            every { productId } returns product
            every { deviceClass } returns if (composite) 0 else if (video) UsbConstants.USB_CLASS_VIDEO else UsbConstants.USB_CLASS_MASS_STORAGE
            every { interfaceCount } returns if (composite) 1 else 0
            every { getInterface(0) } returns mockk { every { interfaceClass } returns UsbConstants.USB_CLASS_VIDEO }
            every { serialNumber } answers {
                check(path in granted) { "Serial queried before USB permission" }
                serial
            }
        }
        connected[path] = result
        return result
    }
    @Test fun `discovery includes video interfaces in composite devices and excludes storage`() {
        val simple = device("one")
        val composite = device("two", composite = true)
        device("disk", video = false)
        assertEquals(listOf(simple, composite), catalog.videoDevices())
        verify(exactly = 0) { simple.serialNumber; composite.serialNumber }
    }
    @Test fun `persisted serial follows the same camera after bus renumbering`() {
        val camera = device(SCENE_NEW_PATH, SCENE_WANTED)
        granted.add(SCENE_NEW_PATH)
        assertEquals(SceneUsbLocator(1, 2, SCENE_WANTED, SCENE_NEW_PATH), catalog.identity(camera))
        assertEquals(SceneUsbSelection.Selected(camera, true), catalog.resolve(SceneUsbLocator(1, 2, SCENE_WANTED, SCENE_OLD_PATH)))
    }
    @Test fun `a different serial at the old address is not substituted`() {
        device(SCENE_OLD_PATH, "different"); granted.add(SCENE_OLD_PATH)
        assertEquals(SceneUsbSelection.Missing, catalog.resolve(SceneUsbLocator(1, 2, SCENE_WANTED, SCENE_OLD_PATH)))
    }
    @Test fun `unknown serial candidate requires consent before it can be matched`() {
        val camera = device("path", "different")
        val wanted = SceneUsbLocator(1, 2, SCENE_WANTED)
        assertEquals(SceneUsbSelection.Selected(camera, false), catalog.resolve(wanted))
        verify(exactly = 0) { camera.serialNumber }
        granted.add("path")
        assertEquals(SceneUsbSelection.Missing, catalog.resolve(wanted))
    }
    @Test fun `multiple unverified cameras remain ambiguous rather than picking enumeration order`() {
        device("one", "a"); device("two", "b")
        assertEquals(SceneUsbSelection.Ambiguous, catalog.resolve(SceneUsbLocator(1, 2, "a")))
        assertEquals(SceneUsbSelection.Ambiguous, catalog.resolve(SceneUsbLocator(1, 2)))
    }
    @Test fun `serial-less cameras prefer the selected path and only reconnect uniquely after renumbering`() {
        val one = device("one"); val two = device("two")
        val locator = SceneUsbLocator(1, 2, deviceNameHint = "one")
        assertEquals(SceneUsbSelection.Selected(one, false), catalog.resolve(locator))
        connected.remove("one")
        assertEquals(SceneUsbSelection.Selected(two, false), catalog.resolve(locator))
        device("three")
        assertEquals(SceneUsbSelection.Ambiguous, catalog.resolve(locator))
    }
    @Test fun `a verified serial wins over another candidate still awaiting permission`() {
        val one = device("one", SCENE_WANTED); device("two", SCENE_OTHER); granted.add("one")
        assertEquals(SceneUsbSelection.Selected(one, true), catalog.resolve(SceneUsbLocator(1, 2, SCENE_WANTED)))
    }
    @Test fun `disconnected and unrelated USB devices cannot satisfy the source`() {
        device(SCENE_OTHER, vendor = 3)
        assertEquals(SceneUsbSelection.Missing, catalog.resolve(SceneUsbLocator(1, 2)))
        assertNull(catalog.deviceNamed("missing"))
    }
    @Test fun `duplicate firmware serials use the selected port and stay ambiguous after both ports change`() {
        val one = device("one", SCENE_DUPLICATE); val two = device("two", SCENE_DUPLICATE)
        granted.addAll(listOf("one", "two"))
        assertEquals(SceneUsbSelection.Selected(one, true), catalog.resolve(catalog.identity(one)))
        assertEquals(SceneUsbSelection.Selected(two, true), catalog.resolve(catalog.identity(two)))
        assertEquals(SceneUsbSelection.Ambiguous, catalog.resolve(SceneUsbLocator(1, 2, SCENE_DUPLICATE, "old-port")))
    }

    private fun source(locator: SceneUsbLocator, id: String = "saved") =
        SceneSource(id, SceneSourceType.UVC, locator.encode(), "My camera")

    @Test fun `editor does not create a source from an ungranted or disconnected selection`() {
        device("path", SCENE_SERIAL)
        assertNull(sources.sourceForGrantedDevice("path", emptyList()))
        assertNull(sources.sourceForGrantedDevice("missing", emptyList()))
    }
    @Test fun `editor creates a serial identity only after real permission`() {
        val camera = device("path", SCENE_SERIAL); granted.add("path")
        val result = requireNotNull(sources.sourceForGrantedDevice("path", emptyList()))
        assertEquals(SceneSourceType.UVC, result.type)
        assertEquals(catalog.identity(camera), SceneUsbLocator.parse(result.locator))
        assertEquals("Camera path", result.name)
        assertTrue(result.id.isNotBlank())
    }
    @Test fun `editor reuses stable source id and user label after bus renumbering`() {
        device(SCENE_NEW_PATH, SCENE_SERIAL); granted.add(SCENE_NEW_PATH)
        val saved = source(SceneUsbLocator(1, 2, SCENE_SERIAL, SCENE_OLD_PATH))
        assertSame(saved, sources.sourceForGrantedDevice(SCENE_NEW_PATH, listOf(saved)))
        assertTrue(sources.matchesGrantedDevice(saved, SCENE_NEW_PATH))
    }
    @Test fun `consent for a different serial cannot retry or replace the saved source`() {
        device("path", SCENE_OTHER)
        val saved = source(SceneUsbLocator(1, 2, SCENE_WANTED, "path"))
        assertEquals("path", sources.permissionDevice(saved))
        assertFalse(sources.matchesGrantedDevice(saved, "path"))
        granted.add("path")
        assertFalse(sources.matchesGrantedDevice(saved, "path"))
        assertNull(sources.permissionDevice(saved))
        assertNotEquals(saved.id, sources.sourceForGrantedDevice("path", listOf(saved))?.id)
    }
    @Test fun `duplicate serials reuse only the selected device's source`() {
        device("one", SCENE_DUPLICATE); device("two", SCENE_DUPLICATE)
        granted.addAll(listOf("one", "two"))
        val one = source(SceneUsbLocator(1, 2, SCENE_DUPLICATE, "one"), "one-id")
        val two = source(SceneUsbLocator(1, 2, SCENE_DUPLICATE, "two"), "two-id")
        assertSame(two, sources.sourceForGrantedDevice("two", listOf(one, two)))
        assertFalse(sources.matchesGrantedDevice(one, "two"))
    }
    @Test fun `Camera2 and unrelated source types cannot match a raw USB permission result`() {
        device("path", SCENE_SERIAL); granted.add("path")
        val saved = source(SceneUsbLocator(1, 2, SCENE_SERIAL, "path"))
        val wrongType = saved.copy(type = SceneSourceType.CAMERA)
        val hal = saved.copy(locator = "usb:video0")
        assertFalse(sources.matchesGrantedDevice(wrongType, "path"))
        assertFalse(sources.matchesGrantedDevice(hal, "path"))
        assertNotEquals(saved.id, sources.sourceForGrantedDevice("path", listOf(wrongType, hal))?.id)
        granted.clear()
        assertFalse(sources.matchesGrantedDevice(saved, "path"))
    }

}

private const val SCENE_DUPLICATE = "duplicate"
private const val SCENE_NEW_PATH = "new-path"
private const val SCENE_OLD_PATH = "old-path"
private const val SCENE_OTHER = "other"
private const val SCENE_SERIAL = "serial"
private const val SCENE_WANTED = "wanted"
