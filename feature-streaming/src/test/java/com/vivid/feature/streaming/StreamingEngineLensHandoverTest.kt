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
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * VIVID-57: die Vorschau schließt, **bevor** die Linse umgeschaltet wird
 * (`docs/sentry-issues.md` §5.20, Sammel-Issue #412).
 *
 * Die gewählte Linse steht nur zur Verfügung, solange die Idle-Kamera offen ist,
 * deshalb las `syncSelectedCamera()` sie vor dem Schließen — und schaltete dort
 * auch sofort um. RootEncoder stellt den Reopen in seinen Camera-Handler ein
 * (`switchCamera` → `reOpenCamera` → `openCameraId`); gewinnt dieser Task das
 * Rennen gegen das direkt folgende `closeCamera()` der eigenen Idle-Sitzung,
 * antwortet Camera2 mit `Open camera <id> failed`. Der STREAM-Pfad meldet das
 * als `idlePreview=false` — genau der Stack der Gruppe.
 *
 * Geprüft wird die Reihenfolge an **beiden** Aufrufstellen (Streamstart,
 * Standalone-Aufnahme) plus die Gegenprobe, dass ohne offene Vorschau nicht
 * umgeschaltet wird.
 */
class StreamingEngineLensHandoverTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine
    private lateinit var idle: Camera2ApiManager

    /** Reihenfolge der beiden konkurrierenden Aufrufe. */
    private val calls = mutableListOf<String>()

    private var selectedId = "0"

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
        every { camera.currentCameraId } answers { selectedId }
        every { camera.glInterface } returns mockk<GlStreamInterface>(relaxed = true)
        every { camera.switchCamera(any<String>()) } answers {
            selectedId = firstArg()
            calls += "camera.switch"
        }

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
        streamingEngine.scheduleRecoveryPreview = { _, _ -> }
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
        streamingEngine.dispatchSceneTask = { it() }
        streamingEngine.initializeCamera()

        idle = mockk(relaxed = true)
        every { idle.getCurrentCameraId() } returns "1"
        every { idle.closeCamera() } answers { calls += "idle.close" }
        streamingEngine.idlePreviewFactory = { idle }
    }

    private fun openIdlePreview() {
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
    }

    @Test
    fun `a stream start closes the idle camera before switching the lens`() {
        openIdlePreview()

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(
            listOf("idle.close", "camera.switch"),
            calls,
            "der Reopen darf nicht gegen das closeCamera der eigenen Idle-Sitzung laufen",
        )
        assertEquals("1", selectedId, "die gewählte Linse muss die Ziel-Linse bleiben")
    }

    @Test
    fun `a standalone recording closes the idle camera before switching the lens`() {
        openIdlePreview()

        streamingEngine.startReplay(100L, includeAudio = false)

        assertEquals(
            listOf("idle.close", "camera.switch"),
            calls,
            "die Aufnahme benutzt dieselbe Übergabe wie der Streamstart",
        )
        assertEquals("1", selectedId)
    }

    @Test
    fun `without an idle preview no lens is switched`() {
        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        verify(exactly = 0) { camera.switchCamera(any<String>()) }
        assertEquals("0", selectedId, "ohne Vorschau gibt es keine Ziel-Linse")
    }
}
