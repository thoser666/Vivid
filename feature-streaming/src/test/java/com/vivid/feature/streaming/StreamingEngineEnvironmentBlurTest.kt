package com.vivid.feature.streaming

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.PrivacyZone
import com.vivid.feature.streaming.source.DisplayFactory
import com.vivid.feature.streaming.source.PlayerFactory
import com.vivid.feature.streaming.source.VideoSourceRegistry
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * P4.1 — Umgebungs-Anonymisierung in der [StreamingEngine] (Skizze §11.4).
 *
 * Verifiziert wird die Verdrahtung: der Toggle ist idempotent, erzeugt beim
 * Einschalten **genau eine** Maske (Fabrik injiziert), reicht sie an den
 * Composer und verbraucht keinen Ellipsen-Slot. Der Render-seitige Fail-Safe
 * (Ganzbild, solange keine Maske hochgeladen ist) ist in
 * `EnvironmentMaskTest`/`PrivacyBlurFilterRenderRobolectricTest` festgenagelt.
 */
class StreamingEngineEnvironmentBlurTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var streamingEngine: StreamingEngine

    /** Zählt die Fabrik-Aufrufe (die Maske selbst ist hier winzig). */
    private var masksCreated = 0

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every {
            context.getSystemService(Context.WINDOW_SERVICE)
        } returns mockk<android.view.WindowManager>(relaxed = true)
        every { context.filesDir } returns tempDir
        camera = mockk(relaxed = true)
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        glStreamInterface = mockk(relaxed = true)
        every { camera.glInterface } returns glStreamInterface

        streamingEngine = StreamingEngine(
            context,
            object : CameraFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 = camera
            },
            object : DisplayFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiDisplay =
                    mockk(relaxed = true)
            },
            object : PlayerFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiFromFile =
                    mockk(relaxed = true)
            },
            VideoSourceRegistry(),
        )
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
        // P4.1: keine 147-kB-Maske pro Testfall; die Fabrik zählt nur.
        streamingEngine.environmentMaskFactory = {
            masksCreated += 1
            EnvironmentMask(2, 2, ByteArray(2 * 2 * EnvironmentMask.RGBA_CHANNELS))
        }
    }

    @Test
    fun `environment toggle flips the engine state idempotently`() {
        assertFalse(streamingEngine.environmentBlurEnabled.value)
        assertFalse(streamingEngine.environmentBlurPublished)
        assertNull(streamingEngine.environmentMaskSnapshot)

        assertTrue(streamingEngine.setEnvironmentBlur(true))
        assertTrue(streamingEngine.environmentBlurEnabled.value)
        assertTrue(streamingEngine.environmentBlurPublished, "der Composer muss den Zustand kennen")
        // Zweiter Aufruf mit gleichem Wert: kein Zustandswechsel, keine neue Maske.
        assertFalse(streamingEngine.setEnvironmentBlur(true))
        assertEquals(1, masksCreated)

        assertTrue(streamingEngine.setEnvironmentBlur(false))
        assertFalse(streamingEngine.environmentBlurEnabled.value)
        assertFalse(streamingEngine.environmentBlurPublished)
        assertEquals(1, masksCreated, "Ausschalten erzeugt keine neue Maske")
    }

    @Test
    fun `enabling creates and publishes exactly one mask`() {
        assertTrue(streamingEngine.setEnvironmentBlur(true))

        assertEquals(1, masksCreated)
        assertNotNull(streamingEngine.environmentMaskSnapshot)
        // Die Maske muss beim Composer ankommen — nicht nur erzeugt werden.
        assertSame(
            streamingEngine.environmentMaskSnapshot,
            streamingEngine.environmentMaskPublished,
        )

        // Aus und wieder an: die Maske wird neu erzeugt (der Render hält die
        // letzte; ein erneutes Einschalten darf nicht auf einen veralteten
        // Snapshot zeigen, wenn die Fabrik später Frames liefert).
        streamingEngine.setEnvironmentBlur(false)
        streamingEngine.setEnvironmentBlur(true)
        assertEquals(2, masksCreated)
    }

    @Test
    fun `environment preference survives a master toggle cycle`() {
        assertTrue(streamingEngine.setEnvironmentBlur(true))

        streamingEngine.setPrivacyEnabled(true)
        streamingEngine.setPrivacyEnabled(false)

        // Der Modus ist Persistenz-Ziel der ViewModel-Schicht, kein
        // Ketten-Zustand — der Master-Toggle darf ihn nicht zurücksetzen.
        assertTrue(streamingEngine.environmentBlurEnabled.value)
        assertEquals(1, masksCreated)
    }

    @Test
    fun `environment does not consume ellipse slots`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setEnvironmentBlur(true))

        val zones = List(PrivacyZone.MAX_ZONES) { i ->
            PrivacyZone(
                centerX = 0.15f + 0.2f * i,
                centerY = 0.3f,
                radiusX = 0.1f,
                radiusY = 0.1f,
            )
        }
        streamingEngine.setPrivacyZones(zones)
        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        // Die Maske ist eine Textur, kein Ellipsen-Slot: die publizierten
        // Ellipsen bleiben exakt die Zonen (hier 4).
        assertEquals(PrivacyZone.MAX_ZONES, streamingEngine.publishedPrivacyEllipses.value.size)
        assertTrue(streamingEngine.environmentBlurEnabled.value)
    }

    @Test
    fun `no mask is created while the toggle stays off`() {
        streamingEngine.initializeCamera()
        streamingEngine.setPrivacyEnabled(true)
        streamingEngine.setPrivacyZones(
            listOf(PrivacyZone(centerX = 0.5f, centerY = 0.5f, radiusX = 0.2f, radiusY = 0.2f)),
        )
        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(0, masksCreated)
        assertNull(streamingEngine.environmentMaskSnapshot)
    }
}
