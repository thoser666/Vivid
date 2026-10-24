package com.vivid.feature.streaming

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.library.view.GlStreamInterface
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract-Tests des [PrivacyComposer] (P0 der Anonymisierungs-Skizze,
 * docs/architecture/privacy-anonymization.md §4): kanonischer Ketten-Rebuild
 * `[Privacy? @0] + [VideoFilter?] + [LUT?] + [LowLight?]` — Anonymisierung an
 * Position 0 (Garantie-Postulat), kreative Filter kombinierbar statt
 * Single-Slot-Exklusivität. Die Render-Fabriken sind injiziert (Mocks), damit
 * die Kanon-Reihenfolge auf der JVM verifizierbar ist (Identitäts-Assertions —
 * toString-Stubbing meidet MockK zu Recht); die Bestands-Engine-Tests
 * (StreamingEngineFiltersTest) frieren zusätzlich die JVM-Realität mit den
 * echten (null-liefernden) Fabriken ein.
 */
class PrivacyComposerTest {

    /** Nimmt die GL-Aufrufe des Composer-Rebuilds auf. */
    private class RecordingGl {
        val ops = mutableListOf<String>()
        val added = mutableListOf<BaseFilterRender>()

        val gl = mockk<GlStreamInterface>(relaxed = true) {
            every { clearFilters() } answers { ops.add("clear") }
            every { addFilter(any()) } answers {
                added.add(firstArg())
                ops.add("add")
            }
        }
    }

    private fun composer(
        recorder: RecordingGl,
        filter: VideoFilter = VideoFilter.NONE,
        lutRender: BaseFilterRender? = null,
        lowLight: Boolean = false,
        privacyRender: PrivacyBlurFilterRender? = null,
        videoRender: BaseFilterRender? = null,
        lowLightRender: BaseFilterRender? = null,
        emptySource: BaseFilterRender = mockk(relaxed = true),
    ): PrivacyComposer {
        val filterController = VideoFilterController()
        if (filter != VideoFilter.NONE) {
            filterController.setFilter(filter) { }
        }
        val lowLightController = LowLightBoostController()
        if (lowLight) lowLightController.setEnabled(true) { }
        return PrivacyComposer(
            filterController = filterController,
            lowLightBoostController = lowLightController,
            lutController = LutController(),
            lutSize = 16,
            privacyRenderFactory = { privacyRender },
            videoFilterRenderFactory = { videoRender },
            lutRenderFactory = { lutRender },
            lowLightRenderFactory = { lowLightRender },
            emptySourceRenderFactory = { emptySource },
        )
    }

    @Test
    fun `empty scene replaces processed camera image with black and restores the source when disabled`() {
        val recorder = RecordingGl()
        val video = mockk<BaseFilterRender>(relaxed = true)
        val black = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(recorder, filter = VideoFilter.SEPIA, videoRender = video, emptySource = black)
        composer.sceneEmpty = true
        composer.requestRebuild(recorder.gl)
        assertEquals(listOf(video, black), recorder.added)
        recorder.added.clear()
        composer.sceneEmpty = false
        composer.requestRebuild(recorder.gl)
        assertEquals(listOf(video), recorder.added)
    }

    @Test
    fun `experimental rebuild preserves external overlay while replacing owned filters`() {
        val chain = mutableListOf<BaseFilterRender>()
        val overlay = mockk<BaseFilterRender>(relaxed = true)
        val video = mockk<BaseFilterRender>(relaxed = true)
        val gl = mockk<GlStreamInterface>(relaxed = true) {
            every { addFilter(any<Int>(), any()) } answers { chain.add(firstArg(), secondArg()) }
            every { removeFilter(any<BaseFilterRender>()) } answers { chain.remove(firstArg()); Unit }
            every { clearFilters() } answers { chain.clear() }
        }
        val composer = composer(RecordingGl(), filter = VideoFilter.GRAYSCALE, videoRender = video)
        composer.preserveExternalFilters = true
        composer.requestRebuild(gl)
        chain.add(overlay)
        composer.requestRebuild(gl)
        assertEquals(listOf(video, overlay), chain)
        io.mockk.verify(exactly = 0) { gl.clearFilters() }
    }

    @Test
    fun `empty slots rebuild to a single clear`() {
        val recorder = RecordingGl()
        val composer = composer(recorder)
        composer.requestRebuild(recorder.gl)
        assertEquals(listOf("clear"), recorder.ops)
        assertEquals(0, recorder.added.size)
    }

    @Test
    fun `single creative filter reproduces the legacy chain`() {
        val recorder = RecordingGl()
        val video = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(recorder, filter = VideoFilter.GRAYSCALE, videoRender = video)
        composer.requestRebuild(recorder.gl)
        assertEquals(listOf("clear", "add"), recorder.ops)
        assertSame(video, recorder.added.single())
    }

    @Test
    fun `creative filters combine in canonical order`() {
        val recorder = RecordingGl()
        val video = mockk<BaseFilterRender>(relaxed = true)
        val lut = mockk<BaseFilterRender>(relaxed = true)
        val lowLight = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(
            recorder,
            filter = VideoFilter.SEPIA,
            lutRender = lut,
            lowLight = true,
            videoRender = video,
            lowLightRender = lowLight,
        )
        composer.requestRebuild(recorder.gl)
        assertEquals(listOf("clear", "add", "add", "add"), recorder.ops)
        assertSame(video, recorder.added[0])
        assertSame(lut, recorder.added[1])
        assertSame(lowLight, recorder.added[2])
    }

    @Test
    fun `privacy sits at position zero in front of all creative filters`() {
        val recorder = RecordingGl()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true)
        val video = mockk<BaseFilterRender>(relaxed = true)
        val lut = mockk<BaseFilterRender>(relaxed = true)
        val lowLight = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(
            recorder,
            filter = VideoFilter.SEPIA,
            lutRender = lut,
            lowLight = true,
            privacyRender = privacy,
            videoRender = video,
            lowLightRender = lowLight,
        )
        assertTrue(composer.setPrivacyEnabled(true, recorder.gl))
        assertEquals(listOf("clear", "add", "add", "add", "add"), recorder.ops)
        assertSame(privacy, recorder.added[0])
        assertSame(video, recorder.added[1])
        assertSame(lut, recorder.added[2])
        assertSame(lowLight, recorder.added[3])
    }

    @Test
    fun `disabling privacy keeps the creative chain intact`() {
        val recorder = RecordingGl()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true)
        val video = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(
            recorder,
            filter = VideoFilter.GRAYSCALE,
            privacyRender = privacy,
            videoRender = video,
        )
        composer.setPrivacyEnabled(true, recorder.gl)
        recorder.ops.clear()
        recorder.added.clear()
        composer.setPrivacyEnabled(false, recorder.gl)
        assertEquals(listOf("clear", "add"), recorder.ops)
        assertSame(video, recorder.added.single())
        assertFalse(composer.privacyEnabled.value)
    }

    @Test
    fun `setPrivacyEnabled is idempotent and does not rebuild`() {
        val recorder = RecordingGl()
        val composer = composer(recorder, privacyRender = mockk(relaxed = true))
        assertTrue(composer.setPrivacyEnabled(true, recorder.gl))
        val callsAfterEnable = recorder.ops.size
        assertFalse(composer.setPrivacyEnabled(true, recorder.gl))
        assertEquals(callsAfterEnable, recorder.ops.size)
    }

    @Test
    fun `null gl skips the rebuild but keeps the state`() {
        val composer = composer(RecordingGl())
        assertTrue(composer.setPrivacyEnabled(true, null))
        assertTrue(composer.privacyEnabled.value)
        composer.requestRebuild(null)
    }

    @Test
    fun `failing privacy render factory keeps the rebuild well-formed`() {
        val recorder = RecordingGl()
        val video = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(
            recorder,
            filter = VideoFilter.GRAYSCALE,
            privacyRender = null,
            videoRender = video,
        )
        composer.setPrivacyEnabled(true, recorder.gl)
        // Privacy-Slot fällt aus, Kette bleibt wohlgeformt (kein Crash, kein
        // Teilzustand) — JVM-Realität ohne GL.
        assertEquals(listOf("clear", "add"), recorder.ops)
        assertSame(video, recorder.added.single())
        assertTrue(composer.privacyEnabled.value)
    }

    @Test
    fun `privacy render instance is reused across rebuilds`() {
        val recorder = RecordingGl()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true)
        val video = mockk<BaseFilterRender>(relaxed = true)
        val composer = composer(
            recorder,
            filter = VideoFilter.GRAYSCALE,
            privacyRender = privacy,
            videoRender = video,
        )
        composer.setPrivacyEnabled(true, recorder.gl)
        recorder.added.clear()
        composer.requestRebuild(recorder.gl)
        assertSame(privacy, recorder.added[0])
        recorder.added.clear()
        composer.requestRebuild(recorder.gl)
        // Bei jedem Rebuild dieselbe Instanz in der Kette (Ellipsen-Uniforms
        // bleiben erhalten, kein Ketten-Flackern).
        assertSame(privacy, recorder.added[0])
    }

    @Test
    fun `setEllipses forwards to the privacy render without touching the chain`() {
        val recorder = RecordingGl()
        val captured = mutableListOf<List<PrivacyEllipse>>()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true) {
            every { setEllipses(any()) } answers { captured.add(firstArg()) }
        }
        val composer = composer(recorder, privacyRender = privacy)
        composer.setPrivacyEnabled(true, recorder.gl)
        val callsBefore = recorder.ops.size

        val ellipses = listOf(PrivacyEllipse(0.5f, 0.5f, 0.1f, 0.1f))
        composer.setEllipses(ellipses)
        assertEquals(callsBefore, recorder.ops.size)
        assertEquals(1, captured.size)
        assertEquals(ellipses, captured[0])
    }

    @Test
    fun `setEllipses is a no-op without a privacy render`() {
        val composer = composer(RecordingGl(), privacyRender = null)
        // Darf nicht werfen (Uniform-Update landet beim nächsten Rebuild).
        composer.setEllipses(listOf(PrivacyEllipse(0.5f, 0.5f, 0.1f, 0.1f)))
    }

    // --- P2b: Voll-Frame-Modus -----------------------------------------------

    @Test
    fun `setFullFrame forwards flag and grid to the render without touching the chain`() {
        val recorder = RecordingGl()
        val fullFrameCalls = mutableListOf<Boolean>()
        val gridCalls = mutableListOf<Float>()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true) {
            every { setFullFrame(any()) } answers { fullFrameCalls.add(firstArg()) }
            every { setPixelGrid(any()) } answers { gridCalls.add(firstArg()) }
        }
        val composer = composer(recorder, privacyRender = privacy)
        composer.setPrivacyEnabled(true, recorder.gl)
        val callsBefore = recorder.ops.size

        composer.setFullFrame(true)
        // Reiner Uniform-Zustand: die Kette wird nicht neu gebaut.
        assertEquals(callsBefore, recorder.ops.size)
        assertEquals(listOf(true), fullFrameCalls)
        assertEquals(listOf(PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID), gridCalls)
        assertTrue(composer.fullFrame)

        // Zurück auf die Zonen-Stufe: Flag aus, grobe Stufe auf Standard.
        composer.setFullFrame(false)
        assertEquals(listOf(true, false), fullFrameCalls)
        assertEquals(
            listOf(
                PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID,
                PrivacyBlurFilterRender.DEFAULT_PIXEL_GRID,
            ),
            gridCalls,
        )
        assertFalse(composer.fullFrame)
    }

    @Test
    fun `setFullFrame is a no-op without a privacy render`() {
        val composer = composer(RecordingGl(), privacyRender = null)
        // Darf nicht werfen; der Soll-Zustand bleibt erhalten und greift beim
        // nächsten Rebuild (JVM-Realität ohne GL).
        composer.setFullFrame(true)
        assertTrue(composer.fullFrame)
    }

    @Test
    fun `full frame state survives a chain rebuild`() {
        val recorder = RecordingGl()
        val privacy = mockk<PrivacyBlurFilterRender>(relaxed = true)
        val composer = composer(recorder, privacyRender = privacy)
        composer.setPrivacyEnabled(true, recorder.gl)
        composer.setFullFrame(true)

        composer.requestRebuild(recorder.gl)

        // Der Rebuild berührt den Voll-Frame-Soll-Zustand nicht (die
        // wiederverwendete Render-Instanz trägt ihn ohnehin weiter).
        assertTrue(composer.fullFrame)
    }

    // --- PrivacyEllipse-Modell-Verträge --------------------------------------

    @Test
    fun `privacy ellipse validates coordinates`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivacyEllipse(1.5f, 0.5f, 0.1f, 0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrivacyEllipse(0.5f, 0.5f, -0.1f, 0.1f)
        }
        // Deaktivierter Slot (Radius 0) ist gültig.
        assertEquals(0f, PrivacyEllipse.DISABLED.radiusX)
    }
}
