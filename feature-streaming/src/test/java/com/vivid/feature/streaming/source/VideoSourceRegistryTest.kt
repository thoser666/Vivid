package com.vivid.feature.streaming.source

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoSourceRegistryTest {

    @Test
    fun `defaults to CAMERA`() = runTest {
        val registry = VideoSourceRegistry()

        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.first())
    }

    @Test
    fun `switchTo CAMERA succeeds and updates the state`() = runTest {
        val registry = VideoSourceRegistry()

        val result = registry.switchTo(VideoSourceKind.CAMERA)

        assertTrue(result)
        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
    }

    @Test
    fun `switchTo SCREEN_CAPTURE is rejected before S2 and keeps CAMERA`() = runTest {
        val registry = VideoSourceRegistry()

        val result = registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertFalse(result)
        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
    }

    @Test
    fun `switchTo VIDEO_PLAYER is rejected before S2 and keeps CAMERA`() = runTest {
        val registry = VideoSourceRegistry()

        val result = registry.switchTo(VideoSourceKind.VIDEO_PLAYER)

        assertFalse(result)
        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
    }

    @Test
    fun `rejected switch does not modify the state`() = runTest {
        val registry = VideoSourceRegistry()

        registry.switchTo(VideoSourceKind.VIDEO_PLAYER)

        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
    }
}