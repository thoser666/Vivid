package com.vivid.feature.streaming

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Tests für die Pfade, die auf der reinen JVM (android.jar-Stub)
 * nicht testbar waren:
 *
 * 1. [LutController.loadCustomLut] mit einem **echten** Bitmap (Happy-Path):
 *    konstruiert einen echten [HaldClutFilterRender] und verdrahtet ihn.
 *    (Der Graceful-Failure-Pfad ist bereits in LutControllerTest auf der JVM
 *    abgedeckt — dort wirft android.opengl.Matrix.)
 * 2. [ReplayLibraryViewModel.shareIntent] Happy-Path: echte FileProvider-
 *    Uri-Auflösung + ACTION_SEND-Intent-Verdrahtung.
 *
 * Windows-Einschränkung: androidx FileProvider kann auf der Windows-JVM keine
 * Canonical-Paths matchen (`belongsToRoot` vergleicht mit hartcodiertem "/",
 * Windows-Canonical-Paths nutzen "\\\" — Separator-Bug in androidx.core).
 * Der shareIntent-Test läuft daher nur auf Linux (CI); der loadCustomLut-Test
 * ist Separator-unabhängig und läuft überall.
 *
 * Pinnt SDK 34, da Robolectric 4.14.1 bis SDK 35 emuliert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LutAndShareRobolectricTest {

    @Test
    fun `loadCustomLut with a real bitmap wires the render and clears the preset`() {
        val controller = LutController()

        // Vorher: WARM aktiv, damit der Wechsel zu Custom sichtbar wird
        assertTrue(controller.setPreset(LutPreset.WARM, 8) { })
        assertEquals(LutPreset.WARM, controller.activePreset.value)

        val lutBitmap = HaldClutFilterRender.generateWarmToneLut(8)

        var appliedRender: Any? = null
        val applied = controller.loadCustomLut(
            bitmap = lutBitmap,
            lutSize = 8,
            applyLut = { render -> appliedRender = render },
        )

        assertTrue(applied)
        assertTrue(appliedRender is HaldClutFilterRender)
        // Custom ist kein Preset -> Auswahl fällt auf NONE zurück
        assertEquals(LutPreset.NONE, controller.activePreset.value)
    }

    @Test
    fun `shareIntent builds an ACTION_SEND intent with a file provider uri`() {
        // Separator-Bug in androidx FileProvider: nur auf Linux (CI) lauffähig
        assumeTrue(
            "FileProvider-Uri-Test läuft nur auf Linux (Windows-Separator-Bug in androidx.core)",
            System.getProperty("os.name").lowercase().contains("linux"),
        )

        val tempDir = kotlin.io.path.createTempDirectory(prefix = "replays-share").toFile()
        val video = java.io.File(tempDir, "clip.mp4").apply { writeBytes(ByteArray(64)) }

        val library = ReplayLibrary(ReplayStorage(tempDir, maxFiles = 3))
        val viewModel = ReplayLibraryViewModel(
            library = library,
            appContext = ApplicationProvider.getApplicationContext(),
        )
        val item = ReplayItem(
            file = video,
            name = video.name,
            sizeBytes = video.length(),
            lastModified = video.lastModified(),
        )

        val intent = viewModel.shareIntent(item)

        assertNotNull(intent)
        assertEquals(android.content.Intent.ACTION_SEND, intent!!.action)
        assertEquals("video/mp4", intent.type)
        val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertTrue(uri!!.toString().endsWith("clip.mp4"))
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

        tempDir.deleteRecursively()
    }

    @Test
    fun `shareIntent returns null for a missing file`() {
        val tempDir = kotlin.io.path.createTempDirectory(prefix = "replays-missing").toFile()
        val library = ReplayLibrary(ReplayStorage(tempDir, maxFiles = 3))
        val viewModel = ReplayLibraryViewModel(
            library = library,
            appContext = ApplicationProvider.getApplicationContext(),
        )
        val ghost = java.io.File(tempDir, "gone.mp4")

        assertNull(viewModel.shareIntent(ReplayItem(ghost, ghost.name, 0, 0)))

        tempDir.deleteRecursively()
    }
}
