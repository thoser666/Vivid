package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZoomCalculatorTest {

    private val range = ZoomRange(min = 1f, max = 8f)

    @Test
    fun `clamp keeps values inside the range`() {
        assertEquals(1f, ZoomCalculator.clamp(1f, range))
        assertEquals(4f, ZoomCalculator.clamp(4f, range))
        assertEquals(8f, ZoomCalculator.clamp(8f, range))
    }

    @Test
    fun `clamp caps values below and above the range`() {
        assertEquals(1f, ZoomCalculator.clamp(0.5f, range))
        assertEquals(8f, ZoomCalculator.clamp(9f, range))
    }

    @Test
    fun `clamp falls back to the min for invalid values`() {
        assertEquals(1f, ZoomCalculator.clamp(Float.NaN, range))
        assertEquals(1f, ZoomCalculator.clamp(Float.POSITIVE_INFINITY, range))
        assertEquals(1f, ZoomCalculator.clamp(Float.NEGATIVE_INFINITY, range))
    }

    @Test
    fun `clamp handles an inverted range by returning the min`() {
        assertEquals(8f, ZoomCalculator.clamp(1f, ZoomRange(min = 8f, max = 1f)))
    }

    @Test
    fun `zoomForScale multiplies the current zoom and clamps`() {
        assertEquals(2f, ZoomCalculator.zoomForScale(1f, 2f, range))
        assertEquals(6f, ZoomCalculator.zoomForScale(3f, 2f, range))
        assertEquals(8f, ZoomCalculator.zoomForScale(5f, 2f, range))
        assertEquals(1f, ZoomCalculator.zoomForScale(4f, 0.1f, range))
    }

    @Test
    fun `zoomForScale ignores invalid scale factors`() {
        assertEquals(3f, ZoomCalculator.zoomForScale(3f, Float.NaN, range))
        assertEquals(3f, ZoomCalculator.zoomForScale(3f, Float.POSITIVE_INFINITY, range))
    }
}
