package com.vivid.feature.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Log
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
import io.mockk.slot
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import timber.log.Timber

/**
 * `#350` (VIVID-46): die Idle-Vorschau fordert 30 fps und kann deshalb keine
 * `CameraConstrainedHighSpeedCaptureSession` erzeugen
 * (`docs/sentry-issues.md` §5.15).
 *
 * Der Befund ist eine Aussage über den Pfad, nicht über ein Gerät: RootEncoder
 * 2.7.5 übergibt in `Camera2ApiManager.createCaptureSession` ausschließlich
 * `SessionConfiguration.SESSION_REGULAR`. Die High-Speed-Session, die im
 * Sentry-Stack von `7812865315` auftaucht, gehört damit nicht zu dieser
 * Anforderung. Der Test hält beides fest — die exakten Parameter und die
 * Breadcrumb-Zeile, die den nächsten Rücklauf lesbar macht — damit die
 * High-Speed-Lesart nicht unbemerkt zurückkehrt.
 */
class IdlePreviewRequestTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var gl: GlStreamInterface
    private lateinit var streamingEngine: StreamingEngine
    private val logs = mutableListOf<Pair<Int, String>>()

    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs += priority to message
        }
    }

    @BeforeEach
    fun setUp() {
        Timber.plant(tree)
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.WINDOW_SERVICE) } returns
            mockk<android.view.WindowManager>(relaxed = true)
        every { context.filesDir } returns tempDir
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns
            PackageManager.PERMISSION_GRANTED
        camera = mockk(relaxed = true)
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        every { camera.currentCameraId } returns ""
        gl = mockk(relaxed = true)
        every { camera.glInterface } returns gl

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
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(tree)
        logs.clear()
    }

    @Test
    fun `the idle preview asks for 720p at 30 fps and never for a high-speed rate`() {
        val preview = mockk<Camera2ApiManager>(relaxed = true)
        val requests = mutableListOf<Triple<Int, Int, Int>>()
        every { preview.prepareCamera(any<SurfaceTexture>(), any(), any(), any()) } answers {
            requests += Triple(arg(1), arg(2), arg(3))
        }
        val forceRender = mutableListOf<Pair<Boolean, Int>>()
        every { gl.setForceRender(any(), any()) } answers { forceRender += arg<Boolean>(0) to arg<Int>(1) }
        streamingEngine.idlePreviewFactory = { preview }

        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)

        assertEquals(
            listOf(Triple(1280, 720, 30)),
            requests,
            "genau eine Anforderung: 720p mit 30 fps",
        )
        // Eine Anforderung über 30 fps wäre die einzige Variante, die den
        // High-Speed-Verdacht aus #350 überhaupt tragen könnte.
        assertTrue(requests.all { it.third <= 30 }, "kein High-Speed-Satz am Preview-Pfad")
        assertEquals(listOf(false to 30), forceRender, "auch das GL-Rendern bleibt bei 30 fps")
    }

    @Test
    fun `a configure failure names the request it failed on`() {
        val preview = mockk<Camera2ApiManager>(relaxed = true)
        every { preview.getCurrentCameraId() } returns "0"
        val callbacks = slot<CameraCallbacks>()
        every { preview.setCameraCallbacks(capture(callbacks)) } returns Unit
        streamingEngine.idlePreviewFactory = { preview }
        streamingEngine.attachPreview(mockk<Surface>(relaxed = true), 1280, 720)
        logs.clear()

        callbacks.captured.onCameraError("Configuration failed")

        val warnings = logs.filter { it.first == Log.WARN }.map { it.second }
        assertTrue(
            warnings.any {
                it.contains("path=idlePreview") && it.contains("request=1280x720@30fps")
            },
            "der Breadcrumb nennt Pfad und Anforderung, tatsächlich: $warnings",
        )
    }
}
