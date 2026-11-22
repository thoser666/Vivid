package com.vivid.feature.streaming.compositor

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.library.view.GlStreamInterface
import com.vivid.feature.streaming.*
import com.vivid.feature.streaming.compositor.gl.SceneFilterChainRender
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneFilterChainTest {
    private class Fixture(val input: Boolean = true) {
        val gl = mockk<GlStreamInterface>(relaxed = true)
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true)
        var scene: BaseFilterRender? = mockk(relaxed = true)
        var video: BaseFilterRender? = mockk(relaxed = true)
        val filters = VideoFilterController().apply { setFilter(VideoFilter.GRAYSCALE) {} }
        val containers = mutableListOf<SceneFilterChainRender>()
        val composer = PrivacyComposer(filters, LowLightBoostController(), LutController(), 16,
            sceneRenderFactory = { scene }, sceneIsInput = { input }, privacyRenderFactory = { privacy },
            videoFilterRenderFactory = { video }, lutRenderFactory = { null }, lowLightRenderFactory = { null })
        init {
            every { gl.setFilter(any<BaseFilterRender>()) } answers {
                containers.add(firstArg<SceneFilterChainRender>())
            }
        }
        fun start() { composer.setPrivacyEnabled(true, gl) }
        fun expected() = if (input) listOfNotNull(scene, privacy, video) else listOfNotNull(privacy, video, scene)
    }

    @Test fun `effect changes update a single stable root slot in both scene positions`() {
        for (input in listOf(true, false)) {
            val f = Fixture(input); f.start()
            repeat(4) { f.video = mockk(relaxed = true); f.composer.requestRebuild(f.gl) }
            assertEquals(1, f.containers.size)
            assertEquals(f.expected(), f.containers.single().requestedChain())
            verify(exactly = 0) { f.gl.clearFilters(); f.gl.addFilter(any<Int>(), any()) }
            verify(exactly = 0) { requireNotNull(f.scene).release(); f.privacy.release() }
        }
    }

    @Test fun `scene removal keeps the protected legacy chain inside the existing root slot`() {
        val f = Fixture(false); f.start()
        f.scene = null
        f.composer.requestRebuild(f.gl)
        assertEquals(1, f.containers.size)
        assertEquals(listOf(f.privacy, f.video), f.containers.single().requestedChain())
        verify(exactly = 0) { f.gl.clearFilters(); f.gl.removeFilter(any<BaseFilterRender>()) }
    }

    @Test fun `privacy toggles retain capture and a completely empty chain returns to the unfiltered path`() {
        val f = Fixture(); f.start()
        f.composer.setPrivacyEnabled(false, f.gl)
        assertEquals(listOf(f.scene, f.video), f.containers.single().requestedChain())
        f.scene = null; f.video = null
        f.composer.requestRebuild(f.gl)
        val removed = f.containers.single()
        assertTrue(removed.requestedChain().isEmpty())
        verify(exactly = 1) { f.gl.removeFilter(removed) }
        f.composer.setPrivacyEnabled(true, f.gl)
        assertEquals(2, f.containers.size)
        assertEquals(listOf(f.privacy), f.containers.last().requestedChain())
        verify(exactly = 0) { f.gl.clearFilters(); f.gl.addFilter(f.privacy) }
    }

    @Test fun `stopped GL invalidation creates a replacement slot with the current desired state`() {
        val f = Fixture(); f.start()
        f.composer.invalidate(f.gl)
        f.scene = mockk(relaxed = true)
        f.composer.requestRebuild(f.gl)
        assertEquals(2, f.containers.size)
        assertEquals(f.expected(), f.containers.last().requestedChain())
        verify(exactly = 0) { f.gl.addFilter(any<Int>(), any()) }
    }

    @Test fun `retired owner cannot invalidate the replacement owner`() {
        val f = Fixture(); f.start()
        val other = mockk<GlStreamInterface>(relaxed = true)
        val container = slot<BaseFilterRender>()
        every { other.setFilter(capture(container)) } just Runs
        f.composer.requestRebuild(other)
        f.composer.invalidate(f.gl)
        f.video = mockk(relaxed = true)
        f.composer.requestRebuild(other)
        verify(exactly = 1) { other.setFilter(any<BaseFilterRender>()) }
        assertEquals(f.expected(), (container.captured as SceneFilterChainRender).requestedChain())
    }

    @Test fun `a scene joins an existing protected chain without any RootEncoder command`() {
        val f = Fixture(false)
        val scene = f.scene; f.scene = null; f.start()
        val oldVideo = requireNotNull(f.video)
        f.scene = scene; f.video = mockk(relaxed = true)
        clearMocks(f.gl, answers = false, recordedCalls = true, childMocks = false)
        f.composer.requestRebuild(f.gl)
        assertEquals(f.expected(), f.containers.single().requestedChain())
        verify(exactly = 0) { f.gl.setFilter(any<BaseFilterRender>()); f.gl.removeFilter(oldVideo) }
        verify(exactly = 0) { f.gl.clearFilters(); f.gl.addFilter(any<Int>(), any()) }
    }
    @Test fun `old container retirement cannot invalidate a replacement queued on the same GL owner`() {
        val f = Fixture(); f.start()
        val old = f.containers.single()
        f.composer.invalidate(f.gl)
        f.composer.requestRebuild(f.gl)
        val current = f.containers.last()
        old.release()
        f.video = mockk(relaxed = true)
        f.composer.requestRebuild(f.gl)
        assertEquals(2, f.containers.size)
        assertSame(current, f.containers.last())
        assertEquals(f.expected(), current.requestedChain())
    }

}
