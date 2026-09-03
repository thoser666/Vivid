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

    // --- Belichtung und Weißabgleich ---

    @Test
    fun `setExposure updates state only when the camera accepts the value`() {
        every { controls.setExposure(2) } returns true
        every { controls.setExposure(50) } returns false

        assertTrue(controller.setExposure(2))
        assertEquals(2, controller.exposure.value)

        assertFalse(controller.setExposure(50))
        assertEquals(2, controller.exposure.value)
    }

    @Test
    fun `setAutoExposure toggles state only on success`() {
        every { controls.disableAutoExposure() } returns true
        every { controls.enableAutoExposure() } returns false

        assertTrue(controller.setAutoExposure(false))
        assertFalse(controller.autoExposureEnabled.value)

        assertFalse(controller.setAutoExposure(true))
        assertFalse(controller.autoExposureEnabled.value)
    }

    @Test
    fun `setAutoWhiteBalance toggles state only on success`() {
        every { controls.disableAutoWhiteBalance() } returns true
        every { controls.enableAutoWhiteBalance() } returns false

        assertTrue(controller.setAutoWhiteBalance(false))
        assertFalse(controller.autoWhiteBalanceEnabled.value)

        assertFalse(controller.setAutoWhiteBalance(true))
        assertFalse(controller.autoWhiteBalanceEnabled.value)
    }

    @Test
    fun `ISO is not exposed and EV maps to the exposure control`() {
        every { controls.hasExposureControl() } returns true

        assertFalse(controller.hasIsoControl())
        assertTrue(controller.hasEvControl())
    }

    @Test
    fun `hasEvControl is false without exposure control`() {
        every { controls.hasExposureControl() } returns false

        assertFalse(controller.hasEvControl())
    }

    // --- syncState ---

    @Test
    fun `syncState updates all state flows`() {
        every { controls.getFocusDistance() } returns 0.3f
        every { lensController.getCurrentLens() } returns CameraLensController.LensType.TELE
        every { controls.getExposureRange() } returns -3..3
        every { controls.getExposure() } returns 1
        every { controls.isAutoExposureEnabled() } returns false
        every { controls.isAutoWhiteBalanceEnabled() } returns false

        controller.syncState()

        assertEquals(0.3f, controller.focusDistance.value)
        assertEquals(CameraLensController.LensType.TELE, controller.currentLens.value)
        assertEquals(-3..3, controller.exposureRange.value)
        assertEquals(1, controller.exposure.value)
        assertFalse(controller.autoExposureEnabled.value)
        assertFalse(controller.autoWhiteBalanceEnabled.value)
    }
}
