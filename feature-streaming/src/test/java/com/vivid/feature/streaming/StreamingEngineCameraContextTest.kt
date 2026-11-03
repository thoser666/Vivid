package com.vivid.feature.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraCallbacks
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import timber.log.Timber

/**
 * Pfad-Angabe und Umgebungs-Kontext an Kamera-Fehlern
 * (`docs/sentry-issues.md` §5.12).
 *
 * Vorher loggte nur der Idle-Preview-Pfad (`"Idle camera: %s"`), der Live-Pfad
 * warf seinen Fehler still weg. Dadurch war ein Event wie
 * `Open camera 5 failed` keinem der beiden sehr unterschiedlichen Pfade
 * zuzuordnen. Geprüft wird hier die Verdrahtung, nicht die Formatierung
 * (die steht in [CameraErrorDiagnosticsTest]).
 */
class StreamingEngineCameraContextTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine

    private val logs = mutableListOf<Pair<Int, String>>()

    private val recordingTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs += priority to message
        }
    }

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
        every { camera.currentCameraId } returns "5"
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
        streamingEngine.displayRotationDegrees = { 0 }
        streamingEngine.cameraAvailability = { true }
        // A1: Kamera-Open laeuft im Produktionspfad im Hintergrund — hier inline.
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
        streamingEngine.cameraEnvironment = {
            CameraEnvironment(
                availableIds = listOf("0", "1", "5"),
                concurrentGroups = listOf(listOf("0", "1")),
            )
        }

        Timber.plant(recordingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(recordingTree)
    }

    private fun errors() = logs.filter { it.first >= android.util.Log.ERROR }.map { it.second }

    private fun warnings() = logs.filter { it.first == android.util.Log.WARN }.map { it.second }

    @Test
    fun `the live camera error names its path and the camera environment`() {
        var callbacks: CameraCallbacks? = null
        every { camera.setCameraCallbacks(any()) } answers { callbacks = firstArg() }
        streamingEngine.initializeCamera()
        logs.clear()

        callbacks!!.onCameraError("Open camera 5 failed")

        assertTrue(
            warnings().any {
                it == "Camera error context: path=stream cameraId=5 available=[0,1,5] " +
                    "concurrent=[0,1] streaming=false recording=false"
            },
            "die Umgebung steht als Breadcrumb-Zeile bereit, tatsächlich: ${warnings()}",
        )
        assertTrue(
            errors().contains("Camera error (stream): Open camera 5 failed"),
            "die Fehlerzeile nennt den Pfad, ohne die Id zu verschlucken: ${errors()}",
        )
    }

    @Test
    fun `the idle preview error keeps its message and adds the path`() {
        var previewCallbacks: CameraCallbacks? = null
        val preview = mockk<Camera2ApiManager>(relaxed = true)
        every { preview.getCurrentCameraId() } returns "0"
        every { preview.setCameraCallbacks(any()) } answers { previewCallbacks = firstArg() }
        streamingEngine.idlePreviewFactory = { preview }
        streamingEngine.initializeCamera()

        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
        logs.clear()

        previewCallbacks!!.onCameraError("Open camera 0 failed")

        assertTrue(
            warnings().any { it.contains("path=idlePreview") && it.contains("cameraId=0") },
            "auch der Vorschau-Pfad trägt den Kontext: ${warnings()}",
        )
        assertTrue(
            errors().contains("Idle camera: Open camera 0 failed"),
            "die bestehende Gruppierung bleibt unverändert: ${errors()}",
        )
    }
}
