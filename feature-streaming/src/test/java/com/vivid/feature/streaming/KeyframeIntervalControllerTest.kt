package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyframeIntervalControllerTest {
    @Test
    fun `normal two second GOP does not need forced keyframes`() {
        for (fps in listOf(14, 30, 60)) {
            val controller = KeyframeIntervalController()
            for (frame in 0..fps * 8) {
                val timeMs = frame * 1_000L / fps
                assertFalse(controller.onFrame(timeMs * 1_000L, frame % (fps * 2) == 0, timeMs))
            }
            assertEquals(2_000L, controller.measuredIntervalMs)
        }
    }

    @Test
    fun `slow 14fps output triggers request by time rather than frame count`() {
        val controller = KeyframeIntervalController()
        assertFalse(controller.onFrame(0, true, 0))
        assertFalse(controller.onFrame(2_000_000, false, 2_000))
        assertTrue(controller.onFrame(2_142_857, false, 2_142))
        assertFalse(controller.onFrame(2_214_285, false, 2_214))
        assertFalse(controller.onFrame(2_214_285, true, 2_214))
        assertEquals(2_214L, controller.measuredIntervalMs)
    }

    @Test
    fun `encoder ignoring requests is retried without per frame request storm`() {
        val controller = KeyframeIntervalController()
        controller.onFrame(0, true, 0)
        assertTrue(controller.onFrame(2_100_000, false, 2_100))
        assertFalse(controller.onFrame(3_000_000, false, 3_000))
        assertTrue(controller.onFrame(4_100_000, false, 4_100))
    }

    @Test
    fun `missing first keyframe is also requested after deadline`() {
        val controller = KeyframeIntervalController()
        assertFalse(controller.onFrame(0, false, 10_000))
        assertTrue(controller.onFrame(2_100_000, false, 12_100))
    }

    @Test
    fun `reset removes previous stream timestamps and measurements`() {
        val controller = KeyframeIntervalController()
        controller.onFrame(1_000_000, true, 1_000)
        controller.onFrame(3_000_000, true, 3_000)
        controller.reset()
        assertNull(controller.measuredIntervalMs)
        assertFalse(controller.onFrame(0, true, 5_000))
        assertFalse(controller.onFrame(2_000_000, true, 7_000))
        assertEquals(2_000L, controller.measuredIntervalMs)
    }
}
