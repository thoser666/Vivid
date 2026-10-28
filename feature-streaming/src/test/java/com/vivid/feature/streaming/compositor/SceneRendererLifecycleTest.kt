package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.gl.SceneCompositorFilterRender
import com.vivid.feature.streaming.compositor.gl.SceneTextureProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneRendererLifecycleTest {
    @Test fun `retirement is visible before capture cleanup can trigger a rebuild`() {
        val events = mutableListOf<String>()
        lateinit var renderer: SceneCompositorFilterRender
        val provider = mockk<SceneTextureProvider>(relaxed = true) {
            every { detach() } answers {
                assertTrue(renderer.isReleased)
                events.add("capture released")
            }
        }
        renderer = SceneCompositorFilterRender(provider)
        assertFalse(renderer.isReleased)
        renderer.release()
        assertEquals(listOf("capture released"), events)
    }
}
