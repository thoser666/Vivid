package com.vivid.feature.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Tests für den [PrivacyBlurFilterRender] (P0 der Anonymisierungs-
 * Skizze): die Render-Konstruktion braucht `ByteBuffer.allocateDirect` (echte
 * NIO — auf der reinen JVM mit android.jar-Stubs unbrauchbar, hier mit echtem
 * Framework lauffähig). Die GL-Aufrufe selbst (initGlFilter/drawFilter)
 * bleiben App-Verifikation auf dem Gerät (wie bei den Bestands-Filtern —
 * Robolectric emuliert EGL/GL nicht nativ in allen Konstellationen); hier
 * frieren wir die Konstruktions-/Uniform-Update-Verträge ein.
 *
 * Pinnt SDK 34 (Muster HaldClutLutBitmapRobolectricTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyBlurFilterRenderRobolectricTest {

    @Test
    fun `render constructs without GL context`() {
        // Konstruktion: nur ByteBuffer/Matrix-Init — kein GL-Kontext nötig.
        // Erster Uniform-Update direkt danach (Snapshot-Mechanik, kein GL).
        val render = PrivacyBlurFilterRender()
        render.setEllipses(emptyList())
    }

    @Test
    fun `render accepts up to max ellipses and rejects more`() {
        val render = PrivacyBlurFilterRender()
        val exact = List(PrivacyBlurFilterRender.MAX_ELLIPSES) { PrivacyEllipse.DISABLED }
        render.setEllipses(exact)
        val tooMany = List(PrivacyBlurFilterRender.MAX_ELLIPSES + 1) { PrivacyEllipse.DISABLED }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            render.setEllipses(tooMany)
        }
    }

    @Test
    fun `render accepts empty and partial ellipse lists`() {
        val render = PrivacyBlurFilterRender()
        render.setEllipses(emptyList())
        render.setEllipses(listOf(PrivacyEllipse(0.25f, 0.25f, 0.1f, 0.1f)))
        render.setEllipses(
            listOf(
                PrivacyEllipse(0.25f, 0.25f, 0.1f, 0.1f),
                PrivacyEllipse(0.75f, 0.75f, 0.2f, 0.15f),
            ),
        )
    }

    // --- P2b: Voll-Frame-Modus + Mosaik-Kantenlänge --------------------------

    @Test
    fun `full frame and pixel grid are framesync snapshots with sane defaults`() {
        val render = PrivacyBlurFilterRender()
        // Defaults: Voll-Frame aus, feine Mosaik-Stufe für Zonen/Gesichter.
        assertFalse(render.fullFrameSnapshot)
        assertEquals(PrivacyBlurFilterRender.DEFAULT_PIXEL_GRID, render.pixelGridSnapshot, 1e-6f)

        render.setFullFrame(true)
        render.setPixelGrid(PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID)
        assertTrue(render.fullFrameSnapshot)
        assertEquals(
            PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID,
            render.pixelGridSnapshot,
            1e-6f,
        )
        // Die Voll-Frame-Stufe muss grober sein als die Zonen-Stufe — sonst
        // bleibt Umgebung lesbar (der eigentliche Standort-Schutz).
        assertTrue(
            PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID >
                PrivacyBlurFilterRender.DEFAULT_PIXEL_GRID,
        )

        render.setFullFrame(false)
        render.setPixelGrid(PrivacyBlurFilterRender.DEFAULT_PIXEL_GRID)
        assertFalse(render.fullFrameSnapshot)
    }

    @Test
    fun `pixel grid rejects non-positive values`() {
        val render = PrivacyBlurFilterRender()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            render.setPixelGrid(0f)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            render.setPixelGrid(-1f)
        }
    }

    // --- P4.1: Umgebungs-Modus + Masken-Textur ------------------------------

    @Test
    fun `environment mode starts disabled and claims no mask before upload`() {
        val render = PrivacyBlurFilterRender()
        // Der Modus darf erst nach einem Upload in den Shader wirken: sonst
        // wäre die schwarze 1x1-Textur „alles Hintergrund" (§11.6).
        assertFalse(render.environmentEnabledSnapshot)
        assertFalse(render.maskUploadedSnapshot)

        render.setEnvironmentMask(
            EnvironmentMask(4, 4, ByteArray(4 * 4 * EnvironmentMask.RGBA_CHANNELS)),
        )
        // Ohne GL-Kontext gibt es keinen Upload — `uMaskEnabled` bleibt 0.
        assertFalse(render.maskUploadedSnapshot)
        org.junit.Assert.assertTrue(
            environmentFullFrameFallback(
                fullFrame = render.fullFrameSnapshot,
                environmentEnabled = true,
                maskUploaded = render.maskUploadedSnapshot,
            ),
        )
    }

    @Test
    fun `environment toggle is a framesync snapshot`() {
        val render = PrivacyBlurFilterRender()
        render.setEnvironmentBlurEnabled(true)
        assertTrue(render.environmentEnabledSnapshot)
        render.setEnvironmentBlurEnabled(false)
        assertFalse(render.environmentEnabledSnapshot)
    }

    @Test
    fun `mask upload never flips the flag on a JVM without GL`() {
        // Der Upload passiert in drawFilter; hier wird nur der Snapshot
        // eingereicht. Der Fail-Safe muss deshalb weiter greifen.
        val render = PrivacyBlurFilterRender()
        render.setEnvironmentMask(PortraitMaskGenerator.create())
        render.setEnvironmentBlurEnabled(true)
        assertFalse(render.maskUploadedSnapshot)
        assertTrue(render.environmentEnabledSnapshot)
        org.junit.Assert.assertTrue(
            environmentFullFrameFallback(
                fullFrame = false,
                environmentEnabled = render.environmentEnabledSnapshot,
                maskUploaded = render.maskUploadedSnapshot,
            ),
        )
    }

    @Test
    fun `full frame still wins over the environment flags`() {
        val render = PrivacyBlurFilterRender()
        render.setFullFrame(true)
        render.setEnvironmentBlurEnabled(true)
        org.junit.Assert.assertTrue(
            environmentFullFrameFallback(
                fullFrame = render.fullFrameSnapshot,
                environmentEnabled = render.environmentEnabledSnapshot,
                maskUploaded = true,
            ),
        )
    }
}
