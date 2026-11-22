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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * A1: Der blockierende Kamera-Open läuft nicht auf dem Aufrufer-Thread
 * (`docs/sentry-issues.md` §5.12, VIVID-4T/#374).
 *
 * RootEncoder wartet in `Camera2ApiManager.openCameraId()` nach
 * `cameraManager.openCamera(...)` auf einem Semaphor. Kommt dieser Aufruf vom
 * Main-Thread (Service-Start, UI-Start), ist die App während des Wartens
 * eingefroren — ein Stop/Back bricht `acquireUninterruptibly()` nicht ab. Die
 * Engine schiebt den Open deshalb in einen seriellen Hintergrund-Scope und holt
 * nur den Abschluss zurück auf Main.
 *
 * Geprüft wird genau das und nichts darüber hinaus: der Open läuft auf einem
 * anderen Thread, der Zustand steht sofort auf `Preparing`, und zwei Starts
 * erzeugen keinen zweiten Öffnungsversuch.
 */
class StreamingEngineCameraThreadTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var streamingEngine: StreamingEngine

    /** Arbeit, die der Produktionspfad an den Hintergrund-Scope gibt. */
    private val pendingOpens = mutableListOf<() -> Unit>()

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
        // Der Produktions-Default plant die Vorschau-Wiederherstellung per
        // `Handler.postDelayed`; in der JVM wirft der Stub.
        streamingEngine.scheduleRecoveryPreview = { _, _ -> }
        // Der Abschluss soll inline laufen, der Open selbst bleibt beim
        // Produktions-Default (Hintergrund-Scope) — das ist die Messung.
        streamingEngine.runOnMain = { it() }
        // Für den Wiederholungs-Test: fiele der Rückweg auf den Main-Post
        // zurück, liefe er hier inline auf dem Test-Thread und der Test würde
        // rot — die Mutation ist damit nachweisbar.
        streamingEngine.dispatchSceneTask = { it() }
        streamingEngine.initializeCamera()
    }

    private fun awaitState(timeoutMs: Long = 5_000, predicate: (StreamingState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(streamingEngine.streamingState.value)) return
            Thread.sleep(10)
        }
        throw AssertionError(
            "Zustand nicht erreicht, aktuell: ${streamingEngine.streamingState.value}",
        )
    }

    @Test
    fun `the camera start opens the device off the calling thread`() {
        val caller = Thread.currentThread()
        val openedOn = java.util.concurrent.atomic.AtomicReference<Thread?>(null)
        every { camera.prepareVideo() } answers {
            openedOn.set(Thread.currentThread())
            true
        }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        // `Streaming` folgt erst dem Verbindungs-Callback; hier zählt, dass der
        // Open überhaupt auf einem anderen Thread gelaufen ist.
        awaitState { openedOn.get() != null }

        val observed = openedOn.get()
        assertNotNull(observed, "prepareVideo wurde nie aufgerufen")
        assertNotSame(
            caller,
            observed,
            "der Semaphor-Wartepunkt von RootEncoder gehört nicht auf den Aufrufer-Thread",
        )
    }

    @Test
    fun `the state is preparing before the open runs`() {
        streamingEngine.launchCameraOpen = { pendingOpens += it }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(
            StreamingState.Preparing,
            streamingEngine.streamingState.value,
            "die UI darf nicht auf den Kamera-Open warten",
        )
        assertEquals(1, pendingOpens.size, "genau ein Öffnungsversuch eingeplant")
        verify(exactly = 0) { camera.prepareVideo() }
    }

    @Test
    fun `a second start while the open is pending does not open twice`() {
        streamingEngine.launchCameraOpen = { pendingOpens += it }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(1, pendingOpens.size, "der laufende Start blockiert den zweiten")
        pendingOpens.single()()
        verify(exactly = 1) { camera.prepareVideo() }
    }

    @Test
    fun `the idle preview stays closed while a camera start is pending`() {
        streamingEngine.launchCameraOpen = { pendingOpens += it }
        var previews = 0
        streamingEngine.idlePreviewFactory = {
            previews += 1
            mockk<Camera2ApiManager>(relaxed = true)
        }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)

        assertEquals(
            0,
            previews,
            "während Preparing darf die Vorschau keine zweite Kamera anfordern",
        )
    }

    @Test
    fun `the idle preview retry reopens the camera off the calling thread`() {
        val reopenOn = java.util.concurrent.atomic.AtomicReference<Thread?>(null)
        var callbacks: com.pedro.encoder.input.video.CameraCallbacks? = null
        val preview = mockk<Camera2ApiManager>(relaxed = true)
        every { preview.setCameraCallbacks(any()) } answers { callbacks = firstArg() }
        every { preview.getCurrentCameraId() } returns "0"
        every { preview.reOpenCamera(any()) } answers {
            reopenOn.set(Thread.currentThread())
            Unit
        }
        streamingEngine.idlePreviewFactory = { preview }
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)

        // Der Fehler-Callback kommt vom Camera-Thread; die Wiederholung darf
        // nicht auf den Main-Thread zurück (gleiche Semaphor-Warte).
        callbacks!!.onCameraError("Configuration failed")
        awaitState { reopenOn.get() != null }

        val observed = reopenOn.get()
        assertNotNull(observed, "reOpenCamera wurde nie aufgerufen")
        assertNotSame(
            Thread.currentThread(),
            observed,
            "der Wiederaufbau hat denselben Wartepunkt wie der erste Open",
        )
    }

    @Test
    fun `a completion after the user stopped starts no stream`() {
        streamingEngine.launchCameraOpen = { pendingOpens += it }

        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        streamingEngine.stopStream()
        pendingOpens.single()()

        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
        verify(exactly = 0) { camera.startStream(any(), any(), any()) }
    }
}
