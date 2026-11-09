package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P4.1-Masken-Pfad ohne Modell (Skizze §11.4/§11.6).
 *
 * Geprüft wird die **Maske als Datum** — Porträt-Ellipse, Gültigkeitsregeln
 * und der Fail-Safe, der ohne hochgeladene Maske auf Ganzbild schaltet. Der
 * GL-Upload selbst bleibt Robolectric-/Geräte-Verifikation
 * ([PrivacyBlurFilterRenderRobolectricTest]).
 */
class EnvironmentMaskTest {

    @Test
    fun `portrait generator marks the centre as person and the corners as background`() {
        val mask = PortraitMaskGenerator.create()

        assertEquals(PortraitMaskGenerator.WIDTH, mask.width)
        assertEquals(PortraitMaskGenerator.HEIGHT, mask.height)
        // Bildmitte (Porträt-Aussparung): voll Person.
        assertEquals(255, mask.personAt(mask.width / 2, mask.height / 2))
        // Alle vier Ecken: Hintergrund.
        listOf(
            0 to 0,
            mask.width - 1 to 0,
            0 to mask.height - 1,
            mask.width - 1 to mask.height - 1,
        ).forEach { (x, y) ->
            assertEquals(0, mask.personAt(x, y), "Ecke $x/$y muss Hintergrund sein")
        }
    }

    @Test
    fun `mask edge fades monotonically from person to background`() {
        val mask = PortraitMaskGenerator.create()
        val y = mask.height / 2
        // Von der Mitte nach rechts: Konfidenz darf nie wieder steigen.
        val values = (0..(mask.width - 1 - mask.width / 2)).map { dx ->
            mask.personAt(mask.width / 2 + dx, y)
        }

        assertEquals(255, values.first())
        assertEquals(0, values.last())
        values.zipWithNext().forEach { (a, b) ->
            assertTrue(a >= b, "Maske muss nach außen monoton fallen: $a -> $b")
        }
        // Weiche Kante: mindestens ein Zwischenwert (kein harter Sprung).
        assertTrue(values.any { it in 1..254 }, "Feather-Kante fehlt komplett")
    }

    @Test
    fun `person coverage is smooth between the feather band and the rim`() {
        // Innerhalb der Feather-Grenze voll, genau am Rand 0, dazwischen weich.
        assertEquals(1f, PortraitMaskGenerator.personCoverage(0.5f, 0.16f))
        assertEquals(0f, PortraitMaskGenerator.personCoverage(1f, 0.16f))
        val middle = PortraitMaskGenerator.personCoverage(0.92f, 0.16f)
        assertTrue(middle > 0f && middle < 1f, "Kante muss weich sein, war $middle")
        // Ohne Feather bleibt die Kante hart, aber der Rand ist weiterhin 0.
        assertEquals(1f, PortraitMaskGenerator.personCoverage(0.99f, 0f))
        assertEquals(0f, PortraitMaskGenerator.personCoverage(1f, 0f))
    }

    @Test
    fun `mask requires a positive size and a matching rgba buffer`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnvironmentMask(0, 144, ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnvironmentMask(4, 4, ByteArray(4 * 4 * EnvironmentMask.RGBA_CHANNELS - 1))
        }
        // Gültiger Puffer: keine Ausnahme.
        EnvironmentMask(4, 4, ByteArray(4 * 4 * EnvironmentMask.RGBA_CHANNELS))
    }

    @Test
    fun `personAt rejects coordinates outside the mask`() {
        val mask = EnvironmentMask(2, 2, ByteArray(2 * 2 * EnvironmentMask.RGBA_CHANNELS))
        assertThrows(IllegalArgumentException::class.java) { mask.personAt(2, 0) }
        assertThrows(IllegalArgumentException::class.java) { mask.personAt(0, -1) }
    }

    @Test
    fun `portrait settings reject impossible geometry`() {
        assertThrows(IllegalArgumentException::class.java) {
            PortraitMaskSettings(centerX = 1.4f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortraitMaskSettings(radiusX = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortraitMaskSettings(feather = 1.5f)
        }
    }

    @Test
    fun `environment without an uploaded mask falls back to full frame`() {
        // Der Panik-Schalter gewinnt immer.
        assertTrue(environmentFullFrameFallback(fullFrame = true, environmentEnabled = true, maskUploaded = true))
        assertTrue(environmentFullFrameFallback(fullFrame = true, environmentEnabled = false, maskUploaded = true))
        // Umgebung an, aber noch keine gültige Maske: Ganzbild statt „nichts".
        assertTrue(environmentFullFrameFallback(fullFrame = false, environmentEnabled = true, maskUploaded = false))
        // Umgebung an und Maske da: nur die Person bleibt scharf.
        assertFalse(environmentFullFrameFallback(fullFrame = false, environmentEnabled = true, maskUploaded = true))
        // Umgebung aus: unverändert.
        assertFalse(environmentFullFrameFallback(fullFrame = false, environmentEnabled = false, maskUploaded = false))
    }
}
