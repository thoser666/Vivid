package com.vivid.feature.streaming

import org.junit.Assert.assertEquals
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
}
