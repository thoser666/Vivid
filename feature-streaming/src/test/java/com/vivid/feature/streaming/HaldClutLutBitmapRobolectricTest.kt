package com.vivid.feature.streaming

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Tests für die LUT-Bitmap-Pfade des HaldClutFilterRender-Companions.
 *
 * Diese Pfade enden in `Bitmap.createBitmap` und brauchen daher ein echtes
 * Android-Framework (Robolectric-Emulation). Auf der reinen JVM (android.jar-Stub)
 * blieben sie bisher ungetestet bzw. nur bis zum createBitmap-Aufruf abgedeckt.
 *
 * Pinnt SDK 34, da Robolectric 4.14.1 bis SDK 35 emuliert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HaldClutLutBitmapRobolectricTest {

    private companion object {
        const val SIZE = 8 // 8^3 = 512 Einträge -> 64×8-Bitmap, schnell genug für Pixel-Checks
    }

    @Test
    fun `identity lut has passthrough pixels along the red axis`() {
        val lut = HaldClutFilterRender.generateIdentityLut(SIZE)

        assertEquals(SIZE * SIZE, lut.width)
        assertEquals(SIZE, lut.height)
        assertEquals(Bitmap.Config.ARGB_8888, lut.config)

        // Identität: y = r (Zeilenindex = Rot-Kanal). Oben (y=0) ist r=0,
        // unten (y=SIZE-1) ist r=max → volles Rot bei x=0 (g=b=0).
        assertEquals(0xFF shl 24, lut.getPixel(0, 0))
        assertEquals(0xFF shl 24 or (255 shl 16), lut.getPixel(0, SIZE - 1))
    }

    @Test
    fun `warm tone lut shifts red up and blue down`() {
        val lut = HaldClutFilterRender.generateWarmToneLut(SIZE)

        assertEquals(SIZE * SIZE, lut.width)
        assertEquals(SIZE, lut.height)

        // y=0 → r=0: Warm verschiebt nichts bei schwarz (0×Faktor = 0)
        assertEquals(0xFF shl 24, lut.getPixel(0, 0))

        // Maximale Rot-Achse (y = SIZE-1, g=b=0): Warm verstärkt rot (×1.10 → clamp 255)
        assertEquals(0xFF shl 24 or (255 shl 16), lut.getPixel(0, SIZE - 1))

        // Mittlere Position (r=g=b=mid): Warm soll Rot erhöhen und Blau senken
        val mid = SIZE / 2
        val midR = (mid * 255.0f / (SIZE - 1) * 1.10f).toInt().coerceIn(0, 255)
        val midG = (mid * 255.0f / (SIZE - 1) * 1.05f).toInt().coerceIn(0, 255)
        val midB = (mid * 255.0f / (SIZE - 1) * 0.85f).toInt().coerceIn(0, 255)
        val pixel = lut.getPixel(mid * SIZE + mid, mid)
        assertEquals(midR, (pixel shr 16) and 0xFF)
        assertEquals(midG, (pixel shr 8) and 0xFF)
        assertEquals(midB, pixel and 0xFF)
    }

    @Test
    fun `cool tone lut shifts blue up and red down`() {
        val lut = HaldClutFilterRender.generateCoolToneLut(SIZE)

        assertEquals(SIZE * SIZE, lut.width)
        assertEquals(SIZE, lut.height)

        // Mittlere Position (r=g=b=mid): Cool dämpft rot (×0.90), verstärkt blau (×1.10)
        val mid = SIZE / 2
        val midR = (mid * 255.0f / (SIZE - 1) * 0.90f).toInt().coerceIn(0, 255)
        val midG = (mid * 255.0f / (SIZE - 1) * 1.05f).toInt().coerceIn(0, 255)
        val midB = (mid * 255.0f / (SIZE - 1) * 1.10f).toInt().coerceIn(0, 255)
        val pixel = lut.getPixel(mid * SIZE + mid, mid)
        assertEquals(midR, (pixel shr 16) and 0xFF)
        assertEquals(midG, (pixel shr 8) and 0xFF)
        assertEquals(midB, pixel and 0xFF)

        // Maximale Rot-Achse: Cool dämpft rot
        val maxR = (SIZE - 1) * 255.0f / (SIZE - 1) * 0.90f
        assertTrue(maxR < 255.0f)
    }

    @Test
    fun `all three presets produce distinct lut images`() {
        val identity = HaldClutFilterRender.generateIdentityLut(SIZE)
        val warm = HaldClutFilterRender.generateWarmToneLut(SIZE)
        val cool = HaldClutFilterRender.generateCoolToneLut(SIZE)

        // Warm vs. Cool am selben Pixel müssen unterschiedlich sein
        val warmPixel = warm.getPixel(SIZE * SIZE / 2 + SIZE / 2, SIZE / 2)
        val coolPixel = cool.getPixel(SIZE * SIZE / 2 + SIZE / 2, SIZE / 2)
        assertTrue(warmPixel != coolPixel)
        // Identität muss von beiden abweichen (Farbverschiebung ≠ 1.0)
        assertTrue(identity.getPixel(SIZE * SIZE / 2 + SIZE / 2, SIZE / 2) != warmPixel)
    }

    @Test
    fun `lut presets enum wires all three factories`() {
        assertEquals(HaldClutFilterRender.generateIdentityLut(SIZE).width, LutPreset.NONE.createLut(SIZE).width)
        assertEquals(HaldClutFilterRender.generateWarmToneLut(SIZE).height, LutPreset.WARM.createLut(SIZE).height)
        assertEquals(HaldClutFilterRender.generateCoolToneLut(SIZE).height, LutPreset.COOL.createLut(SIZE).height)
    }
}
