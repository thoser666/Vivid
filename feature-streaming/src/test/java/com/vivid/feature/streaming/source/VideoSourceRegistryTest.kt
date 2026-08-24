package com.vivid.feature.streaming.source

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoSourceRegistryTest {

    private class FakeSource(override val kind: VideoSourceKind) : VideoSource {
        override val isActive: Boolean = false
        override fun start(): Boolean = true
        override fun stop(): Boolean = true
    }

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
        assertNull(registry.activeSource.value)
    }

    @Test
    fun `switchTo SCREEN_CAPTURE is rejected without a registered factory`() = runTest {
        val registry = VideoSourceRegistry()

        val result = registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertFalse(result)
        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
    }

    @Test
    fun `switchTo VIDEO_PLAYER is rejected without a registered factory`() = runTest {
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
        assertNull(registry.activeSource.value)
    }

    @Test
    fun `registerFactory makes switchTo SCREEN_CAPTURE succeed`() = runTest {
        val registry = VideoSourceRegistry()
        val source = FakeSource(VideoSourceKind.SCREEN_CAPTURE)
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { source }

        val result = registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertTrue(result)
        assertEquals(VideoSourceKind.SCREEN_CAPTURE, registry.activeKind.value)
        assertSame(source, registry.activeSource.value)
    }

    @Test
    fun `switchTo uses the source returned by the registered factory`() = runTest {
        val registry = VideoSourceRegistry()
        val source = FakeSource(VideoSourceKind.SCREEN_CAPTURE)
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { source }

        registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertSame(source, registry.activeSource.value)
    }

    @Test
    fun `switchTo is rejected when the factory returns null`() = runTest {
        val registry = VideoSourceRegistry()
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { null }

        val result = registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertFalse(result)
        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
        assertNull(registry.activeSource.value)
    }

    @Test
    fun `re-registering a factory replaces the previous one`() = runTest {
        val registry = VideoSourceRegistry()
        val first = FakeSource(VideoSourceKind.SCREEN_CAPTURE)
        val second = FakeSource(VideoSourceKind.SCREEN_CAPTURE)
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { first }
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { second }

        registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        assertSame(second, registry.activeSource.value)
    }

    @Test
    fun `switching back to CAMERA clears the active source`() = runTest {
        val registry = VideoSourceRegistry()
        val source = FakeSource(VideoSourceKind.SCREEN_CAPTURE)
        registry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { source }
        registry.switchTo(VideoSourceKind.SCREEN_CAPTURE)

        registry.switchTo(VideoSourceKind.CAMERA)

        assertEquals(VideoSourceKind.CAMERA, registry.activeKind.value)
        assertNull(registry.activeSource.value)
    }
}
