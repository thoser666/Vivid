package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CameraFocusControllerTest {

    private lateinit var camera: FocusableCamera
    private lateinit var controller: CameraFocusController

    @BeforeEach
    fun setUp() {
        camera = mockk(relaxed = true)
        controller = CameraFocusController(camera)
    }

    @Test
    fun `initial mode is AUTO and not locked`() {
        assertEquals(FocusMode.AUTO, controller.mode)
        assertFalse(controller.isFocusLocked)
    }

    @Test
    fun `toggleFocusLock locks the focus to infinity`() {
        every { camera.disableAutoFocus() } returns true

        val result = controller.toggleFocusLock()

        assertTrue(result)
        assertEquals(FocusMode.LOCKED_INFINITY, controller.mode)
        assertTrue(controller.isFocusLocked)
        verify { camera.disableAutoFocus() }
        verify { camera.setFocusDistance(CameraFocusController.FOCUS_DISTANCE_INFINITY) }
    }

    @Test
    fun `toggleFocusLock unlocks back to autofocus`() {
        every { camera.disableAutoFocus() } returns true
        controller.toggleFocusLock()

        every { camera.enableAutoFocus() } returns true
        val result = controller.toggleFocusLock()

        assertTrue(result)
        assertEquals(FocusMode.AUTO, controller.mode)
        assertFalse(controller.isFocusLocked)
        verify { camera.enableAutoFocus() }
    }

    @Test
    fun `toggleFocusLock keeps the current mode when the camera rejects the lock`() {
        every { camera.disableAutoFocus() } returns false

        val result = controller.toggleFocusLock()

        assertFalse(result)
        assertEquals(FocusMode.AUTO, controller.mode)
        assertFalse(controller.isFocusLocked)
        // Ohne erfolgreichen Lock darf die Distanz nicht gesetzt werden.
        verify(exactly = 0) { camera.setFocusDistance(any()) }
    }

    @Test
    fun `toggleFocusLock keeps the lock when the camera rejects the unlock`() {
        every { camera.disableAutoFocus() } returns true
        controller.toggleFocusLock()

        every { camera.enableAutoFocus() } returns false
        val result = controller.toggleFocusLock()

        assertFalse(result)
        assertEquals(FocusMode.LOCKED_INFINITY, controller.mode)
        assertTrue(controller.isFocusLocked)
    }

    @Test
    fun `apply returns the camera result for each mode`() {
        every { camera.enableAutoFocus() } returns true
        assertTrue(controller.apply(FocusMode.AUTO))
        every { camera.enableAutoFocus() } returns false
        assertFalse(controller.apply(FocusMode.AUTO))

        every { camera.disableAutoFocus() } returns true
        assertTrue(controller.apply(FocusMode.LOCKED_INFINITY))
        every { camera.disableAutoFocus() } returns false
        assertFalse(controller.apply(FocusMode.LOCKED_INFINITY))
    }
}
