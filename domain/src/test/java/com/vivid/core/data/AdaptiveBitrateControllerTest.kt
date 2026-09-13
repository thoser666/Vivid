package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Logik der adaptiven Bitrate (v0.6.0-Bucket): AIMD-ähnlicher Abbau bei
 * Sättigung (3 Low-Samples in Folge), vorsichtiger additiver Aufbau bei
 * gesunder Strecke (10 Samples), Bounds (min/max) und Streak-Regeln.
 * Läuft ohne Android-Framework.
 */
class AdaptiveBitrateControllerTest {

    private fun controller(
        min: Int = 1_000,
        max: Int = 6_000,
    ) = AdaptiveBitrateController(
        AdaptiveBitrateConfig(
            minBitrateKbps = min,
            maxBitrateKbps = max,
        ),
    )

    // --- Reset ----------------------------------------------------------------

    @Test
    fun `reset setzt Ziel auf Startwert und leert die Streaks`() {
        val c = controller()
        c.reset(4_000)
        assertEquals(4_000, c.targetKbps)

        // Direkt nach Reset: 2 Low-Samples reichen nicht (Streak wurde geleert).
        assertNull(c.onSample(2_000))
        assertNull(c.onSample(2_000))
        assertNotNull(c.onSample(2_000))
    }

    @Test
    fun `reset begrenzt den Startwert auf min und max`() {
        val c = controller(min = 1_000, max = 6_000)
        c.reset(30_000)
        assertEquals(6_000, c.targetKbps)
        c.reset(50)
        assertEquals(1_000, c.targetKbps)
    }

    // --- Abbau (Multiplicative Decrease) ---------------------------------------

    @Test
    fun `dauerhafte Saettigung senkt die Bitrate multiplikativ`() {
        val c = controller()
        c.reset(6_000)

        // 2000/6000 → Defizit 0.667 (> 0.20): erst nach 3 Samples reagieren.
        assertNull(c.onSample(2_000))
        assertNull(c.onSample(2_000))
        assertEquals(4_200, c.onSample(2_000)) // 6000 * 0.7

        // Neu bewertet gegen 4200: 2000 → Defizit 0.524 → wieder 3 Samples.
        assertNull(c.onSample(2_000))
        assertNull(c.onSample(2_000))
        assertEquals(2_940, c.onSample(2_000)) // 4200 * 0.7
    }

    @Test
    fun `Abbau bricht bei minBitrate ab`() {
        val c = controller(min = 1_000, max = 6_000)
        c.reset(2_000)
        // 3× Saettigung: 2000 → 1400 → …
        assertNull(c.onSample(100))
        assertNull(c.onSample(100))
        assertEquals(1_400, c.onSample(100))
        assertNull(c.onSample(100))
        assertNull(c.onSample(100))
        assertEquals(1_000, c.onSample(100)) // Floor
        // Weitere Saettigung bleibt am Floor.
        assertNull(c.onSample(100))
        assertNull(c.onSample(100))
        assertEquals(1_000, c.onSample(100))
    }

    @Test
    fun `einzelnes Low-Sample loest keinen Abbau aus`() {
        val c = controller()
        c.reset(6_000)
        assertNull(c.onSample(2_000))
        // Grauzone (Defizit 0.13): hält den Low-Streak, bricht aber nichts.
        assertNull(c.onSample(5_200))
        assertNull(c.onSample(2_000))
        // Streak läuft weiter → drittes Low-Sample baut ab.
        assertEquals(4_200, c.onSample(2_000))
    }

    // --- Aufbau (Additive Increase) --------------------------------------------

    @Test
    fun `gesunde Strecke baut additiv auf`() {
        val c = controller()
        c.reset(4_000)
        // 3900/4000 → Defizit 0.025 (< 0.05): gesund. 10 Samples → +500.
        repeat(9) {
            assertNull(c.onSample(3_900))
        }
        assertEquals(4_500, c.onSample(3_900))
        repeat(9) {
            assertNull(c.onSample(4_400))
        }
        assertEquals(5_000, c.onSample(4_400))
    }

    @Test
    fun `Aufbau bricht bei maxBitrate ab`() {
        val c = controller(min = 1_000, max = 4_200)
        c.reset(4_000)
        repeat(9) { assertNull(c.onSample(3_900)) }
        assertEquals(4_200, c.onSample(3_900)) // Cap
        repeat(9) { assertNull(c.onSample(4_100)) }
        assertNotNull(c.onSample(4_100))
        assertEquals(4_200, c.targetKbps)
    }

    @Test
    fun `Grauzone bricht den Aufbau-Streak`() {
        val c = controller()
        c.reset(4_000)
        repeat(9) { assertNull(c.onSample(3_900)) }
        // Grauzone (Defizit 0.10): healthyStreak → 0.
        assertNull(c.onSample(3_600))
        // Wieder 9 gesunde Samples → noch kein Aufbau (Streak neu).
        repeat(9) { assertNull(c.onSample(3_900)) }
        assertEquals(4_500, c.onSample(3_900))
    }

    // --- Wechselwirkung --------------------------------------------------------

    @Test
    fun `Sättigungs-Streak bricht bei gesundem Sample`() {
        val c = controller()
        c.reset(6_000)
        assertNull(c.onSample(2_000))
        assertNull(c.onSample(2_000))
        // Gesundes Sample: lowStreak → 0, kein Abbau.
        assertNull(c.onSample(5_950))
        assertNull(c.onSample(2_000))
        assertNull(c.onSample(2_000))
        assertEquals(4_200, c.onSample(2_000))
    }

    @Test
    fun `nach Abbau stabilisiert sich der Wert knapp unter dem Ziel`() {
        val c = controller()
        c.reset(6_000)
        repeat(2) { assertNull(c.onSample(2_000)) }
        assertEquals(4_200, c.onSample(2_000))
        // 4000/4200 → Defizit 0.048 (< 0.05): gesund — kein weiterer Abbau.
        repeat(9) { assertNull(c.onSample(4_000)) }
        assertEquals(4_700, c.onSample(4_000)) // vorsichtiger Aufbau statt weiterer Abbau
        assertTrue(c.targetKbps < 6_000)
        assertFalse(c.targetKbps < 4_000)
    }
}
