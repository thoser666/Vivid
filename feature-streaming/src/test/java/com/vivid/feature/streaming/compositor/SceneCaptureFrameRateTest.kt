package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.CameraCaptureCapabilities
import com.vivid.feature.streaming.compositor.capture.resolveSceneCaptureFrameRate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneCaptureFrameRateTest {
    @Test fun `capture uses the requested high rate only when this buffer size supports it`() {
        val checked = mutableListOf<Triple<Int, Int, Int>>()
        val camera = CameraCaptureCapabilities { w, h, fps -> checked += Triple(w, h, fps); fps <= 60 }
        assertEquals(60, resolveSceneCaptureFrameRate(1920, 1080, 60, false, camera))
        assertEquals(listOf(Triple(1920, 1080, 60)), checked)
    }

    @Test fun `a slower camera retains supported capture while the scene output can remain faster`() {
        val camera = CameraCaptureCapabilities { _, _, fps -> fps <= 30 }
        assertEquals(30, resolveSceneCaptureFrameRate(3840, 2160, 60, false, camera))
    }

    @Test fun `shared camera budget never probes a high speed rate`() {
        val checked = mutableListOf<Int>()
        val camera = CameraCaptureCapabilities { _, _, fps -> checked += fps; true }
        assertEquals(30, resolveSceneCaptureFrameRate(1280, 720, 60, true, camera))
        assertEquals(listOf(30), checked)
    }

    @Test fun `lower output demand is respected without forcing a 30fps request`() {
        val camera = CameraCaptureCapabilities { _, _, fps -> fps == 24 }
        assertEquals(24, resolveSceneCaptureFrameRate(1280, 720, 24, false, camera))
        assertEquals(24, resolveSceneCaptureFrameRate(1280, 720, 60, false, camera))
    }

    @Test fun `unknown metadata keeps the bounded legacy default and never implies high rate support`() {
        val unknown = CameraCaptureCapabilities { _, _, _ -> false }
        assertEquals(30, resolveSceneCaptureFrameRate(1920, 1080, 60, false, unknown))
        assertEquals(24, resolveSceneCaptureFrameRate(1920, 1080, 24, false, unknown))
    }

    @Test fun `higher output rates can fall back to a verified regular 60fps session`() {
        val camera = CameraCaptureCapabilities { _, _, fps -> fps == 60 }
        assertEquals(60, resolveSceneCaptureFrameRate(1280, 720, 120, false, camera))
        assertThrows(IllegalArgumentException::class.java) { resolveSceneCaptureFrameRate(1, 1, 0, false, camera) }
    }
}
