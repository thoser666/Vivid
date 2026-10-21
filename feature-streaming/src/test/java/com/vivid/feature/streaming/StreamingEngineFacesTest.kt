package com.vivid.feature.streaming

import android.content.Context
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.PrivacyZone
import com.vivid.feature.streaming.source.DisplayFactory
import com.vivid.feature.streaming.source.PlayerFactory
import com.vivid.feature.streaming.source.VideoSourceRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * P2 — Gesichtserkennung in der [StreamingEngine] (Skizze §5/§8).
 *
 * Was hier verifiziert wird, ist die Verdrahtung, nicht die Erkennung selbst:
 * Attach/Detach an den beiden Kamerapfaden, das Gating über Master- und
 * P2-Toggle, die Capability-Meldung und — das Entscheidende — dass
 * Gesichts-Frames **nur die Ellipsen-Uniforms** setzen und die Filterkette
 * damit nicht neu gebaut wird (sonst wären 5–10 fps Erkennung im Stream
 * unsichtbar).
 *
 * Die Geräte-Konvention der Abbildung (Rotation/Mirror, Risiko #2) liegt mit
 * Goldenwerten in FaceEllipseMapperTest; die Erkennung selbst ist
 * RootEncoder-Hardware (Route A) und wird auf dem Gerät geprüft.
 */
class StreamingEngineFacesTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var streamingEngine: StreamingEngine

    /** Das an den Stream-Encoder angehängte Ziel. */
    private lateinit var cameraTarget: FakeFaceDetectable

    /** Das an die Idle-Preview angehängte Ziel. */
    private lateinit var idleTarget: FakeFaceDetectable

    private class FakeFaceDetectable : FaceDetectable {
        var enableCalls = 0
        var disableCalls = 0
        var callback: ((FaceFrame) -> Unit)? = null

        override fun enableFaceDetection(onFaces: (FaceFrame) -> Unit): Boolean {
            enableCalls += 1
            callback = onFaces
            return true
        }

        override fun disableFaceDetection() {
            disableCalls += 1
        }

        fun emit(vararg faces: DetectedFace) {
            val sink = checkNotNull(callback) { "Erkennung ist nicht eingeschaltet" }
            sink(
                FaceFrame(
                    faces = faces.toList(),
                    meta = FaceFrameMeta(4000, 3000, 90, 0, false),
                ),
            )
        }
    }

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.WINDOW_SERVICE) } returns mockk<android.view.WindowManager>(relaxed = true)
        every { context.filesDir } returns tempDir
        camera = mockk(relaxed = true)
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        glStreamInterface = mockk(relaxed = true)
        every { camera.glInterface } returns glStreamInterface

        cameraTarget = FakeFaceDetectable()
        idleTarget = FakeFaceDetectable()

        streamingEngine = StreamingEngine(
            context,
            object : CameraFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 = camera
            },
            object : DisplayFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiDisplay = mockk(relaxed = true)
            },
            object : PlayerFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiFromFile = mockk(relaxed = true)
            },
            VideoSourceRegistry(),
        )
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.cameraFaceDetectableFactory = { cameraTarget }
        streamingEngine.idleFaceDetectableFactory = { idleTarget }
        streamingEngine.displayRotationDegrees = { 0 }
    }

    private fun goldenFace() = DetectedFace(1000, 500, 2000, 1500, 80)

    private fun assertEllipse(
        expected: PrivacyEllipse,
        actual: PrivacyEllipse,
        delta: Float = 1e-4f,
        label: String = "ellipse",
    ) {
        assertEquals(expected.centerX, actual.centerX, delta, "$label centerX")
        assertEquals(expected.centerY, actual.centerY, delta, "$label centerY")
        assertEquals(expected.radiusX, actual.radiusX, delta, "$label radiusX")
        assertEquals(expected.radiusY, actual.radiusY, delta, "$label radiusY")
    }

    // ------------------------------------------------------------------
    // Stream-Pfad: Erkennung am Encoder, Frames ohne Ketten-Rebuild.
    // ------------------------------------------------------------------

    @Test
    fun `face frames publish ellipses without rebuilding the chain`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFaceBlurEnabled(true))

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(1, cameraTarget.enableCalls, "der Stream haengt die Erkennung an")
        assertNotNull(cameraTarget.callback)
        assertEquals(true, streamingEngine.faceDetectionAvailable.value)

        // Ab hier zaehlt nur, was die Frames selbst anrichten: die bisherigen
        // Aufrufe der Kette loeschen (stubs bleiben erhalten, relaxed reicht).
        clearMocks(glStreamInterface)

        // Hysterese: der erste Frame zaehlt noch nicht, der zweite schon.
        cameraTarget.emit(goldenFace())
        assertTrue(
            streamingEngine.publishedPrivacyEllipses.value.isEmpty(),
            "ein einzelner Frame darf noch keinen Blur setzen",
        )

        cameraTarget.emit(goldenFace())
        val published = streamingEngine.publishedPrivacyEllipses.value
        assertEquals(1, published.size)
        assertEllipse(
            PrivacyEllipse(0.666667f, 0.625f, 0.166667f, 0.125f),
            published.single(),
            label = "golden face",
        )

        // Der entscheidende P2-Satz: Uniform-Update statt Filter-Neubau.
        verify(exactly = 0) { glStreamInterface.clearFilters() }
        verify(exactly = 0) { glStreamInterface.setFilter(any()) }
    }

    @Test
    fun `zones keep their slots and faces fill only the free ones`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFaceBlurEnabled(true))
        val zones = listOf(
            PrivacyZone(0.25f, 0.75f, 0.1f, 0.2f),
            PrivacyZone(0.5f, 0.75f, 0.1f, 0.2f),
            PrivacyZone(0.75f, 0.75f, 0.1f, 0.2f),
            PrivacyZone(0.25f, 0.25f, 0.1f, 0.2f),
        )
        streamingEngine.setPrivacyZones(zones)

        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        clearMocks(glStreamInterface)

        val faces = arrayOf(
            DetectedFace(200, 400, 600, 1000, 80),
            DetectedFace(1200, 400, 1600, 1000, 80),
            DetectedFace(2200, 400, 2600, 1000, 80),
            DetectedFace(3200, 400, 3600, 1000, 80),
        )
        cameraTarget.emit(*faces)
        cameraTarget.emit(*faces)

        val published = streamingEngine.publishedPrivacyEllipses.value
        assertEquals(PrivacyBlurFilterRender.MAX_ELLIPSES, published.size, "harte Slot-Grenze")
        zones.forEachIndexed { index, zone ->
            assertEllipse(zone.toPrivacyEllipse(), published[index], label = "zone $index")
        }
        verify(exactly = 0) { glStreamInterface.clearFilters() }
    }

    @Test
    fun `the master toggle gates the detection until it is switched on`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setFaceBlurEnabled(true))

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(0, cameraTarget.enableCalls, "ohne Master-Toggle wird nichts eingeschaltet")
        assertNull(streamingEngine.faceDetectionAvailable.value)

        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertEquals(1, cameraTarget.enableCalls, "der Toggle schaltet am laufenden Ziel ein")
        assertEquals(true, streamingEngine.faceDetectionAvailable.value)

        // Ohne Wunsch bleiben die Frames wirkungslos.
        assertTrue(streamingEngine.setFaceBlurEnabled(false))
        assertTrue(streamingEngine.publishedPrivacyEllipses.value.isEmpty())
    }

    @Test
    fun `stopping the stream detaches the detection`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFaceBlurEnabled(true))
        streamingEngine.startStream(listOf("rtmp://example.com/live"))
        assertEquals(1, cameraTarget.enableCalls)

        every { camera.isStreaming } returns true
        streamingEngine.stopStream()

        assertEquals(1, cameraTarget.disableCalls, "detached wird beim Stopp, solange die Kamera offen ist")
        assertNull(streamingEngine.faceDetectionAvailable.value, "Capability ist danach wieder unbekannt")
    }

    // ------------------------------------------------------------------
    // Idle-Preview-Pfad: gleicher Producer, anderes Ziel.
    // ------------------------------------------------------------------

    @Test
    fun `the idle preview attaches and detaches its own target`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFaceBlurEnabled(true))
        val previewCamera = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { previewCamera }
        val surface = mockk<Surface>(relaxed = true)

        streamingEngine.attachPreview(surface, 1280, 720)

        assertEquals(1, idleTarget.enableCalls, "die Idle-Preview schaltet die Erkennung ein")
        assertEquals(true, streamingEngine.faceDetectionAvailable.value)

        streamingEngine.detachPreview(surface)

        assertEquals(1, idleTarget.disableCalls)
        assertNull(streamingEngine.faceDetectionAvailable.value)
    }

    @Test
    fun `an unused face toggle never touches the detection`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))

        streamingEngine.startStream(listOf("rtmp://example.com/live"))

        assertEquals(0, cameraTarget.enableCalls)
        assertNull(streamingEngine.faceDetectionAvailable.value)
        assertTrue(streamingEngine.publishedPrivacyEllipses.value.isEmpty())
    }

    @Test
    fun `multi target streams share one detection`() {
        streamingEngine.initializeCamera()
        assertTrue(streamingEngine.setPrivacyEnabled(true))
        assertTrue(streamingEngine.setFaceBlurEnabled(true))

        streamingEngine.startStream(
            listOf("rtmp://example.com/live", "rtmp://example.com/live2"),
        )

        assertEquals(1, cameraTarget.enableCalls, "ein Ziel-Wechsel haengt keine zweite Erkennung an")
        verify(atLeast = 2) { camera.startStream(MultiType.RTMP, any(), any()) }
    }
}
