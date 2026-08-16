package com.vivid.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatBotLimitPresetTest {

    @Test
    fun `exact values match their preset`() {
        assertEquals(
            ChatBotLimitPreset.LOCKER,
            ChatBotLimitPreset.matching(30, 0, 0),
        )
        assertEquals(
            ChatBotLimitPreset.BALANCED,
            ChatBotLimitPreset.matching(60, 10, 120),
        )
        assertEquals(
            ChatBotLimitPreset.STRICT,
            ChatBotLimitPreset.matching(180, 5, 60),
        )
    }

    @Test
    fun `diverging values match no preset (custom)`() {
        assertNull(ChatBotLimitPreset.matching(60, 0, 0)) // Default-Werte sind keine Voreinstellung
        assertNull(ChatBotLimitPreset.matching(90, 4, 50))
        assertNull(ChatBotLimitPreset.matching(180, 5, 0)) // nur ein Wert abweichend
    }

    @Test
    fun `stored preset wins over value matching`() {
        // Benutzer wählte Streng, aber die Werte wurden zwischenzeitlich
        // geändert → gespeicherter Preset bleibt die Auswahl.
        assertEquals(
            ChatBotLimitPreset.STRICT,
            ChatBotLimitPreset.selection("STRICT", 90, 4, 50),
        )
    }

    @Test
    fun `custom falls back to value matching`() {
        // „Eigene“ (CUSTOM) oder fehlender Wert → Matching entscheidet.
        assertEquals(
            ChatBotLimitPreset.BALANCED,
            ChatBotLimitPreset.selection(ChatBotLimitPreset.CUSTOM, 60, 10, 120),
        )
        assertEquals(
            ChatBotLimitPreset.BALANCED,
            ChatBotLimitPreset.selection(null, 60, 10, 120),
        )
        assertNull(ChatBotLimitPreset.selection(ChatBotLimitPreset.CUSTOM, 90, 4, 50))
    }

    @Test
    fun `fromName parses case-insensitively and rejects unknown names`() {
        assertEquals(ChatBotLimitPreset.LOCKER, ChatBotLimitPreset.fromName("locker"))
        assertEquals(ChatBotLimitPreset.STRICT, ChatBotLimitPreset.fromName("STRICT"))
        assertNull(ChatBotLimitPreset.fromName("UNBEKANNT"))
        assertNull(ChatBotLimitPreset.fromName(ChatBotLimitPreset.CUSTOM))
    }

    @Test
    fun `every preset has a display name and distinct values`() {
        val entries = ChatBotLimitPreset.entries
        assertEquals(3, entries.size)
        entries.forEach { preset ->
            assert(preset.displayName.isNotBlank())
        }
        val valueSets = entries.map { Triple(it.perViewerCooldownSeconds, it.perViewerMaxReplies, it.maxRepliesPerHour) }
        assertEquals(valueSets.size, valueSets.toSet().size)
    }
}
