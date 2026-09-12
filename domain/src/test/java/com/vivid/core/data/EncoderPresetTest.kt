package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Fallback-Kette des Encoder-Presets (v0.6.0 „4K/60fps + HEVC“):
 * Codec-Auflösung (HEVC-First bei AUTO), Preset-Abstufung, Strict-Modus.
 * Läuft ohne Android-Framework — [EncoderCapabilities] ist ein Fake.
 */
class EncoderPresetTest {

    private data class Cap(val mime: String, val width: Int, val height: Int, val fps: Int)

    /** Fake: unterstützt exakt die angegebenen (mime, w, h, fps)-Kombis. */
    private class FakeCaps(private val supported: Set<Cap>) : EncoderCapabilities {
        override fun supports(mime: String, width: Int, height: Int, fps: Int): Boolean =
            Cap(mime, width, height, fps) in supported
    }

    private fun caps(vararg entries: Cap) = FakeCaps(entries.toSet())
    private fun hevc(w: Int, h: Int, fps: Int) = Cap("video/hevc", w, h, fps)
    private fun avc(w: Int, h: Int, fps: Int) = Cap("video/avc", w, h, fps)

    // --- fromName (Robustheit, Persistenz) -----------------------------------

    @Test
    fun `fromName liest alle Presets und faellt auf FHD30 zurueck`() {
        assertEquals(EncoderPreset.S_4K60, EncoderPreset.fromName("S_4K60"))
        assertEquals(EncoderPreset.FHD60, EncoderPreset.fromName("fhd60"))
        assertEquals(EncoderPreset.HD30, EncoderPreset.fromName("HD30"))
        assertEquals(EncoderPreset.FHD30, EncoderPreset.fromName("ULTRA_HD"))
        assertEquals(EncoderPreset.FHD30, EncoderPreset.fromName(null))
    }

    @Test
    fun `fromName der Codec-Präferenz fällt auf AUTO zurück`() {
        assertEquals(VideoCodecPreference.H265, VideoCodecPreference.fromName("h265"))
        assertEquals(VideoCodecPreference.AUTO, VideoCodecPreference.fromName("MPEG2"))
        assertEquals(VideoCodecPreference.AUTO, VideoCodecPreference.fromName(null))
    }

    @Test
    fun `toMimeType mappt alle Präferenzen`() {
        assertEquals("video/avc", VideoCodecPreference.H264.toMimeType())
        assertEquals("video/hevc", VideoCodecPreference.H265.toMimeType())
        assertEquals("video/av01", VideoCodecPreference.AV1.toMimeType())
        assertEquals("", VideoCodecPreference.AUTO.toMimeType())
    }

    // --- AUTO: HEVC-First, dann H.264 -----------------------------------------

    @Test
    fun `AUTO wählt HEVC im Wunsch-Preset wenn die Hardware es kann`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.AUTO,
            EncoderPreset.S_4K60,
            caps(hevc(3840, 2160, 60)),
        )
        assertEquals(VideoCodecPreference.H265, resolved.codec)
        assertEquals(EncoderPreset.S_4K60, resolved.preset)
        assertFalse(resolved.fallbackApplied)
    }

    @Test
    fun `AUTO fällt auf H264 im Wunsch-Preset zurück wenn HEVC fehlt`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.AUTO,
            EncoderPreset.S_4K60,
            caps(avc(3840, 2160, 60)),
        )
        assertEquals(VideoCodecPreference.H264, resolved.codec)
        assertEquals(EncoderPreset.S_4K60, resolved.preset)
        assertTrue(resolved.fallbackApplied)
    }

    // --- Preset-Abstufung ------------------------------------------------------

    @Test
    fun `HEVC in 4K nicht moeglich - Abstufung bis HEVC geht`() {
        // HEVC kann nur 1080p60 — HEVC-First gewinnt mit herabgesetztem Preset
        // (Codec-Wunsch schlägt Auflösungs-Wunsch).
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.AUTO,
            EncoderPreset.S_4K60,
            caps(hevc(1920, 1080, 60)),
        )
        assertEquals(VideoCodecPreference.H265, resolved.codec)
        assertEquals(EncoderPreset.FHD60, resolved.preset)
        assertTrue(resolved.fallbackApplied)
    }

    @Test
    fun `explizites H265 stuft Preset ab statt auf H264 zu wechseln`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.H265,
            EncoderPreset.FHD30,
            caps(hevc(1280, 720, 30)),
        )
        assertEquals(VideoCodecPreference.H265, resolved.codec)
        assertEquals(EncoderPreset.HD30, resolved.preset)
        assertTrue(resolved.fallbackApplied)
    }

    @Test
    fun `FHD30-Preset-Kette stuft bis SD30 ab`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.H265,
            EncoderPreset.FHD30,
            caps(hevc(854, 480, 30)),
        )
        assertEquals(EncoderPreset.SD30, resolved.preset)
    }

    // --- Explizite Präferenz ---------------------------------------------------

    @Test
    fun `explizites H264 bleibt H264 ohne Fallback-Marker`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.H264,
            EncoderPreset.FHD30,
            caps(avc(1920, 1080, 30)),
        )
        assertEquals(VideoCodecPreference.H264, resolved.codec)
        assertEquals(EncoderPreset.FHD30, resolved.preset)
        assertFalse(resolved.fallbackApplied)
    }

    @Test
    fun `explizites AV1 stuft ab, letzter Ausweg ist H264 im naechstkleineren Preset`() {
        // AV1 nirgends unterstützt, H.264 nur in 720p30:
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.AV1,
            EncoderPreset.FHD30,
            caps(avc(1280, 720, 30)),
        )
        assertEquals(VideoCodecPreference.H264, resolved.codec)
        assertEquals(EncoderPreset.HD30, resolved.preset)
        assertTrue(resolved.fallbackApplied)
    }

    // --- Letzter Ausweg (leere/lehnende Capability-Liste) ----------------------

    @Test
    fun `leere Capability-Liste - H264 im Wunsch-Preset als letzter Ausweg`() {
        val resolved = resolveEncoderConfig(
            VideoCodecPreference.AUTO,
            EncoderPreset.FHD30,
            caps(),
        )
        assertEquals(VideoCodecPreference.H264, resolved.codec)
        assertEquals(EncoderPreset.FHD30, resolved.preset)
        assertTrue(resolved.fallbackApplied)
    }
}
