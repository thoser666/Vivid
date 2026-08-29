package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManualCameraControllerTest {

    private lateinit var controls: CameraControls
    private lateinit var lensController: CameraLensController
    private lateinit var controller: ManualCameraController

    @BeforeEach
    fun setUp() {
        controls = mockk(relaxed = true)
        lensController = mockk(relaxed = true)
        every { lensController.getCurrentLens() } returns CameraLensController.LensType.WIDE
        every { lensController.getAvailableLenses() } returns emptyList()
        every { lensController.selectLens(any()) } returns true
        controller = ManualCameraController(controls, lensController)
    }

    // --- Focus Distance ---

    @Test
    fun `setFocusDistance updates state`() {
        controller.setFocusDistance(0.5f)

        verify { controls.setFocusDistance(0.5f) }
        assertEquals(0.5f, controller.focusDistance.value)
    }

    @Test
    fun `hasManualFocus delegates to controls`() {
        every { controls.hasManualFocus() } returns true

        assertTrue(controller.hasManualFocus())
    }

    // --- Lens Selection ---

    @Test
    fun `selectLens delegates to lens controller`() {
        val result = controller.selectLens("1")

        assertTrue(result)
        verify { lensController.selectLens("1") }
    }

    @Test
    fun `getAvailableLenses delegates to lens controller`() {
        val lenses = listOf(
            LensInfo("0", CameraLensController.LensType.WIDE, true),
            LensInfo("1", CameraLensController.LensType.ULTRA_WIDE, false),
        )
        every { lensController.getAvailableLenses() } returns lenses

        val result = controller.getAvailableLenses()

        assertEquals(2, result.size)
    }

    // --- syncState ---

    @Test
    fun `syncState updates all state flows`() {
        every { controls.getFocusDistance() } returns 0.3f
        every { lensController.getCurrentLens() } returns CameraLensController.LensType.TELE

        controller.syncState()

        assertEquals(0.3f, controller.focusDistance.value)
        assertEquals(CameraLensController.LensType.TELE, controller.currentLens.value)
    }
}
