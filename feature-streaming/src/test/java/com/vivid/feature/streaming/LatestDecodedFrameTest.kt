package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LatestDecodedFrameTest {
    @Test fun `previous camera clock cannot acknowledge a submitted decoder frame`() {
        val frames = LatestDecodedFrame()
        val rendered = mutableListOf<Int>()
        frames.offer(0, 300_000, {})
        assertTrue(frames.renderIfConsumed(818_000_000, rendered::add))
        frames.offer(1, 316_667, {})
        assertFalse(frames.renderIfConsumed(818_000_000, rendered::add))
        assertTrue(frames.renderIfConsumed(300_000_000, rendered::add))
        assertEquals(listOf(0, 1), rendered)
    }

    @Test fun `slow GL receives newest decoded frame without queueing stale images`() {
        val frames = LatestDecodedFrame()
        val rendered = mutableListOf<Int>()
        val discarded = mutableListOf<Int>()
        frames.offer(0, 1_000, discarded::add)
        assertTrue(frames.renderIfConsumed(0, rendered::add))
        for (buffer in 1..60) {
            frames.offer(buffer, 1_000L + buffer * 16_667L, discarded::add)
            assertFalse(frames.renderIfConsumed(999_999, rendered::add))
        }
        assertEquals(listOf(0), rendered)
        assertEquals((1..59).toList(), discarded)
        assertTrue(frames.renderIfConsumed(1_000_000, rendered::add))
        assertEquals(listOf(0, 60), rendered)
        assertFalse(frames.renderIfConsumed(Long.MAX_VALUE, rendered::add))
    }

    @Test fun `fast GL receives every frame with original PTS acknowledgement`() {
        val frames = LatestDecodedFrame()
        val rendered = mutableListOf<Int>()
        val discarded = mutableListOf<Int>()
        var consumed = 0L
        for (buffer in 0..59) {
            val pts = 1_000L + buffer * 16_667L
            frames.offer(buffer, pts, discarded::add)
            assertTrue(frames.renderIfConsumed(consumed, rendered::add))
            consumed = pts * 1_000
        }
        assertEquals((0..59).toList(), rendered)
        assertTrue(discarded.isEmpty())
    }
}
