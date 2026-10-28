package com.vivid.feature.streaming.compositor

import android.content.Context
import android.opengl.EGL14
import com.pedro.encoder.input.gl.render.RenderHandler
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.vivid.feature.streaming.compositor.gl.SceneFilterChainRender
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/** Ownership/ordering contracts only; GLES pixels require the instrumented suite. */
class SceneFilterChainRenderTest {
    @BeforeEach fun mockContextBoundary() {
        mockkStatic(EGL14::class)
        every { EGL14.eglGetCurrentContext() } returns null
    }
    @AfterEach fun restoreContextBoundary() { unmockkStatic(EGL14::class) }

    private fun child(id: Int): BaseFilterRender {
        var handler = RenderHandler().apply { texId[0] = id }
        return mockk(relaxed = true) {
            every { getRenderHandler() } answers { handler }
            every { setRenderHandler(any()) } answers { handler = firstArg() }
            every { getTexId() } answers { handler.texId[0] }
        }
    }
    private fun start(chain: SceneFilterChainRender) {
        chain.initGl(32, 32, mockk<Context>(relaxed = true), 32, 32)
        chain.setPreviousTexId(7)
    }

    @Test fun `creative replacement preserves initialized scene and privacy and the output texture identity`() {
        val scene = child(11); val privacy = child(12); val first = child(13); val next = child(14)
        val chain = SceneFilterChainRender()
        chain.getRenderHandler().texId[0] = 99
        chain.setChain(listOf(scene, privacy, first)); start(chain); chain.draw()
        val output = chain.texId
        chain.setChain(listOf(scene, privacy, next)); chain.draw()
        assertEquals(99, output); assertEquals(output, chain.texId)
        verify(exactly = 1) { scene.initGl(any(), any(), any(), any(), any()); privacy.initGl(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { scene.release(); privacy.release() }
        verify(exactly = 1) { first.release() }
        verifyOrder { scene.draw(); privacy.draw(); first.draw(); scene.draw(); privacy.draw(); next.draw() }
        verify { scene.setPreviousTexId(7); privacy.setPreviousTexId(11); next.setPreviousTexId(12) }
        assertEquals(14, next.texId) // The borrowed output handler is restored after drawing.
    }

    @Test fun `last child uses the shared output framebuffer without an extra draw or FBO allocation`() {
        val scene = child(11); val effect = child(12)
        val chain = SceneFilterChainRender()
        chain.setChain(listOf(scene)); start(chain); chain.draw()
        verify(exactly = 0) { scene.initFBOLink() }
        chain.setChain(listOf(scene, effect)); chain.draw()
        verify(exactly = 1) { scene.initFBOLink() }
        verify(exactly = 0) { effect.initFBOLink() }
        verify(exactly = 2) { scene.draw() }
        verify(exactly = 1) { effect.draw() }
    }

    @Test fun `multiple edits before a frame initialize only the final creative filter`() {
        val scene = child(11); val discarded = child(12); val latest = child(13)
        val chain = SceneFilterChainRender()
        chain.setChain(listOf(scene, discarded)); chain.setChain(listOf(scene, latest))
        start(chain); chain.draw()
        verify(exactly = 0) { discarded.initGl(any(), any(), any(), any(), any()); discarded.draw() }
        verify(exactly = 1) { latest.draw() }
    }

    @Test fun `removing a scene releases capture while retaining the existing privacy pass`() {
        val scene = child(11); val privacy = child(12)
        val chain = SceneFilterChainRender()
        chain.setChain(listOf(scene, privacy)); start(chain); chain.draw()
        chain.setChain(listOf(privacy)); chain.draw()
        verify(exactly = 1) { scene.release(); privacy.initGl(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { privacy.release() }
        chain.release()
        verify(exactly = 1) { privacy.release() }
    }

    @Test fun `stop retires before releasing initialized children and discards pending changes`() {
        val events = mutableListOf<String>()
        val scene = child(11); val pending = child(12)
        every { scene.release() } answers { events.add("capture stopped") }
        val chain = SceneFilterChainRender { events.add("retired") }
        chain.setChain(listOf(scene)); start(chain); chain.draw()
        chain.setChain(listOf(pending)); chain.release()
        assertTrue(chain.isReleased)
        assertEquals(listOf("retired", "capture stopped"), events)
        assertTrue(chain.requestedChain().isEmpty())
        verify(exactly = 0) { pending.initGl(any(), any(), any(), any(), any()) }
    }
    @Test fun `retirement callback failure still releases every initialized capture and clears pending state`() {
        val scene = child(11); val privacy = child(12)
        val failure = IllegalStateException("retirement failure")
        val chain = SceneFilterChainRender { throw failure }
        chain.setChain(listOf(scene, privacy)); start(chain); chain.draw()
        assertSame(failure, assertThrows(IllegalStateException::class.java) { chain.release() })
        verify(exactly = 1) { scene.release(); privacy.release() }
        assertTrue(chain.requestedChain().isEmpty())
        assertTrue(chain.isReleased)
    }

    @Test fun `a failed child release cannot retain other captures or stale pending state`() {
        val scene = child(11); val effect = child(12)
        val failure = IllegalStateException("effect release failure")
        every { effect.release() } throws failure
        val chain = SceneFilterChainRender()
        chain.setChain(listOf(scene, effect)); start(chain); chain.draw()
        assertSame(failure, assertThrows(IllegalStateException::class.java) { chain.release() })
        verify(exactly = 1) { scene.release(); effect.release() }
        assertTrue(chain.requestedChain().isEmpty())
        assertTrue(chain.isReleased)
    }

}
