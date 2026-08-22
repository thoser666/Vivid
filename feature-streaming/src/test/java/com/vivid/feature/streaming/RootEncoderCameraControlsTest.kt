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
}
