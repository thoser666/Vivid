package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.capture.SceneWidgetFeed
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneWidgetContentTest {
    private val now = Instant.parse("2026-10-24T20:45:06Z")
    @Test fun `clock respects current timezone and updates each second`() {
        val source = SceneSource("clock", SceneSourceType.WIDGET, "clock")
        assertEquals("15:45:06", SceneWidgetFeed.content(source, now, ZoneId.of("America/Lima")))
        assertEquals("15:45:07", SceneWidgetFeed.content(source, now.plusSeconds(1), ZoneId.of("America/Lima")))
    }
    @Test fun `text preserves line breaks and is bounded while external widgets are untouched`() {
        val text = SceneSource("text", SceneSourceType.WIDGET, "text:Hello\nworld")
        assertEquals("Hello\nworld", SceneWidgetFeed.content(text, now, ZoneId.of("UTC")))
        assertEquals(2048, SceneWidgetFeed.content(text.copy(locator = "text:" + "a".repeat(3000)), now, ZoneId.of("UTC"))!!.length)
        assertNull(SceneWidgetFeed.content(text.copy(locator = "chat"), now, ZoneId.of("UTC")))
        assertNull(SceneWidgetFeed.content(text.copy(type = SceneSourceType.IMAGE), now, ZoneId.of("UTC")))
    }
}
