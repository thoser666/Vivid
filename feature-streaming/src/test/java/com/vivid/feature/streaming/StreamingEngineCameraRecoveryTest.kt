package com.vivid.feature.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.view.GlStreamInterface
import com.vivid.feature.streaming.source.DisplayFactory
import com.vivid.feature.streaming.source.PlayerFactory
import com.vivid.feature.streaming.source.VideoSourceRegistry
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Vorschau-Wiederherstellung nach einem fehlgeschlagenen Stream-Start
 * (`docs/sentry-issues.md` §5.12).
 *
 * Der frühere Code rief im Fehlerzweig sofort `startIdlePreviewIfReady()`. War
 * der Start gescheitert, weil die Kamera noch belegt war, traf der zweite
 * `openCamera`-Versuch im selben Aufrufzug auf dieselbe Belegung — dem
 * Kamera-Server blieb keine Zeit, das Gerät freizugeben (`docs/sentry-issues.md`
 * §5.12).
 *
 * Geprüft wird: kein Kamera-Open im selben Zug, genau eine geplante
 * Wiederherstellung, und diese baut die Vorschau nach Ablauf wirklich auf.
 */
class StreamingEngineCameraRecoveryTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine

    /** Zählt die Aufbauten der Idle-Preview. */
    private var previewFactories = 0

    /** Auffangbecken für die geplante Aktion (statt eines echten Handlers). */
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.WINDOW_SERVICE) } returns
            mockk<android.view.WindowManager>(relaxed = true)
        every { context.filesDir } returns tempDir
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns
            PackageManager.PERMISSION_GRANTED
        camera = mockk(relaxed = true)
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        every { camera.glInterface } returns mockk<GlStreamInterface>(relaxed = true)

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
        streamingEngine.displayRotationDegrees = { 0 }
        streamingEngine.cameraAvailability = { true }
        streamingEngine.idlePreviewFactory = {
            previewFactories += 1
            mockk<Camera2ApiManager>(relaxed = true)
        }
        streamingEngine.scheduleRecoveryPreview = { delayMs, action ->
            scheduled += delayMs to action
        }
        // A1: Open und Main-Rueckkehr inline, damit die Zaehler synchron bleiben.
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
        streamingEngine.initializeCamera()
    }

    /** Eine angehängte Vorschau ist die Vorbedingung für den Wiederaufbau. */
    private fun attachSurface(): Int {
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
        val opened = previewFactories
        assertEquals(1, opened, "Vorbedingung: die erste Vorschau steht")
        return opened
    }

    private fun failOneStart() {
        every { camera.prepareVideo() } returns false
        streamingEngine.startStream(listOf("rtmp://example.com/live"))
    }

    @Test
    fun `a failed start schedules the preview instead of reopening it in the same call`() {
        val opened = attachSurface()

        failOneStart()

        assertEquals(opened, previewFactories, "kein zweiter Kamera-Open im selben Zug (§5.12)")
        assertEquals(1, scheduled.size, "genau eine geplante Wiederherstellung")
        assertTrue(
            scheduled.single().first > 0,
            "die Wiederherstellung ist verzögert, damit der Kamera-Server das Gerät freigibt",
        )
    }

    @Test
    fun `the scheduled recovery rebuilds the preview once`() {
        val opened = attachSurface()
        failOneStart()

        scheduled.single().second()

        assertEquals(opened + 1, previewFactories, "nach Ablauf wird die Vorschau genau einmal aufgebaut")
    }

    @Test
    fun `a second failed start does not queue another recovery`() {
        attachSurface()
        failOneStart()
        failOneStart()

        assertEquals(1, scheduled.size, "das Budget pro Fehlerfall verhindert die Schleife")
    }

    @Test
    fun `the recovery stays inert while a new stream is running`() {
        val opened = attachSurface()
        failOneStart()
        val recovery = scheduled.single().second

        // Ein neuer Start gelingt inzwischen — die Vorschau darf ihn nicht stören.
        every { camera.prepareVideo() } returns true
        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        assertTrue(
            streamingEngine.streamingState.value is StreamingState.Preparing ||
                streamingEngine.streamingState.value is StreamingState.Streaming,
            "Vorbedingung: der zweite Start läuft (${streamingEngine.streamingState.value})",
        )

        recovery()

        assertEquals(opened, previewFactories, "während Preparing/Streaming öffnet keine zweite Kamera")
    }
}
