package com.vivid.feature.streaming.compositor

import com.vivid.core.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneTransitionClockTest {
    private fun state(id: String, inputId: String? = null) = SceneRenderState(
        SceneComposition(sources = listOf(SceneSource(id, SceneSourceType.IMAGE)),
            layers = listOf(SceneLayer("layer:$id", id)),
            transition = SceneTransition(SceneTransitionType.FADE, 200)), inputId,
    )

    @Test fun `first scene is immediate and fade retains both until its final frame`() {
        val clock = SceneTransitionClock()
        assertNull(clock.frame(0))
        val first = state("first")
        val second = state("second")
        clock.replace(first, 0)
        assertNull(clock.frame(0)!!.outgoing)
        clock.replace(second, 100)
        assertEquals(first, clock.frame(100)!!.outgoing)
        assertEquals(0.5f, clock.frame(200)!!.progress)
        assertEquals(2, clock.frame(200)!!.compositions.size)
        assertNull(clock.frame(300)!!.outgoing)
        assertEquals(second, clock.frame(300)!!.current)
    }

    @Test fun `clock rollback cannot reverse a fade and repeated desired state does not restart it`() {
        val clock = SceneTransitionClock()
        clock.replace(state("a"), 0)
        clock.replace(state("b"), 100)
        assertEquals(0.5f, clock.frame(200)!!.progress)
        clock.replace(state("b"), 201)
        assertEquals(0.5f, clock.frame(50)!!.progress)
        assertEquals(0.75f, clock.frame(250)!!.progress)
    }

    @Test fun `resource budget and conflicting source descriptions cut instead of overallocating`() {
        val clock = SceneTransitionClock()
        val first = state("a")
        clock.replace(first, 0)
        clock.replace(first.copy(composition = first.composition.copy(
            sources = listOf(first.composition.sources.single().copy(locator = "different")))), 10)
        assertNull(clock.frame(10)!!.outgoing)
        val many = SceneComposition(sources = (0 until SceneComposition.MAX_SOURCES).map {
            SceneSource("$it", SceneSourceType.IMAGE)
        }, transition = SceneTransition(SceneTransitionType.FADE, 200))
        clock.replace(SceneRenderState(many), 20)
        assertNull(clock.frame(20)!!.outgoing)
    }

    @Test fun `legacy input switch and editor updates cut while rapid scene switches stay bounded`() {
        val clock = SceneTransitionClock()
        clock.replace(state("a", "a"), 0)
        clock.replace(state("b", "b"), 10)
        assertNull(clock.frame(10)!!.outgoing)
        clock.replace(state("c"), 20, animate = false)
        assertNull(clock.frame(20)!!.outgoing)
        clock.replace(state("d"), 30)
        clock.replace(state("e"), 40)
        assertEquals(2, clock.frame(40)!!.compositions.size)
        assertEquals("d", clock.frame(40)!!.outgoing!!.composition.sources.single().id)
        clock.clear()
        assertNull(clock.frame(100))
    }

    @Test fun `source display name changes do not disable a valid crossfade`() {
        val clock = SceneTransitionClock()
        val first = state("camera")
        clock.replace(first, 0)
        clock.replace(first.copy(composition = first.composition.copy(sources = listOf(
            first.composition.sources.single().copy(name = "New label")))), 10)
        assertNotNull(clock.frame(10)!!.outgoing)
    }
}
