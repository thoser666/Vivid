package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die kuratierten Akzentfarben (Settings-Kategorie „Darstellung“,
 * PARITY-Zusatz „UI-Farbschemata“ — Stufe 2).
 */
class AccentColorTest {

    @Test
    fun `fromName liest bekannte Namen case-insensitive`() {
        assertEquals(AccentColor.VIVID_GREEN, AccentColor.fromName("VIVID_GREEN"))
        assertEquals(AccentColor.OCEAN_BLUE, AccentColor.fromName("ocean_blue"))
        assertEquals(AccentColor.ROYAL_PURPLE, AccentColor.fromName("Royal_Purple"))
        assertEquals(AccentColor.SUNSET_ORANGE, AccentColor.fromName("sunset_orange"))
        assertEquals(AccentColor.ROSE_PINK, AccentColor.fromName("rose_pink"))
        assertEquals(AccentColor.TEAL, AccentColor.fromName("teal"))
    }

    @Test
    fun `fromName faellt auf VIVID_GREEN bei unbekannt oder null zurueck`() {
        assertEquals(AccentColor.VIVID_GREEN, AccentColor.fromName("NEON_PINK"))
        assertEquals(AccentColor.VIVID_GREEN, AccentColor.fromName(""))
        assertEquals(AccentColor.VIVID_GREEN, AccentColor.fromName(null))
    }

    @Test
    fun `jede Akzentfarbe hat einen gueltigen 6-stelligen Hex-Seed`() {
        AccentColor.entries.forEach { accent ->
            assertTrue(
                "Seed '${accent.seedHex}' muss ein 6-stelliger Hex-Wert sein",
                accent.seedHex.matches(Regex("^#[0-9A-Fa-f]{6}$")),
            )
        }
    }

    @Test
    fun `Standard-Akzent ist Vivid-Gruen`() {
        assertEquals("#3DDC84", AccentColor.VIVID_GREEN.seedHex)
    }
}
