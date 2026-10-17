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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * P2b — Voll-Frame-Verpixelung in der [StreamingEngine] (Skizze §5).
 *
 * Was hier verifiziert wird, ist die Verdrahtung: der Toggle lebt im
 * Engine-Soll-Zustand (idempotent, überlebt Master-Toggle-Zyklen) und
 * **verbraucht keine Ellipsen-Slots** — der Voll-Frame-Modus ist ein
 * Shader-Uniform (`uFullFrame`), kein zusätzlicher Slot. Dass der Uniform
 * tatsächlich beim Render landet, friert PrivacyComposerTest ein.
 */
class StreamingEngineFullFrameTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var streamingEngine: StreamingEngine

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
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
    }

    @Test
    fun `full frame toggle flips the engine state idempotently`() {
        assertFalse(streamingEngine.fullFramePrivacy.value)

        assertTrue(streamingEngine.setFullFramePrivacy(true))
        assertTrue(streamingEngine.fullFramePrivacy.value)
        // Zweiter Aufruf mit gleichem Wert: kein Zustandswechsel.
        assertFalse(streamingEngine.setFullFramePrivacy(true))
        assertTrue(streamingEngine.fullFramePrivacy.value)

        assertTrue(streamingEngine.setFullFramePrivacy(false))
        assertFalse(streamingEngine.fullFramePrivacy.value)
    }

    @Test
    fun `full frame preference survives a master toggle cycle`() {
        assertTrue(streamingEngine.setFullFramePrivacy(true))

        // Master aus und wieder an: die Präferenz bleibt erhalten (sie ist
        // Persistenz-Ziel der ViewModel-Schicht, kein Ketten-Zustand).
        streamingEngine.setPrivacyEnabled(true)
        streamingEngine.setPrivacyEnabled(false)
        assertTrue(streamingEngine.fullFramePrivacy.value)

        // Und sie ist weiter umschaltbar.
        assertTrue(streamingEngine.setFullFramePrivacy(false))
    }

    @Test
    fun `full frame does not consume ellipse slots`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFullFramePrivacy(true))

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

        // Der Voll-Frame-Modus ist ein Uniform, kein Ellipsen-Slot: die
        // publizierten Ellipsen bleiben exakt die Zonen (hier 4).
        assertEquals(PrivacyZone.MAX_ZONES, streamingEngine.publishedPrivacyEllipses.value.size)
        assertTrue(streamingEngine.fullFramePrivacy.value)
    }
}
