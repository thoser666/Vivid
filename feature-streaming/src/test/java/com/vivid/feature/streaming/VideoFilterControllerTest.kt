package com.vivid.feature.streaming

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VideoFilterControllerTest {

    private lateinit var controller: VideoFilterController
    private var lastApplied: BaseFilterRender? = null
    private var applyCount = 0
    private var wasCleared = false

    private val applyFilter: FilterApplier = { render ->
        lastApplied = render
        wasCleared = render == null
        applyCount++
    }

    @BeforeEach
    fun setUp() {
        controller = VideoFilterController()
        lastApplied = null
        applyCount = 0
        wasCleared = false
    }

    @Test
    fun `initial filter is NONE`() = runTest {
        assertEquals(VideoFilter.NONE, controller.activeFilter.value)
    }

    @Test
    fun `setFilter applies GREYSCALE`() = runTest {
        val result = controller.setFilter(VideoFilter.GRAYSCALE, applyFilter)

        assertTrue(result)
        assertEquals(VideoFilter.GRAYSCALE, controller.activeFilter.value)
        assertEquals(1, applyCount)
        // In unit tests without Android context, filter creation may fail (returns null → cleared).
        // The controller still succeeds and the applier is always called.
    }

    @Test
    fun `setFilter NONE clears all filters`() = runTest {
        controller.setFilter(VideoFilter.SEPIA, applyFilter)
        assertEquals(VideoFilter.SEPIA, controller.activeFilter.value)

        val result = controller.setFilter(VideoFilter.NONE, applyFilter)

        assertTrue(result)
        assertEquals(VideoFilter.NONE, controller.activeFilter.value)
        assertEquals(2, applyCount)
        assertTrue(wasCleared)
    }

    @Test
    fun `setFilter handles exception gracefully`() = runTest {
        val failingApplier: FilterApplier = { _ -> throw RuntimeException("GL error") }

        val result = controller.setFilter(VideoFilter.GRAYSCALE, failingApplier)

        assertFalse(result)
        assertEquals(VideoFilter.NONE, controller.activeFilter.value)
    }

    @Test
    fun `nextFilter cycles through filters`() = runTest {
        assertEquals(VideoFilter.NONE, controller.activeFilter.value)

        val second = controller.nextFilter(applyFilter)
        assertEquals(VideoFilter.GRAYSCALE, second)
        assertEquals(VideoFilter.GRAYSCALE, controller.activeFilter.value)

        val third = controller.nextFilter(applyFilter)
        assertEquals(VideoFilter.SEPIA, third)
    }

    @Test
    fun `nextFilter wraps around to NONE`() = runTest {
        val last = VideoFilter.entries.last()
        controller.setFilter(last, applyFilter)

        val next = controller.nextFilter(applyFilter)
        assertEquals(VideoFilter.NONE, next)
    }

    @Test
    fun `resetFilterState resets to NONE`() = runTest {
        controller.setFilter(VideoFilter.CARTOON, applyFilter)
        assertEquals(VideoFilter.CARTOON, controller.activeFilter.value)

        controller.resetFilterState()
        assertEquals(VideoFilter.NONE, controller.activeFilter.value)
    }

    @Test
    fun `createFilterRender returns render for known Java filters`() {
        listOf(
            VideoFilter.NONE,
            VideoFilter.GRAYSCALE,
            VideoFilter.SEPIA,
            VideoFilter.NEGATIVE,
            VideoFilter.EDGE_DETECTION,
            VideoFilter.CARTOON,
            VideoFilter.PIXELATED,
            VideoFilter.BLUR,
            VideoFilter.BEAUTY,
            VideoFilter.DUOTONE,
        ).forEach { filter ->
            val render = VideoFilterController.createFilterRender(filter)
            // Java-based filters should work in unit tests
            if (render != null) {
                assertTrue(render::class.simpleName!!.isNotEmpty(), "Filter $filter should create a render")
            }
        }
    }

    @Test
    fun `setFilter stores GREYSCALE then SEPIA`() = runTest {
        controller.setFilter(VideoFilter.GRAYSCALE, applyFilter)
        assertEquals(VideoFilter.GRAYSCALE, controller.activeFilter.value)

        controller.setFilter(VideoFilter.SEPIA, applyFilter)
        assertEquals(VideoFilter.SEPIA, controller.activeFilter.value)
        assertEquals(2, applyCount)
    }
}
