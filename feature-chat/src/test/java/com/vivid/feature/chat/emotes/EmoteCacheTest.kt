package com.vivid.feature.chat.emotes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmoteCacheTest {

    private lateinit var cache: EmoteCache

    @BeforeEach
    fun setUp() {
        cache = EmoteCache()
    }

    @Test
    fun `get returns null for unknown channel`() {
        assertNull(cache.get("unknown"))
    }

    @Test
    fun `put and get returns emotes`() {
        val emotes = listOf(
            ThirdPartyEmote(
                id = "bttv_123",
                name = "TestEmote",
                url = "https://example.com/emote.png",
                source = EmoteSource.BTTV,
            )
        )
        cache.put("12345", emotes)
        assertEquals(emotes, cache.get("12345"))
    }

    @Test
    fun `size reflects number of cached channels`() {
        assertEquals(0, cache.size)
        cache.put("1", emptyList())
        assertEquals(1, cache.size)
        cache.put("2", emptyList())
        assertEquals(2, cache.size)
    }

    @Test
    fun `invalidate removes specific channel`() {
        cache.put("1", emptyList())
        cache.put("2", emptyList())
        cache.invalidate("1")
        assertEquals(1, cache.size)
        assertNull(cache.get("1"))
    }

    @Test
    fun `clear removes all entries`() {
        cache.put("1", emptyList())
        cache.put("2", emptyList())
        cache.clear()
        assertEquals(0, cache.size)
    }
}
