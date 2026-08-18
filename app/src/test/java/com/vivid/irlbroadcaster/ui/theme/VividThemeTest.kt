package com.vivid.irlbroadcaster.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Testet die Vivid-Farbpalette (Stufe 1 der UI-Farbschemata, PARITY-Zusatz):
 *  - Branding: Primary ist Vivid-Grün (Seed #3DDC84, kein Template-Lila mehr)
 *  - Dark/Light sind echte, unterschiedliche Schemata (nicht mehr hartkodiert Light)
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
}
