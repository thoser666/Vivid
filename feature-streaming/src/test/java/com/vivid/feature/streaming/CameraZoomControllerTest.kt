package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraZoomControllerTest {
    @Test fun `presets transition gradually at the saved speed and end at the exact target`() = runTest {
        var zoom = 1f
        val values = mutableListOf<Float>()
        val camera = mockk<CameraControls> {
            every { getZoomRange() } returns ZoomRange(0.5f, 8f)
            every { getZoom() } answers { zoom }
            every { setZoom(any()) } answers { zoom = firstArg(); values += zoom }
        }
        val controller = CameraZoomController(this)
        assertTrue(controller.move(camera, 2f, 1f))
        advanceTimeBy(500)
        assertTrue(zoom in 1.4f..1.6f)
        advanceUntilIdle()
        assertEquals(2f, zoom)
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(controller.move(camera, 0.5f, 10f))
        advanceUntilIdle()
        assertEquals(0.5f, zoom)
    }

    @Test fun `unsupported presets send no zoom commands and cancellation stops an old transition`() = runTest {
        val values = mutableListOf<Float>()
        val camera = mockk<CameraControls> {
            every { getZoomRange() } returns ZoomRange(1f, 4f)
            every { getZoom() } returns 1f
            every { setZoom(any()) } answers { values += firstArg<Float>() }
        }
        val controller = CameraZoomController(this)
        assertFalse(controller.move(camera, 0.5f, 5f))
        assertFalse(controller.move(camera, Float.NaN, 5f))
        assertTrue(values.isEmpty())
        var active = true
        assertTrue(controller.move(camera, 4f, 1f) { active })
        advanceTimeBy(100)
        active = false
        val count = values.size
        advanceUntilIdle()
        assertEquals(count, values.size)
        assertTrue(controller.move(camera, 4f, 1f))
        advanceTimeBy(100)
        controller.cancel()
        val cancelledCount = values.size
        advanceUntilIdle()
        assertEquals(cancelledCount, values.size)
    }
}
