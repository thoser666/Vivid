package com.vivid.feature.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Probe-Test: verifiziert, dass Robolectric in diesem Modul unter JDK 25 / AGP 9
 * startet und die kritische API (Bitmap.createBitmap) wirklich funktioniert.
 * Pinnt SDK 34, da Robolectric 4.14.1 bis SDK 35 emuliert.
 *
 * Hinweis: Der FileProvider-Ur-Test lebt in [ReplayLibraryViewModelRobolectricTest]
 * (dort mit Windows-Ignore-Gate, da androidx FileProvider auf der Windows-JVM
 * keine Canonical-Paths matchen kann — Separator-Bug, nur Linux/Android ok).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RobolectricProbeTest {

    @Test
    fun `application context is available`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)
        // AGP hängt für die Unit-Test-Variante ein ".test"-Suffix an die package an
        assertEquals("com.vivid.feature.streaming.test", context.packageName)
    }

    @Test
    fun `bitmap createBitmap and getPixel work for real`() {
        val pixels = intArrayOf(
            0xFF112233.toInt(),
            0xFF445566.toInt(),
            0xFF778899.toInt(),
            0xFFAABBCC.toInt(),
        )
        val bitmap = android.graphics.Bitmap.createBitmap(pixels, 2, 2, android.graphics.Bitmap.Config.ARGB_8888)

        assertNotNull(bitmap)
        assertEquals(2, bitmap.width)
        assertEquals(2, bitmap.height)
        assertEquals(0xFF112233.toInt(), bitmap.getPixel(0, 0))
        assertEquals(0xFFAABBCC.toInt(), bitmap.getPixel(1, 1))
    }
}
