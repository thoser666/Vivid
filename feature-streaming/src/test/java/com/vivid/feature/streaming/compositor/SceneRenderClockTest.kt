package com.vivid.feature.streaming.compositor

import com.pedro.library.view.ForceRenderer
import com.pedro.library.view.GlStreamInterface
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.RejectedExecutionException

class SceneRenderClockTest {
    private val renderer = mockk<ForceRenderer>(relaxed = true)
    private val gl = mockk<GlStreamInterface>(relaxed = true) {
        every { isRunning } returns true
    }
    private val ticks = mutableListOf<() -> Unit>()
    private val clock = SceneRenderClock(renderer)

    init { every { renderer.start(any()) } answers { ticks.add(firstArg()); Unit } }

    @Test fun `adding a scene to running GL starts a clock without restarting GL or encoders`() {
        clock.update(gl, 30)
        ticks.single()()
        verify { renderer.setEnabled(true, 30); gl.onFrameAvailable(null) }
        verify(exactly = 0) { gl.start(); gl.stop() }
        clock.update(gl, 30)
        verify(exactly = 1) { renderer.start(any()) }
    }

    @Test fun `source frames suppress fallback but fallback frames retain their full cadence`() {
        clock.update(gl)
        clock.frameRendered(gl)
        verify(exactly = 1) { renderer.frameAvailable() }
        clearMocks(renderer, answers = false, recordedCalls = true, childMocks = false)
        repeat(3) { ticks.single()(); clock.frameRendered(gl) }
        verify(exactly = 3) { gl.onFrameAvailable(null) }
        verify(exactly = 0) { renderer.frameAvailable() }
    }

    @Test fun `stopped GL and callbacks from removed owners cannot request frames`() {
        clock.update(gl)
        val oldTick = ticks.single()
        every { gl.isRunning } returns false
        clearMocks(renderer, answers = false, recordedCalls = true, childMocks = false)
        oldTick()
        verify(exactly = 0) { gl.onFrameAvailable(any()) }
        verify(exactly = 1) { renderer.stop() }
        clock.update(null)
        every { gl.isRunning } returns true
        oldTick()
        clock.frameRendered(gl)
        verify(exactly = 0) { gl.onFrameAvailable(any()); renderer.frameAvailable() }
    }

    @Test fun `FPS and owner changes retire previous schedules and honor encoder cadence`() {
        clock.update(gl, 30)
        val oldTick = ticks.single()
        clock.update(gl, 60)
        oldTick()
        verify(exactly = 0) { gl.onFrameAvailable(any()) }
        ticks.last()()
        verify { renderer.setEnabled(true, 60); gl.onFrameAvailable(null) }
        val other = mockk<GlStreamInterface>(relaxed = true)
        clock.update(other, 60)
        clearMocks(renderer, answers = false, recordedCalls = true, childMocks = false)
        clock.frameRendered(gl)
        verify(exactly = 0) { renderer.frameAvailable() }
        assertThrows(IllegalArgumentException::class.java) { clock.update(other, 0) }
    }

    @Test fun `shutdown during submission is harmless and a later running tick can retry`() {
        clock.update(gl)
        every { gl.onFrameAvailable(null) } throws RejectedExecutionException("GL stopped")
        ticks.single()()
        every { gl.onFrameAvailable(null) } just Runs
        ticks.single()()
        clock.frameRendered(gl)
        verify(exactly = 2) { gl.onFrameAvailable(null) }
        verify(exactly = 0) { renderer.frameAvailable() }
    }
    @Test fun `a stopped old owner cannot retire a replacement clock`() {
        val replacement = mockk<GlStreamInterface>(relaxed = true) {
            every { isRunning } returns true
        }
        clock.update(gl)
        val oldTick = ticks.single()
        every { gl.isRunning } answers { clock.update(replacement); false }
        oldTick()
        ticks.last()()
        verify(exactly = 1) { replacement.onFrameAvailable(null) }
        verify(exactly = 0) { gl.onFrameAvailable(any()) }
    }

}
