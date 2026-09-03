package com.vivid.feature.streaming

import android.util.Range
import android.view.MotionEvent
import android.view.View
import com.pedro.library.base.Camera2Base
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootEncoderCameraControlsTest {

    private fun camera(): Camera2Base = mockk(relaxed = true)

    private fun zoomRange(lower: Float, upper: Float): Range<Float> =
        mockk<Range<Float>>(relaxed = true).also {
            every { it.lower } returns lower
            every { it.upper } returns upper
        }

    @Test
    fun `getZoom delegates to the camera`() {
        val cam = camera()
        every { cam.zoom } returns 2.5f

        assertEquals(2.5f, RootEncoderCameraControls(cam).getZoom())
    }

    @Test
    fun `setZoom delegates to the camera`() {
        val cam = camera()

        RootEncoderCameraControls(cam).setZoom(3f)

        verify { cam.setZoom(3f) }
    }

    @Test
    fun `getZoomRange maps the android range to a pure ZoomRange`() {
        val cam = camera()
        every { cam.zoomRange } returns zoomRange(1f, 8f)

        assertEquals(ZoomRange(min = 1f, max = 8f), RootEncoderCameraControls(cam).getZoomRange())
    }

    @Test
    fun `getZoomRange is null when the camera has no zoom range`() {
        val cam = camera()
        every { cam.zoomRange } returns null

        assertNull(RootEncoderCameraControls(cam).getZoomRange())
    }

    @Test
    fun `tapToFocus delegates to the camera`() {
        val cam = camera()
        val view: View = mockk()
        val event: MotionEvent = mockk()

        RootEncoderCameraControls(cam).tapToFocus(view, event)

        verify { cam.tapToFocus(view, event) }
    }

    @Test
    fun `hasOpticalStabilization is true when optical zooms are available`() {
        val cam = camera()
        every { cam.opticalZooms } returns arrayOf(1f, 2f)

        assertTrue(RootEncoderCameraControls(cam).hasOpticalStabilization())
    }

    @Test
    fun `hasOpticalStabilization is false without optical zooms`() {
        val cam = camera()
        every { cam.opticalZooms } returns emptyArray()

        assertFalse(RootEncoderCameraControls(cam).hasOpticalStabilization())
    }

    @Test
    fun `isStabilizationEnabled combines digital and optical`() {
        val cam = camera()
        every { cam.isVideoStabilizationEnabled } returns false
        every { cam.isOpticalVideoStabilizationEnabled } returns true

        assertTrue(RootEncoderCameraControls(cam).isStabilizationEnabled())
    }

    @Test
    fun `enableStabilization prefers optical over digital`() {
        val cam = camera()
        every { cam.opticalZooms } returns arrayOf(1f)
        every { cam.enableOpticalVideoStabilization() } returns true

        assertTrue(RootEncoderCameraControls(cam).enableStabilization())
        verify { cam.enableOpticalVideoStabilization() }
        verify(exactly = 0) { cam.enableVideoStabilization() }
    }

    @Test
    fun `enableStabilization falls back to digital when no optical zoom is available`() {
        val cam = camera()
        every { cam.opticalZooms } returns emptyArray()
        every { cam.enableVideoStabilization() } returns true

        assertTrue(RootEncoderCameraControls(cam).enableStabilization())
        verify { cam.enableVideoStabilization() }
        verify(exactly = 0) { cam.enableOpticalVideoStabilization() }
    }

    @Test
    fun `disableStabilization disables digital and optical`() {
        val cam = camera()
        every { cam.isVideoStabilizationEnabled } returns true
        every { cam.isOpticalVideoStabilizationEnabled } returns true
        every { cam.disableVideoStabilization() } just runs
        every { cam.disableOpticalVideoStabilization() } just runs

        assertTrue(RootEncoderCameraControls(cam).disableStabilization())
        verify { cam.disableVideoStabilization() }
        verify { cam.disableOpticalVideoStabilization() }
    }

    @Test
    fun `disableStabilization skips inactive modes`() {
        val cam = camera()
        every { cam.isVideoStabilizationEnabled } returns false
        every { cam.isOpticalVideoStabilizationEnabled } returns false

        assertTrue(RootEncoderCameraControls(cam).disableStabilization())
        verify(exactly = 0) { cam.disableVideoStabilization() }
        verify(exactly = 0) { cam.disableOpticalVideoStabilization() }
    }

    // --- Taschenlampe (Torch/Lantern) ---

    @Test
    fun `hasTorch delegates to isLanternSupported`() {
        val cam = camera()
        every { cam.isLanternSupported } returns true

        assertTrue(RootEncoderCameraControls(cam).hasTorch())
    }

    @Test
    fun `isTorchEnabled delegates to isLanternEnabled`() {
        val cam = camera()
        every { cam.isLanternEnabled } returns true

        assertTrue(RootEncoderCameraControls(cam).isTorchEnabled())
    }

    @Test
    fun `enableTorch calls enableLantern and returns true`() {
        val cam = camera()
        every { cam.enableLantern() } just runs

        assertTrue(RootEncoderCameraControls(cam).enableTorch())
        verify { cam.enableLantern() }
    }

    @Test
    fun `disableTorch calls disableLantern and returns true`() {
        val cam = camera()
        every { cam.disableLantern() } just runs

        assertTrue(RootEncoderCameraControls(cam).disableTorch())
        verify { cam.disableLantern() }
    }

    @Test
    fun `enableTorch returns false when enableLantern throws`() {
        val cam = camera()
        every { cam.enableLantern() } throws RuntimeException("no flash")

        assertFalse(RootEncoderCameraControls(cam).enableTorch())
    }

    @Test
    fun `disableTorch returns false when disableLantern throws`() {
        val cam = camera()
        every { cam.disableLantern() } throws RuntimeException("failed")

        assertFalse(RootEncoderCameraControls(cam).disableTorch())
    }

    // --- Manuelle Kamera-Steuerung ---

    @Test
    fun `hasManualFocus returns true`() {
        val cam = camera()
        assertTrue(RootEncoderCameraControls(cam).hasManualFocus())
    }

    @Test
    fun `setFocusDistance delegates to camera`() {
        val cam = camera()

        RootEncoderCameraControls(cam).setFocusDistance(0.5f)

        verify { cam.setFocusDistance(0.5f) }
    }

    @Test
    fun `getCurrentCameraId delegates to camera`() {
        val cam = camera()
        every { cam.currentCameraId } returns "0"

        assertEquals("0", RootEncoderCameraControls(cam).getCurrentCameraId())
    }

    @Test
    fun `getAvailableCameraIds returns current camera id`() {
        val cam = camera()
        every { cam.currentCameraId } returns "0"

        val ids = RootEncoderCameraControls(cam).getAvailableCameraIds()
        assertEquals(1, ids.size)
        assertEquals("0", ids[0])
    }

    @Test
    fun `selectCamera delegates to switchCamera`() {
        val cam = camera()

        assertTrue(RootEncoderCameraControls(cam).selectCamera("1"))
        verify { cam.switchCamera("1") }
    }

    @Test
    fun `selectCamera returns false when switchCamera fails`() {
        val cam = camera()
        every { cam.switchCamera("9") } throws RuntimeException("camera busy")

        assertFalse(RootEncoderCameraControls(cam).selectCamera("9"))
    }

    // --- Belichtung und Weißabgleich ---

    @Test
    fun `hasExposureControl is true when the camera offers a non-empty range`() {
        val cam = camera()
        every { cam.minExposure } returns -3
        every { cam.maxExposure } returns 3

        assertTrue(RootEncoderCameraControls(cam).hasExposureControl())
    }

    @Test
    fun `hasExposureControl is false when min equals max`() {
        val cam = camera()
        every { cam.minExposure } returns 0
        every { cam.maxExposure } returns 0

        assertFalse(RootEncoderCameraControls(cam).hasExposureControl())
    }

    @Test
    fun `getExposure delegates to the camera`() {
        val cam = camera()
        every { cam.exposure } returns 2

        assertEquals(2, RootEncoderCameraControls(cam).getExposure())
    }

    @Test
    fun `getExposure maps the camera limits to an IntRange`() {
        val cam = camera()
        every { cam.minExposure } returns -6
        every { cam.maxExposure } returns 6

        assertEquals(-6..6, RootEncoderCameraControls(cam).getExposureRange())
    }

    @Test
    fun `getExposureRange is null for an invalid range`() {
        val cam = camera()
        every { cam.minExposure } returns 5
        every { cam.maxExposure } returns -5

        assertNull(RootEncoderCameraControls(cam).getExposureRange())
    }

    @Test
    fun `setExposure delegates for an in-range value`() {
        val cam = camera()
        every { cam.minExposure } returns -3
        every { cam.maxExposure } returns 3

        assertTrue(RootEncoderCameraControls(cam).setExposure(2))
        verify { cam.setExposure(2) }
    }

    @Test
    fun `setExposure rejects values outside the supported range`() {
        val cam = camera()
        every { cam.minExposure } returns -3
        every { cam.maxExposure } returns 3

        assertFalse(RootEncoderCameraControls(cam).setExposure(10))
        verify(exactly = 0) { cam.setExposure(any()) }
    }

    @Test
    fun `isAutoExposureEnabled delegates to the camera`() {
        val cam = camera()
        every { cam.isAutoExposureEnabled } returns false

        assertFalse(RootEncoderCameraControls(cam).isAutoExposureEnabled())
    }

    @Test
    fun `enableAutoExposure delegates and returns the camera result`() {
        val cam = camera()
        every { cam.enableAutoExposure() } returns true

        assertTrue(RootEncoderCameraControls(cam).enableAutoExposure())
        verify { cam.enableAutoExposure() }
    }

    @Test
    fun `disableAutoExposure delegates and returns true`() {
        val cam = camera()

        assertTrue(RootEncoderCameraControls(cam).disableAutoExposure())
        verify { cam.disableAutoExposure() }
    }

    @Test
    fun `hasWhiteBalanceControl is true when auto modes exist`() {
        val cam = camera()
        every { cam.autoWhiteBalanceModesAvailable } returns listOf(0, 1)

        assertTrue(RootEncoderCameraControls(cam).hasWhiteBalanceControl())
    }

    @Test
    fun `hasWhiteBalanceControl is false without auto modes`() {
        val cam = camera()
        every { cam.autoWhiteBalanceModesAvailable } returns emptyList()

        assertFalse(RootEncoderCameraControls(cam).hasWhiteBalanceControl())
    }

    @Test
    fun `enableAutoWhiteBalance uses the first available mode`() {
        val cam = camera()
        every { cam.autoWhiteBalanceModesAvailable } returns listOf(2, 5)
        every { cam.enableAutoWhiteBalance(2) } returns true

        assertTrue(RootEncoderCameraControls(cam).enableAutoWhiteBalance())
        verify { cam.enableAutoWhiteBalance(2) }
    }

    @Test
    fun `enableAutoWhiteBalance returns false without available modes`() {
        val cam = camera()
        every { cam.autoWhiteBalanceModesAvailable } returns emptyList()

        assertFalse(RootEncoderCameraControls(cam).enableAutoWhiteBalance())
    }

    @Test
    fun `disableAutoWhiteBalance delegates and returns true`() {
        val cam = camera()

        assertTrue(RootEncoderCameraControls(cam).disableAutoWhiteBalance())
        verify { cam.disableAutoWhiteBalance() }
    }

    @Test
    fun `getWhiteBalanceModesAvailable maps the camera list`() {
        val cam = camera()
        every { cam.autoWhiteBalanceModesAvailable } returns listOf(1, 2, 3)

        assertEquals(listOf(1, 2, 3), RootEncoderCameraControls(cam).getWhiteBalanceModesAvailable())
    }

    @Test
    fun `exposure helpers fail gracefully when the camera throws`() {
        val cam = camera()
        every { cam.minExposure } throws RuntimeException("not prepared")

        assertFalse(RootEncoderCameraControls(cam).hasExposureControl())
        assertNull(RootEncoderCameraControls(cam).getExposureRange())
        assertFalse(RootEncoderCameraControls(cam).setExposure(0))
    }
}
