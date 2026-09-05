package com.vivid.feature.streaming

import android.content.Context
import com.pedro.common.ConnectChecker
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Deckt die Filter-/Effekt-API der [StreamingEngine] ab: Idle-Guards (vor
 * `initializeCamera` bzw. ohne GL-Interface) und die camera-attached Pfade
 * mit gemocktem [GlStreamInterface].
 *
 * JVM-Realität: RootEncoder-Render-Klassen werfen im Unit-Test (GL/Bitmap
 * nicht gemockt), die Fabriken fangen das und liefern null — die Engine
 * verdrahtet dann korrekt `clearFilters()`. Genau dieses Verhalten wird
 * hier verifiziert (App-Verhalten auf dem Gerät: echte Renders via setFilter).
 * Die Controller-Logik selbst decken LowLightBoostControllerTest /
 * VideoFilterControllerTest / LutControllerTest ab.
 */
class StreamingEngineFiltersTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var cameraFactory: CameraFactory
    private lateinit var displayFactory: DisplayFactory
    private lateinit var playerFactory: PlayerFactory
    private lateinit var streamingEngine: StreamingEngine

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        // ReplayStorage leitet aus context.filesDir ab — im JVM-Test ein echtes
        // Temp-Verzeichnis, damit kein Mock-File mit null-path durchs API läuft.
        every { context.filesDir } returns tempDir
        camera = mockk(relaxed = true)
        glStreamInterface = mockk(relaxed = true)
        every { camera.glInterface } returns glStreamInterface
        cameraFactory = object : CameraFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 = camera
        }
        displayFactory = object : DisplayFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiDisplay = mockk(relaxed = true)
        }
        playerFactory = object : PlayerFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiFromFile = mockk(relaxed = true)
        }
        streamingEngine = StreamingEngine(
            context,
            cameraFactory,
            displayFactory,
            playerFactory,
            VideoSourceRegistry(),
        )
    }

    // ------------------------------------------------------------------
    // Idle-Guards: ohne Kamera/GL bleibt alles No-Op (kein Crash).
    // ------------------------------------------------------------------

    @Test
    fun `setVideoFilter returns false before the camera is initialized`() {
        assertFalse(streamingEngine.setVideoFilter(VideoFilter.GRAYSCALE))
        assertEquals(VideoFilter.NONE, streamingEngine.activeFilter.value)
    }

    @Test
    fun `nextVideoFilter without a camera keeps NONE`() {
        assertEquals(VideoFilter.NONE, streamingEngine.nextVideoFilter())
    }

    @Test
    fun `toggleLowLightBoost returns false before the camera is initialized`() {
        assertFalse(streamingEngine.toggleLowLightBoost())
        assertFalse(streamingEngine.lowLightBoostEnabled.value)
    }

    @Test
    fun `setLutPreset returns false before the camera is initialized`() {
        assertFalse(streamingEngine.setLutPreset(LutPreset.WARM))
        assertEquals(LutPreset.NONE, streamingEngine.activeLutPreset.value)
    }

    @Test
    fun `setColorSpace returns false before the camera is initialized`() {
        assertFalse(streamingEngine.setColorSpace(ColorSpace.DISPLAY_P3))
        assertEquals(ColorSpace.SRGB, streamingEngine.activeColorSpace.value)
    }

    @Test
    fun `selectLens and getAvailableLenses are safe without a camera`() {
        assertFalse(streamingEngine.selectLens("0"))
        assertTrue(streamingEngine.getAvailableLenses().isEmpty())
    }

    @Test
    fun `manual focus is a no-op without a camera`() {
        // Darf nicht werfen (safe-call in der Engine).
        streamingEngine.setManualFocusDistance(2.5f)
    }

    @Test
    fun `capability probes are false without a camera`() {
        assertFalse(streamingEngine.hasIsoControl())
        assertFalse(streamingEngine.hasEvControl())
    }

    @Test
    fun `replay controls are safe without a camera`() = runTest {
        // startReplay braucht die Kamera (ReplayRecorder), darf nicht werfen.
        assertFalse(streamingEngine.startReplay(0L))
        assertNull(streamingEngine.stopReplay())
        streamingEngine.pruneReplays()
        assertEquals(ReplayState.Idle, streamingEngine.replayState.first())
    }

    @Test
    fun `reset helpers are no-ops without a camera`() {
        streamingEngine.resetVideoFilter()
        streamingEngine.resetLowLightBoost()
        streamingEngine.resetLut()
        assertEquals(VideoFilter.NONE, streamingEngine.activeFilter.value)
    }

    // ------------------------------------------------------------------
    // Camera-attached Pfade: Engine verdrahtet Controller -> GL-Interface.
    // JVM: Render-Fabrik liefert null -> Engine ruft clearFilters() auf.
    // ------------------------------------------------------------------

    @Test
    fun `setVideoFilter updates state and wires the null render to clearFilters`() = runTest {
        streamingEngine.initializeCamera()

        assertTrue(streamingEngine.setVideoFilter(VideoFilter.GRAYSCALE))
        assertEquals(VideoFilter.GRAYSCALE, streamingEngine.activeFilter.value)
        verify(exactly = 1) { glStreamInterface.clearFilters() }
        verify(exactly = 0) { glStreamInterface.setFilter(any()) }
    }

    @Test
    fun `setVideoFilter NONE clears the filters`() = runTest {
        streamingEngine.initializeCamera()

        assertTrue(streamingEngine.setVideoFilter(VideoFilter.NONE))
        assertEquals(VideoFilter.NONE, streamingEngine.activeFilter.value)
        verify(exactly = 1) { glStreamInterface.clearFilters() }
    }

    @Test
    fun `nextVideoFilter cycles through filters and clears on the gl interface`() = runTest {
        streamingEngine.initializeCamera()

        val first = streamingEngine.nextVideoFilter()
        val second = streamingEngine.nextVideoFilter()
        assertTrue(first != VideoFilter.NONE)
        assertTrue(second != first)
        assertEquals(second, streamingEngine.activeFilter.value)
        verify(exactly = 2) { glStreamInterface.clearFilters() }
    }

    @Test
    fun `toggleLowLightBoost toggles state and wires clearFilters`() = runTest {
        streamingEngine.initializeCamera()

        assertTrue(streamingEngine.toggleLowLightBoost())
        assertTrue(streamingEngine.lowLightBoostEnabled.value)

        assertFalse(streamingEngine.toggleLowLightBoost())
        assertFalse(streamingEngine.lowLightBoostEnabled.value)

        // Beide Toggles: Render null (JVM) -> jeweils clearFilters.
        verify(exactly = 2) { glStreamInterface.clearFilters() }
    }

    @Test
    fun `setLutPreset selects the preset and wires clearFilters`() = runTest {
        streamingEngine.initializeCamera()

        // setPreset liefert true, sobald sich der Preset ändert — unabhängig
        // davon, ob die Render-Erzeugung im JVM-Test gelang (hier: null).
        assertTrue(streamingEngine.setLutPreset(LutPreset.WARM))
        assertEquals(LutPreset.WARM, streamingEngine.activeLutPreset.value)
        verify(exactly = 1) { glStreamInterface.clearFilters() }

        // Gleicher Preset -> false, keine weitere GL-Aktion.
        assertFalse(streamingEngine.setLutPreset(LutPreset.WARM))
        verify(exactly = 1) { glStreamInterface.clearFilters() }
    }

    @Test
    fun `setColorSpace updates the state without a filter crash`() = runTest {
        streamingEngine.initializeCamera()

        streamingEngine.setColorSpace(ColorSpace.APPLE_LOG)
        assertEquals(ColorSpace.APPLE_LOG, streamingEngine.activeColorSpace.value)
    }

    @Test
    fun `resetLut returns the preset to NONE`() = runTest {
        streamingEngine.initializeCamera()

        streamingEngine.resetLut()
        assertEquals(LutPreset.NONE, streamingEngine.activeLutPreset.value)
    }

    @Test
    fun `startReplay records via the camera and stop returns to idle`() = runTest {
        streamingEngine.initializeCamera()

        // RootEncoderReplayRecorder.start -> camera.startRecord (relaxed mock = true)
        assertTrue(streamingEngine.startReplay(1_700_000_000_000L))
        verify(exactly = 1) { camera.startRecord(any<String>()) }
        assertTrue(streamingEngine.replayState.first() is ReplayState.Recording)

        // stop: Datei existiert nicht wirklich -> Rückgabe null, State Idle.
        assertNull(streamingEngine.stopReplay())
        verify(exactly = 1) { camera.stopRecord() }
        assertEquals(ReplayState.Idle, streamingEngine.replayState.first())
    }
}
