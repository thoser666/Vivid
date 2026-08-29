package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraLensControllerTest {

    private fun controls(
        cameraIds: List<String> = listOf("0"),
        currentCameraId: String = "0",
        selectResult: Boolean = true,
    ): CameraControls = mockk<CameraControls>(relaxed = true).also { ctrl ->
        every { ctrl.getAvailableCameraIds() } returns cameraIds
        every { ctrl.getCurrentCameraId() } returns currentCameraId
        every { ctrl.selectCamera(any()) } returns selectResult
        every { ctrl.hasManualFocus() } returns true
        every { ctrl.getFocusDistance() } returns 0.0f
        every { ctrl.setFocusDistance(any()) } just runs
    }

    @Test
    fun `getAvailableLenses returns lenses from camera ids`() {
        val ctrl = controls(cameraIds = listOf("0", "1", "2"))
        val controller = CameraLensController(ctrl)

        val lenses = controller.getAvailableLenses()

        assertEquals(3, lenses.size)
        assertEquals("0", lenses[0].id)
        assertEquals("1", lenses[1].id)
        assertEquals("2", lenses[2].id)
    }

    @Test
    fun `getAvailableLenses marks active camera`() {
        val ctrl = controls(cameraIds = listOf("0", "1"), currentCameraId = "1")
        val controller = CameraLensController(ctrl)

        val lenses = controller.getAvailableLenses()

        assertFalse(lenses[0].isActive)
        assertTrue(lenses[1].isActive)
    }

    @Test
    fun `guessLensType returns WIDE for first camera`() {
        val ctrl = controls(cameraIds = listOf("0"))
        val controller = CameraLensController(ctrl)

        val lenses = controller.getAvailableLenses()

        assertEquals(CameraLensController.LensType.WIDE, lenses[0].type)
    }

    @Test
    fun `guessLensType returns ULTRA_WIDE for second camera`() {
        val ctrl = controls(cameraIds = listOf("0", "1"))
        val controller = CameraLensController(ctrl)

        val lenses = controller.getAvailableLenses()

        assertEquals(CameraLensController.LensType.WIDE, lenses[0].type)
        assertEquals(CameraLensController.LensType.ULTRA_WIDE, lenses[1].type)
    }

    @Test
    fun `guessLensType returns TELE for third camera`() {
        val ctrl = controls(cameraIds = listOf("0", "1", "2"))
        val controller = CameraLensController(ctrl)

        val lenses = controller.getAvailableLenses()

        assertEquals(CameraLensController.LensType.TELE, lenses[2].type)
    }

    @Test
    fun `selectLens delegates to controls and updates current lens`() {
        val ctrl = controls(cameraIds = listOf("0", "1"), selectResult = true)
        val controller = CameraLensController(ctrl)

        val result = controller.selectLens("1")

        assertTrue(result)
        verify { ctrl.selectCamera("1") }
        // After selecting camera "1" (second in list), lens type should be ULTRA_WIDE
        assertEquals(CameraLensController.LensType.ULTRA_WIDE, controller.getCurrentLens())
    }

    @Test
    fun `selectLens returns false on failure`() {
        val ctrl = controls(selectResult = false)
        val controller = CameraLensController(ctrl)

        val result = controller.selectLens("99")

        assertFalse(result)
        assertEquals(CameraLensController.LensType.WIDE, controller.getCurrentLens()) // unchanged
    }

    @Test
    fun `getCurrentLens returns WIDE by default`() {
        val ctrl = controls()
        val controller = CameraLensController(ctrl)

        assertEquals(CameraLensController.LensType.WIDE, controller.getCurrentLens())
    }
}
