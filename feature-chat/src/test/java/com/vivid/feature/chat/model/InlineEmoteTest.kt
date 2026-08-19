package com.vivid.feature.chat.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InlineEmoteTest {

    @Test
    fun `parseFromEmotesTag with empty string returns empty list`() {
        assertEquals(emptyList<InlineEmote>(), InlineEmote.parseFromEmotesTag(""))
    }

    @Test
    fun `parseFromEmotesTag with blank string returns empty list`() {
        assertEquals(emptyList<InlineEmote>(), InlineEmote.parseFromEmotesTag("   "))
    }

    @Test
    fun `parseFromEmotesTag with single emote`() {
        val result = InlineEmote.parseFromEmotesTag("30259:0-4")
        assertEquals(1, result.size)
        assertEquals("30259", result[0].id)
        assertEquals(0, result[0].start)
        assertEquals(4, result[0].end)
    }

    @Test
    fun `parseFromEmotesTag with multiple ranges for same emote`() {
        val result = InlineEmote.parseFromEmotesTag("30259:0-4,10-14")
        assertEquals(2, result.size)
        assertEquals("30259", result[0].id)
        assertEquals(0, result[0].start)
        assertEquals(4, result[0].end)
        assertEquals("30259", result[1].id)
        assertEquals(10, result[1].start)
        assertEquals(14, result[1].end)
    }

    @Test
    fun `parseFromEmotesTag with multiple different emotes`() {
        val result = InlineEmote.parseFromEmotesTag("30259:0-4/88:6-7")
        assertEquals(2, result.size)
        assertEquals("30259", result[0].id)
        assertEquals(0, result[0].start)
        assertEquals(4, result[0].end)
        assertEquals("88", result[1].id)
        assertEquals(6, result[1].start)
        assertEquals(7, result[1].end)
    }

    @Test
    fun `parseFromEmotesTag sorts by start position`() {
        val result = InlineEmote.parseFromEmotesTag("88:10-11/30259:0-4")
        assertEquals(2, result.size)
        assertEquals(0, result[0].start)
        assertEquals(10, result[1].start)
    }

    @Test
    fun `parseFromEmotesTag with invalid range returns empty list`() {
        val result = InlineEmote.parseFromEmotesTag("30259:abc-def")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseFromEmotesTag with end before start returns empty list`() {
        val result = InlineEmote.parseFromEmotesTag("30259:5-2")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseFromEmotesTag with missing colon separator ignored`() {
        val result = InlineEmote.parseFromEmotesTag("30259")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseFromEmotesTag with custom scale`() {
        val result = InlineEmote.parseFromEmotesTag("30259:0-4", scale = 3.0)
        assertEquals(1, result.size)
        assertEquals(3.0, result[0].scale)
    }

    @Test
    fun `url constructs Twitch CDN URL correctly`() {
        val emote = InlineEmote(id = "30259", start = 0, end = 4, scale = 2.0)
        assertEquals(
            "https://static-cdn.jtvnw.net/emoticons/v2/30259/default/dark/2.0",
            emote.url,
        )
    }

    @Test
    fun `url with scale 1_0 constructs correct URL`() {
        val emote = InlineEmote(id = "30259", start = 0, end = 4, scale = 1.0)
        assertEquals(
            "https://static-cdn.jtvnw.net/emoticons/v2/30259/default/dark/1.0",
            emote.url,
        )
    }
}
