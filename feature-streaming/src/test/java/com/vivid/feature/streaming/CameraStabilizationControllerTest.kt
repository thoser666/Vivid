package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraStabilizationControllerTest {

    private fun camera(enabled: Boolean): CameraControls = mockk<CameraControls> {
        every { isStabilizationEnabled() } returns enabled
    }

    @Test
    fun `initial state reflects the camera`() {
        assertTrue(CameraStabilizationController(camera(enabled = true)).isEnabled)
        assertFalse(CameraStabilizationController(camera(enabled = false)).isEnabled)
    }

    @Test
    fun `toggle enables stabilization when disabled`() {
        val cam = camera(enabled = false)
        every { cam.enableStabilization() } returns true

        val controller = CameraStabilizationController(cam)

        assertTrue(controller.toggle())
        assertTrue(controller.isEnabled)
        verify { cam.enableStabilization() }
    }

    @Test
    fun `toggle disables stabilization when enabled`() {
        val cam = camera(enabled = true)
        every { cam.disableStabilization() } returns true

        val controller = CameraStabilizationController(cam)

        assertTrue(controller.toggle())
        assertFalse(controller.isEnabled)
        verify { cam.disableStabilization() }
    }

    @Test
    fun `toggle keeps the state when the camera rejects the change`() {
        val cam = camera(enabled = false)
        every { cam.enableStabilization() } returns false

        val controller = CameraStabilizationController(cam)

        assertFalse(controller.toggle())
        assertFalse(controller.isEnabled)
        verify(exactly = 1) { cam.enableStabilization() }
    }
}
