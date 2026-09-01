package com.vivid.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideshowWidgetTest {
    @Test
    fun `parses and de-duplicates image uris`() {
        assertEquals(
            listOf("content://one", "content://two"),
            SlideshowController.parseUris("content://one\n content://two;content://one"),
        )
    }

    @Test
    fun `wraps index at the end`() {
        assertEquals(0, SlideshowController.nextIndex(1, 2))
        assertEquals(0, SlideshowController.nextIndex(4, 0))
    }

    @Test
    fun `clamps interval to safe range`() {
        assertEquals(5, SlideshowController.intervalSeconds(0))
        assertEquals(3600, SlideshowController.intervalSeconds(9999))
    }
}
