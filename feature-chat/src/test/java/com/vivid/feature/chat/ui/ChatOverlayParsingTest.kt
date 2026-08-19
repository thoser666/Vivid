package com.vivid.feature.chat.ui

import com.vivid.feature.chat.model.InlineEmote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatOverlayParsingTest {

    @Test
    fun `parseMessageSegments with no emotes returns single text segment`() {
        val result = parseMessageSegments("Hello World", emptyList())
        assertEquals(1, result.size)
        assert(result[0] is MessageSegment.Text)
        assertEquals("Hello World", (result[0] as MessageSegment.Text).text)
    }

    @Test
    fun `parseMessageSegments with one emote in the middle`() {
        val text = "Hello Kappa World"
        val emotes = listOf(InlineEmote(id = "30259", start = 6, end = 10))
        val result = parseMessageSegments(text, emotes)
        assertEquals(3, result.size)
        assertEquals("Hello ", (result[0] as MessageSegment.Text).text)
        assertEquals("30259", (result[1] as MessageSegment.Emote).emote.id)
        assertEquals(" World", (result[2] as MessageSegment.Text).text)
    }

    @Test
    fun `parseMessageSegments with emote at start`() {
        val text = "Kappa hello"
        val emotes = listOf(InlineEmote(id = "30259", start = 0, end = 4))
        val result = parseMessageSegments(text, emotes)
        assertEquals(2, result.size)
        assertEquals("30259", (result[0] as MessageSegment.Emote).emote.id)
        assertEquals(" hello", (result[1] as MessageSegment.Text).text)
    }

    @Test
    fun `parseMessageSegments with emote at end`() {
        val text = "hello Kappa"
        val emotes = listOf(InlineEmote(id = "30259", start = 6, end = 10))
        val result = parseMessageSegments(text, emotes)
        assertEquals(2, result.size)
        assertEquals("hello ", (result[0] as MessageSegment.Text).text)
        assertEquals("30259", (result[1] as MessageSegment.Emote).emote.id)
    }

    @Test
    fun `parseMessageSegments with multiple emotes`() {
        val text = "Kappa Kappa"
        val emotes = listOf(
            InlineEmote(id = "30259", start = 0, end = 4),
            InlineEmote(id = "30259", start = 6, end = 10),
        )
        val result = parseMessageSegments(text, emotes)
        assertEquals(3, result.size)
        assertEquals("30259", (result[0] as MessageSegment.Emote).emote.id)
        assertEquals(" ", (result[1] as MessageSegment.Text).text)
        assertEquals("30259", (result[2] as MessageSegment.Emote).emote.id)
    }

    @Test
    fun `parseMessageSegments with emote covering entire text`() {
        val text = "Kappa"
        val emotes = listOf(InlineEmote(id = "30259", start = 0, end = 4))
        val result = parseMessageSegments(text, emotes)
        assertEquals(1, result.size)
        assertEquals("30259", (result[0] as MessageSegment.Emote).emote.id)
    }

    @Test
    fun `parseMessageSegments with empty text and no emotes`() {
        val result = parseMessageSegments("", emptyList())
        assertEquals(1, result.size)
        assertEquals("", (result[0] as MessageSegment.Text).text)
    }
}
