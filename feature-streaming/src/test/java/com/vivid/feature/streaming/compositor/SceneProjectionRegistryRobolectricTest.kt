package com.vivid.feature.streaming.compositor

import android.media.projection.MediaProjection
import android.hardware.display.VirtualDisplay
import android.os.Looper
import android.view.Surface
import com.vivid.feature.streaming.compositor.capture.SceneProjectionRegistry
import io.mockk.*
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneProjectionRegistryRobolectricTest {
    @Test fun `grant replacement ignores stale revoke and publishes active lifecycle`() {
        val registry = SceneProjectionRegistry()
        val first = mockk<MediaProjection>(relaxed = true)
        val second = mockk<MediaProjection>(relaxed = true)
        val callback = slot<MediaProjection.Callback>()
        every { first.registerCallback(capture(callback), any()) } just Runs
        assertFalse(registry.active.value)
        registry.grant(SCENE_SCREEN, first)
        assertTrue(registry.active.value)
        registry.grant(SCENE_SCREEN, second)
        callback.captured.onStop()
        assertTrue(registry.hasGrant(SCENE_SCREEN))
        assertTrue(registry.active.value)
        verify { first.stop() }
        registry.close()
        assertFalse(registry.active.value)
        verify { second.stop() }
    }

    @Test fun `old lease cannot detach a replacement grant with the same generation`() {
        val registry = SceneProjectionRegistry()
        val first = mockk<MediaProjection>(relaxed = true)
        val second = mockk<MediaProjection>(relaxed = true)
        val oldDisplay = mockk<VirtualDisplay>(relaxed = true)
        val display = mockk<VirtualDisplay>(relaxed = true)
        val surface = mockk<Surface>(relaxed = true)
        every { first.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any()) } returns oldDisplay
        every { second.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any()) } returns display
        registry.grant(SCENE_SCREEN, first)
        val oldLease = registry.acquire(SCENE_SCREEN, surface, 640, 360, 160) { }
        registry.grant(SCENE_SCREEN, second)
        val newLease = registry.acquire(SCENE_SCREEN, surface, 640, 360, 160) { }
        oldLease.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(registry.active.value)
        verify(exactly = 0) { display.surface = null }
        newLease.close()
        registry.close()
    }

    @Test fun `GL rebuild rebinds one display then idle release ends consent`() {
        val registry = SceneProjectionRegistry()
        val projection = mockk<MediaProjection>(relaxed = true)
        val display = mockk<VirtualDisplay>(relaxed = true)
        val surface = mockk<Surface>(relaxed = true)
        every { projection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any()) } returns display
        registry.grant(SCENE_SCREEN, projection)
        val first = registry.acquire(SCENE_SCREEN, surface, 640, 360, 160) { }
        first.close()
        val second = registry.acquire(SCENE_SCREEN, surface, 1280, 720, 160) { }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(registry.active.value)
        verify(exactly = 1) { projection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { display.resize(1280, 720, 160) }
        second.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(registry.active.value)
        verify { display.release(); projection.stop() }
    }
}

private const val SCENE_SCREEN = "screen"
