package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet den Design-Modus (Settings-Kategorie „Darstellung“, PARITY-Zusatz
 * „UI-Farbschemata“ — Stufe 2): robustes Parsen + reine Entscheidungslogik.
 */
class ThemeModeTest {

    @Test
    fun `fromName liest alle vier Modi`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromName("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("Dark"))
        assertEquals(ThemeMode.AMOLED, ThemeMode.fromName("amoled"))
    }

    @Test
    fun `fromName faellt auf SYSTEM bei unbekannt oder null zurueck`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("NIGHT_MODE"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
    }

    @Test
    fun `resolveDark - System folgt der System-Einstellung`() {
        assertEquals(false, ThemeMode.SYSTEM.resolveDark(systemDark = false))
        assertEquals(true, ThemeMode.SYSTEM.resolveDark(systemDark = true))
    }

    @Test
    fun `resolveDark - Light ist immer hell`() {
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = true))
    }

    @Test
    fun `resolveDark - Dark und AMOLED sind immer dunkel`() {
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = false))
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = true))
        assertTrue(ThemeMode.AMOLED.resolveDark(systemDark = false))
        assertTrue(ThemeMode.AMOLED.resolveDark(systemDark = true))
    }
}
