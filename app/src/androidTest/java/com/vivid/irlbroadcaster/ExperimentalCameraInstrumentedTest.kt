package com.vivid.irlbroadcaster

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.library.base.Camera2Base
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.VideoFilter
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CAMERA_MODE_LOG_TAG = "VividCameraModeTest"

/** Opt-in physical-device evidence. Uses an explicit loopback endpoint, never saved destinations. */
@RunWith(AndroidJUnit4::class)
class ExperimentalCameraInstrumentedTest {

    // The opt-in capture path needs the same runtime grants the gate used (#249).
    @get:Rule
    val permissions = EmulatorTestPermissions.rule()

    @Test
    fun recordingAndLocalRtmpKeepGlEffectsAndShareCapture() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runExperimentalCamera") == "true")
        val url = args.getString("localRtmpUrl") ?: ""
        require(url.startsWith("rtmp://127.0.0.1:"))
        val duration = args.getString("captureSeconds")?.toInt() ?: 30
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: StreamingEngine
            scenario.onActivity { engine = ViewModelProvider(it)[StreamingViewModel::class.java].streamingEngine }
            await { engine.getAvailableLenses().isNotEmpty() }
            val previousFilter = engine.activeFilter.value
            var output: File? = null
            try {
                engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), true)
                assertTrue(engine.configureCameraMode(CameraMode.EXPERIMENTAL_60_FPS))
                assertTrue(engine.supportsExperimentalCamera(EncoderPreset.FHD30, VideoCodecPreference.H264))
                assertTrue(engine.setVideoFilter(VideoFilter.GRAYSCALE))
                assertTrue(engine.startReplay(includeAudio = true))
                await { !engine.cameraModeStatus.value.preparing }
                assertEquals(CameraMode.EXPERIMENTAL_60_FPS, engine.cameraModeStatus.value.applied)
                assertTrue(engine.replayState.value is ReplayState.Recording)
                output = (engine.replayState.value as ReplayState.Recording).file
                assertFalse(engine.configureCameraMode(CameraMode.STANDARD))
                val cameraField = StreamingEngine::class.java.getDeclaredField("camera").apply { isAccessible = true }
                val camera = cameraField.get(engine) as Camera2Base
                val gl = camera.glInterface as GlStreamInterface
                val overlay = TextObjectFilterRender().apply {
                    setText("Vivid GL 60 FPS", 64f, Color.RED)
                    setDefaultScale(1920, 1080)
                    setPosition(5f, 5f)
                }
                gl.addFilter(overlay)
                // Starting RTMP on an existing recording must retain the capture and its GL chain.
                engine.startStream(url)
                await { engine.streamingState.value is StreamingState.Streaming }
                repeat(duration) {
                    Thread.sleep(1_000)
                    Log.i(CAMERA_MODE_LOG_TAG, "second=$it mode=${engine.cameraModeStatus.value} fps=${engine.measuredEncoderFps.value}")
                    assertEquals(CameraMode.EXPERIMENTAL_60_FPS, engine.cameraModeStatus.value.applied)
                    assertEquals(VideoFilter.GRAYSCALE, engine.activeFilter.value)
                }
                engine.stopStream()
                assertTrue(engine.replayState.value is ReplayState.Recording)
                assertFalse(engine.configureCameraMode(CameraMode.STANDARD))
                Thread.sleep(1_000)
                output = engine.stopReplay()
                assertNotNull(output)
                assertTrue(requireNotNull(output).length() > 0)
                Log.i(CAMERA_MODE_LOG_TAG, "RESULT file=${output?.absolutePath} measured=${engine.measuredEncoderFps.value}")
                assertTrue(engine.configureCameraMode(CameraMode.STANDARD))
                // Standard recording after external capture verifies camera ownership was released.
                assertTrue(engine.startReplay(includeAudio = false))
                Thread.sleep(2_000)
                val standard = engine.stopReplay()
                assertNotNull(standard)
                Log.i(CAMERA_MODE_LOG_TAG, "STANDARD file=${standard?.absolutePath}")
            } finally {
                engine.stopStream()
                engine.stopReplay()
                engine.configureCameraMode(CameraMode.STANDARD)
                engine.setVideoFilter(previousFilter)
                Log.i(CAMERA_MODE_LOG_TAG, "CLEANUP file=${output?.absolutePath}")
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Camera transition timed out", condition())
    }
}
