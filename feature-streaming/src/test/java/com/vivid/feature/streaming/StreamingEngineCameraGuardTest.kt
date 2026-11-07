package com.vivid.feature.streaming

import android.content.Context
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Kamera-Cluster November 2026 (`docs/sentry-issues.md` §9).
 *
 * RootEncoder 2.7.5 wirft `CameraOpenException: Camera no detected` in
 * `getCameraIdForFacing`, aus einem von der Library selbst geposteten Task und
 * damit **vor** `onConfigureFailed`. Weder das `try/catch` um
 * `preview.prepareCamera(...)` noch `onCameraError` sehen den Wurf — er tötet
 * den Looper (Sentry: fatal, 16+ Events). Die Engine prüft deshalb **vor** dem
 * Aufruf, ob es die Kamera überhaupt gibt ([StreamingEngine.cameraAvailability]).
 *
 * Was hier verifiziert wird, ist die Verdrahtung: ohne Kamera wird die
 * Library nicht angefasst (kein Wurf möglich), der Stream endet in einem
 * lokalisierten Fehlerzustand statt in einem Crash, und der Normalfall bleibt
 * unverändert.
 */
class StreamingEngineCameraGuardTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine

    /** Zählt die Aufbauten der Idle-Preview — darf ohne Kamera 0 bleiben. */
    private var previewFactories = 0

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.WINDOW_SERVICE) } returns
            mockk<android.view.WindowManager>(relaxed = true)
        every { context.filesDir } returns tempDir
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
        streamingEngine.idlePreviewFactory = {
            previewFactories += 1
            mockk<Camera2ApiManager>(relaxed = true)
        }
        // A1: der Kamera-Open laeuft im Produktionspfad im Hintergrund; hier
        // inline, damit die Zaehler im selben Aufruf stimmen.
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
    }

    @Test
    fun `idle preview is skipped when the device has no camera for the facing`() {
        streamingEngine.initializeCamera()
        streamingEngine.cameraAvailability = { false }

        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)

        assertEquals(
            0,
            previewFactories,
            "ohne Kamera darf prepareCamera nie erreicht werden (der Wurf ist nicht abfangbar)",
        )
    }

    @Test
    fun `preview rebuild checks the selected idle lens before replacing capture`() {
        val idle = mockk<Camera2ApiManager>(relaxed = true)
        every { idle.getCurrentCameraId() } returns "selected-lens"
        streamingEngine.initializeCamera()
        streamingEngine.cameraAvailability = { true }
        streamingEngine.idlePreviewFactory = { idle }
        val surface = mockk<Surface>(relaxed = true)
        streamingEngine.attachPreview(surface, 1280, 720)

        val checkedIds = mutableListOf<String?>()
        streamingEngine.cameraAvailability = { id ->
            checkedIds += id
            false
        }
        streamingEngine.attachPreview(surface, 720, 1280)

        assertEquals(listOf("selected-lens"), checkedIds)
        verify(exactly = 1) { idle.prepareCamera(any(), any(), any(), any()) }
        verify(exactly = 0) { idle.closeCamera() }
    }

    @Test
    fun `deferred preview open retains the lens selected before rebuilding`() {
        val initial = mockk<Camera2ApiManager>(relaxed = true)
        every { initial.getCurrentCameraId() } returns "selected-lens"
        streamingEngine.initializeCamera()
        streamingEngine.cameraAvailability = { true }
        streamingEngine.idlePreviewFactory = { initial }
        val surface = mockk<Surface>(relaxed = true)
        streamingEngine.attachPreview(surface, 1280, 720)

        val replacement = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { replacement }
        val pending = mutableListOf<() -> Unit>()
        streamingEngine.launchCameraOpen = { pending += it }
        streamingEngine.attachPreview(surface, 720, 1280)
        every { camera.currentCameraId } returns "different-lens"

        verify(exactly = 0) { replacement.openCameraId(any()) }
        pending.single()()
        verify(exactly = 1) { replacement.openCameraId("selected-lens") }
    }

    @Test
    fun `stream start reports a localized camera error instead of throwing inside the library`() {
        streamingEngine.initializeCamera()
        streamingEngine.cameraAvailability = { false }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        val state = streamingEngine.streamingState.value
        assertTrue(state is StreamingState.Failed, "der Start endet als Fehler, nicht als Preparing")
        assertEquals(
            R.string.streaming_error_camera_unavailable,
            (state as StreamingState.Failed).reasonRes,
        )
        verify(exactly = 0) { camera.prepareVideo() }
        verify(exactly = 0) { camera.startStream(any(), any(), any()) }
    }

    @Test
    fun `an available camera keeps the normal preparation path`() {
        streamingEngine.initializeCamera()
        streamingEngine.cameraAvailability = { true }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        verify(exactly = 1) { camera.prepareVideo() }
        assertFalse(streamingEngine.streamingState.value is StreamingState.Failed)
    }
}
