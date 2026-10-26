package com.vivid.irlbroadcaster

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.pedro.library.base.Camera2Base
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: capture the actual preview Surface, without UI or saved stream destinations. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class CameraOrientationInstrumentedTest {
    @Test fun idleToStreamPreservesCameraGeometry() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runCameraOrientation") == "true")
        val url = requireNotNull(args.getString("localRtmpUrl"))
        require(url.startsWith("rtmp://127.0.0.1:"))
        val modes = (args.getString("cameraModes") ?: "EXPERIMENTAL_60_FPS,STANDARD")
            .split(',').map(CameraMode::valueOf)
        val orientations = listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: StreamingEngine
            var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            scenario.onActivity {
                engine = ViewModelProvider(it)[StreamingViewModel::class.java].streamingEngine
                previousOrientation = it.requestedOrientation
            }
            try {
                for (mode in modes) for (orientation in orientations) {
                    scenario.onActivity { it.requestedOrientation = orientation }
                    Thread.sleep(1_500)
                    await { engine.getAvailableLenses().isNotEmpty() && engine.canChangeCameraMode() }
                    engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), true)
                    assertTrue(engine.configureCameraMode(mode))
                    snapshot(scenario, engine, "${mode.name}-$orientation-idle")
                    engine.startStream("$url/${mode.name}-$orientation")
                    await { engine.streamingState.value is StreamingState.Streaming && !engine.cameraModeStatus.value.preparing }
                    await { (engine.measuredEncoderFps.value ?: 0) > 0 }
                    Thread.sleep(2_000)
                    assertEquals(mode, engine.cameraModeStatus.value.applied)
                    snapshot(scenario, engine, "${mode.name}-$orientation-stream")
                    engine.stopStream()
                    Thread.sleep(1_000)
                    snapshot(scenario, engine, "${mode.name}-$orientation-return")
                }
            } finally {
                engine.stopStream()
                engine.configureCameraMode(CameraMode.STANDARD)
                scenario.onActivity { it.requestedOrientation = previousOrientation }
            }
        }
    }

    private fun snapshot(scenario: ActivityScenario<MainActivity>, engine: StreamingEngine, label: String) {
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        lateinit var bitmap: Bitmap
        lateinit var output: File
        scenario.onActivity { activity ->
            fun find(view: View): SurfaceView? = if (view is SurfaceView) view else
                (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
            val view = requireNotNull(find(activity.window.decorView))
            bitmap = Bitmap.createBitmap(view.holder.surfaceFrame.width(), view.holder.surfaceFrame.height(), Bitmap.Config.ARGB_8888)
            output = File(activity.filesDir, "orientation-evidence/$label.png").apply { parentFile?.mkdirs() }
            PixelCopy.request(view.holder.surface, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result)
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val field = StreamingEngine::class.java.getDeclaredField("camera").apply { isAccessible = true }
        val gl = (field.get(engine) as Camera2Base).glInterface as GlStreamInterface
        fun value(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
        val main = requireNotNull(value(gl, "mainRender"))
        val camera = requireNotNull(value(main, "cameraRender"))
        val rotation = value(camera, "rotationMatrix") as FloatArray
        Log.i("VividOrientationTest", "$label size=${value(gl, "encoderWidth")}x${value(gl, "encoderHeight")} previewRotation=${value(gl, "previewOrientation")} cameraMatrix=${rotation.joinToString()} file=${output.absolutePath}")
        if (label.startsWith("EXPERIMENTAL_60_FPS") && label.endsWith("-stream")) {
            val expected = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
            if (label.contains("-${ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE}-")) {
                expected[0] = -1f
                expected[5] = -1f
            }
            assertArrayEquals("Decoded input needs the display half-turn before filters, without Camera2's quarter-turn", expected, rotation, 0.001f)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Camera transition timed out", condition())
    }
}
