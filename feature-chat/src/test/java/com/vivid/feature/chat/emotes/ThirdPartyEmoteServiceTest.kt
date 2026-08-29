package com.vivid.feature.chat.emotes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThirdPartyEmoteServiceTest {

    private lateinit var service: ThirdPartyEmoteService

    @BeforeEach
    fun setUp() {
        service = ThirdPartyEmoteService()
    }

    @Test
    fun `service starts enabled`() {
        assertTrue(service.enabled.value)
    }

    @Test
    fun `all sources active by default`() {
        val sources = service.activeSources.value
        assertTrue(EmoteSource.BTTV in sources)
        assertTrue(EmoteSource.FFZ in sources)
        assertTrue(EmoteSource.SEVENTV in sources)
    }

    @Test
    fun `setEnabled false clears cache and emotes`() {
        service.setEnabled(false)
        assertFalse(service.enabled.value)
        assertEquals(emptyMap<String, List<ThirdPartyEmote>>(), service.emotes.value)
    }

    @Test
    fun `setActiveSources updates sources`() {
        service.setActiveSources(setOf(EmoteSource.BTTV))
        assertEquals(setOf(EmoteSource.BTTV), service.activeSources.value)
    }

    @Test
    fun `findEmote returns null when no emotes loaded`() {
        assertNull(service.findEmote("12345", "TestEmote"))
    }

    @Test
    fun `parseMessage returns text when no emotes loaded`() {
        val result = service.parseMessage("12345", "Hello world")
        assertEquals(1, result.size)
        assertTrue(result[0] is EmoteSegment.Text)
        assertEquals("Hello world", (result[0] as EmoteSegment.Text).text)
    }

    @Test
    fun `invalidateCache clears cache for channel`() {
        service.invalidateCache("12345")
        assertNull(service.emotes.value["12345"])
    }

    @Test
    fun `setEnabled then setEnabled true restores emotes map`() {
        service.setEnabled(false)
        service.setEnabled(true)
        assertEquals(emptyMap<String, List<ThirdPartyEmote>>(), service.emotes.value)
    }
}
