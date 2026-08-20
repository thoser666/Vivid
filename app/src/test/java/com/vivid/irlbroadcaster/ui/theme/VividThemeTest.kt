package com.vivid.irlbroadcaster.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vivid.core.data.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Vivid-Farbpalette (UI-Farbschemata, PARITY-Zusatz):
 *  - Stufe 1: Branding-Primary ist Vivid-Grün (Seed #3DDC84, kein Template-Lila),
 *    Dark/Light sind echte, unterschiedliche Schemata (nicht hartkodiert Light)
 *  - Stufe 2: Akzent-Paletten (jeder [AccentColor] hat eine eigene Primary-Familie,
 *    Vivid-Grün = Basis), AMOLED-Schema (rein-schwarze Flächen)
 */
class VividThemeTest {

    @Test
    fun `Light-Palette nutzt Vivid-Gruen als Primary`() {
        assertEquals(Color(0xFF006B3F), VividLightColorScheme.primary)
    }

    @Test
    fun `Dark-Palette nutzt helleres Vivid-Gruen als Primary`() {
        assertEquals(Color(0xFF6BE59A), VividDarkColorScheme.primary)
    }

    @Test
    fun `Dark und Light sind unterschiedliche Schemata`() {
        assertNotEquals(VividLightColorScheme.primary, VividDarkColorScheme.primary)
        assertNotEquals(VividLightColorScheme.background, VividDarkColorScheme.background)
        assertNotEquals(VividLightColorScheme.onBackground, VividDarkColorScheme.onBackground)
    }

    @Test
    fun `Dark-Schema hat hellen Text auf dunklem Hintergrund`() {
        // Helligkeit der sRGB-Komponenten: onBackground > background im Dark-Schema
        val bg = VividDarkColorScheme.background
        val onBg = VividDarkColorScheme.onBackground
        assert(onBg.red > bg.red && onBg.green > bg.green && onBg.blue > bg.blue)
    }

    @Test
    fun `Paletten sind keine Template-Standardfarben mehr`() {
        // Template-Scaffold nutzte Purple/Pink — darf nicht mehr Primary sein
        assertNotEquals(Color(0xFF6650A4), VividLightColorScheme.primary) // Material3-Template Purple40
        assertNotEquals(Color(0xFFD0BCFF), VividDarkColorScheme.primary) // Template Purple80
    }

    // --- Stufe 2: Akzentfarben (Settings-Kategorie „Darstellung“) ---

    @Test
    fun `accentPalettes deckt jede Akzentfarbe ab`() {
        assertEquals(AccentColor.entries.toSet(), accentPalettes.keys)
    }

    @Test
    fun `Vivid-Gruen-Akzent entspricht exakt der Basis-Palette`() {
        // Stufe-1-Look bleibt unverändert: Der Standard-Akzent darf die
        // bestehenden Light-/Dark-Schemata nicht verändern.
        val green = accentPalettes.getValue(AccentColor.VIVID_GREEN)
        assertEquals(VividLightColorScheme.primary, green.lightPrimary)
        assertEquals(VividLightColorScheme.primaryContainer, green.lightPrimaryContainer)
        assertEquals(VividDarkColorScheme.primary, green.darkPrimary)
        assertEquals(VividDarkColorScheme.primaryContainer, green.darkPrimaryContainer)
    }

    @Test
    fun `jeder Akzent hat eine eigene Light-Primary`() {
        val primaries = AccentColor.entries.map { accentPalettes.getValue(it).lightPrimary }.toSet()
        assertEquals(AccentColor.entries.size, primaries.size) // alle 6 verschieden
        assertNotEquals(VividLightColorScheme.primary, accentPalettes.getValue(AccentColor.OCEAN_BLUE).lightPrimary)
    }

    @Test
    fun `Ozean-Blau-Akzent faerbt Primary und Container, Laesst Neutrale unberuehrt`() {
        val oceanLight = accentPalettes.getValue(AccentColor.OCEAN_BLUE)
        assertNotEquals(VividLightColorScheme.primary, oceanLight.lightPrimary)
        assertNotEquals(VividLightColorScheme.primaryContainer, oceanLight.lightPrimaryContainer)
        // Neutrale Rollen bleiben Vivid-Basis (Light-Hintergrund unverändert).
        assertEquals(VividLightColorScheme.background, VividLightColorScheme.copy(primary = oceanLight.lightPrimary).background)
        assertEquals(VividLightColorScheme.secondary, VividLightColorScheme.copy(primary = oceanLight.lightPrimary).secondary)
    }

    @Test
    fun `AMOLED-Schema nutzt rein-schwarze Flaeche`() {
        assertEquals(Color(0xFF000000), VividAmoledColorScheme.background)
        assertEquals(Color(0xFF000000), VividAmoledColorScheme.surface)
        assertEquals(Color(0xFF000000), VividAmoledColorScheme.surfaceContainerLowest)
        // Text bleibt lesbar (hell auf Schwarz) — dunkler als das Standard-Dark-Schema.
        assertTrue(VividAmoledColorScheme.surface.luminance() < VividDarkColorScheme.surface.luminance())
    }

    @Test
    fun `AMOLED erbt die Dark-Primary des Akzents`() {
        val teal = accentPalettes.getValue(AccentColor.TEAL)
        val amoledTeal = VividAmoledColorScheme.withAccentDark(teal)
        assertEquals(teal.darkPrimary, amoledTeal.primary)
        assertEquals(Color(0xFF000000), amoledTeal.surface)
    }
}
