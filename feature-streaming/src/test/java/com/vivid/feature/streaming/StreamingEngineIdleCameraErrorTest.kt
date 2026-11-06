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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import timber.log.Timber

/**
 * Fehlerpolitik der Idle-Vorschau (`docs/sentry-issues.md` §5.16, #336).
 *
 * RootEncoder 2.7.5 meldet einen gescheiterten Session-Aufbau mit **zwei**
 * Texten: `Configuration failed` (`onConfigureFailed`) und
 * `Create capture session failed: …` (Exception aus `setRepeatingRequest`).
 * Die Engine wiederholte bisher nur den ersten. Der zweite ist die Signatur des
 * Tickets #336 (20 Events, `CAMERA_DISCONNECTED (2)`), also genau der Fall ohne
 * Wiederholung.
 *
 * Geprüft wird die Zuordnung von Fehlertext zu Wiederholung, das Einmal-Budget
 * und die Meldung einer Geräte-Trennung.
 */
class StreamingEngineIdleCameraErrorTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine
    private lateinit var preview: Camera2ApiManager
    private lateinit var secondPreview: Camera2ApiManager

    private var callbacks: CameraCallbacks? = null
    private var secondCallbacks: CameraCallbacks? = null
    private var reopens = 0
    private var built = 0

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
        every { camera.currentCameraId } returns "0"
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
        // A1: Open und Main-Rueckkehr laufen inline, damit die Zaehler synchron sind.
        streamingEngine.launchCameraOpen = { it() }
        streamingEngine.runOnMain = { it() }
        streamingEngine.cameraEnvironment = {
            CameraEnvironment(
                availableIds = listOf("0", "1", "5"),
                concurrentGroups = listOf(listOf("0", "1")),
            )
        }

        preview = mockk(relaxed = true)
        every { preview.getCurrentCameraId() } returns "0"
        every { preview.setCameraCallbacks(any()) } answers { callbacks = firstArg() }
        every { preview.reOpenCamera(any()) } answers { reopens += 1 }

        secondPreview = mockk(relaxed = true)
        every { secondPreview.getCurrentCameraId() } returns "1"
        every { secondPreview.setCameraCallbacks(any()) } answers { secondCallbacks = firstArg() }
        every { secondPreview.reOpenCamera(any()) } answers { reopens += 1 }

        streamingEngine.idlePreviewFactory = {
            built += 1
            if (built == 1) preview else secondPreview
        }
        // Ohne initializeCamera() existiert `camera` nicht und der Vorschau-Aufbau
        // bricht vor der Factory ab (gleiches Vorgehen wie in den Nachbartests).
        streamingEngine.initializeCamera()

        Timber.plant(recordingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(recordingTree)
    }

    private fun errors() = logs.filter { it.first >= android.util.Log.ERROR }.map { it.second }

    private fun warnings() = logs.filter { it.first == android.util.Log.WARN }.map { it.second }

    private fun openPreview() {
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
    }

    @Test
    fun `a session creation failure reopens the camera once`() {
        openPreview()

        callbacks!!.onCameraError(
            "Create capture session failed: CAMERA_DISCONNECTED (2): checkPidStatus:2802: " +
                "The camera device has been closed",
        )

        assertEquals(1, reopens, "der Fehler aus #336 muss die vorhandene Wiederholung auslösen")
        assertTrue(
            errors().contains(
                "Idle camera: Create capture session failed: CAMERA_DISCONNECTED (2): " +
                    "checkPidStatus:2802: The camera device has been closed",
            ),
            "die Fehlerzeile bleibt unverändert: ${errors()}",
        )
    }

    @Test
    fun `a configuration failure still reopens the camera once`() {
        openPreview()

        callbacks!!.onCameraError("Configuration failed")

        assertEquals(1, reopens, "das Bestandsverhalten aus #350 bleibt erhalten")
    }

    @Test
    fun `an open failure is not retried`() {
        openPreview()

        callbacks!!.onCameraError("Open camera 0 failed")

        assertEquals(
            0,
            reopens,
            "ein gescheitertes Öffnen ist keine Session-Wiederholung — sonst würde jeder Fehler einen zweiten Open auslösen",
        )
    }

    @Test
    fun `the reopen budget stays one per preview build`() {
        openPreview()

        callbacks!!.onCameraError("Create capture session failed: CAMERA_DISCONNECTED (2)")
        callbacks!!.onCameraError("Create capture session failed: CAMERA_DISCONNECTED (2)")

        assertEquals(1, reopens, "ein Fehlerfall darf genau einen Wiederaufbau auslösen")
    }

    @Test
    fun `a device disconnect is reported with path and request`() {
        openPreview()
        logs.clear()

        callbacks!!.onCameraDisconnected()

        assertTrue(
            warnings().any {
                it.contains("path=idlePreview") && it.contains("request=1280x720@30fps")
            },
            "die Trennung trägt denselben Kontext wie ein Fehler: ${warnings()}",
        )
        assertTrue(
            errors().contains("Idle camera: disconnected"),
            "ohne diese Zeile bleibt eine Trennung in Sentry unsichtbar: ${errors()}",
        )
    }

    @Test
    fun `a retired preview neither reports nor reopens`() {
        openPreview()
        // Neues Surface → neuer Aufbau, die erste Vorschau ist abgelöst.
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
        assertEquals(2, built, "der zweite Aufbau muss eine neue Vorschau erzeugen")
        logs.clear()
        val reopensBefore = reopens

        callbacks!!.onCameraError("Create capture session failed: CAMERA_DISCONNECTED (2)")
        callbacks!!.onCameraDisconnected()

        assertEquals(reopensBefore, reopens, "eine abgelöste Vorschau darf nichts wieder öffnen")
        assertTrue(
            errors().none { it.contains("Idle camera:") },
            "eine abgelöste Vorschau darf keine Fehlergruppe erzeugen: ${errors()}",
        )
    }

    @Test
    fun `the current preview keeps its callbacks after a rebuild`() {
        openPreview()
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)

        secondCallbacks!!.onCameraError("Create capture session failed: CAMERA_NOT_AVAILABLE")

        assertEquals(1, reopens, "die aktive Vorschau wiederholt weiterhin")
    }
}
