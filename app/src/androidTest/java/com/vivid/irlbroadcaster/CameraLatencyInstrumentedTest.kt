package com.vivid.irlbroadcaster

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.media.MediaCodec
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedro.library.base.Camera2Base
import com.pedro.library.view.GlStreamInterface
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.VideoFilter
import java.io.File
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in capture-to-GL frame age, not display or network latency. Uses only loopback RTMP. */
@RunWith(AndroidJUnit4::class)
class CameraLatencyInstrumentedTest {
    @Test fun measureCaptureToGlAge() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runCameraLatency") == "true")
        val url = requireNotNull(args.getString("localRtmpUrl"))
        require(url.startsWith("rtmp://127.0.0.1:"))
        val duration = (args.getString("latencySeconds") ?: "30").toLong() * 1_000
        require(duration >= 10_000)
        val ageLimit = (args.getString("maxFrameAgeP95Ms") ?: "250").toDouble()
        val effects = args.getString("latencyGlEffects") == "true"
        val stallMs = (args.getString("stallGlMillis") ?: "0").toLong()
        val modes = (args.getString("cameraModes") ?: "STANDARD,EXPERIMENTAL_60_FPS")
            .split(',').map(CameraMode::valueOf)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var engine: StreamingEngine
            scenario.onActivity { engine = ViewModelProvider(it)[StreamingViewModel::class.java].streamingEngine }
            val previousFilter = engine.activeFilter.value
            try {
                for (mode in modes) {
                    startLatencyStream(engine, mode, url, effects)
                    measureFrameAge(engine, context, mode, duration, ageLimit, effects, stallMs)
                    engine.stopStream()
                    Thread.sleep(1_000)
                }
            } finally {
                engine.stopStream()
                engine.configureCameraMode(CameraMode.STANDARD)
                engine.setVideoFilter(previousFilter)
            }
        }
    }

    private fun startLatencyStream(engine: StreamingEngine, mode: CameraMode, url: String, effects: Boolean) {
        await { engine.getAvailableLenses().isNotEmpty() && engine.canChangeCameraMode() }
        engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), true)
        assertTrue(engine.configureCameraMode(mode))
        if (effects) assertTrue(engine.setVideoFilter(VideoFilter.GRAYSCALE))
        engine.startStream("$url-${mode.name}")
        await { engine.streamingState.value is StreamingState.Streaming && !engine.cameraModeStatus.value.preparing }
        assertEquals(mode, engine.cameraModeStatus.value.applied)
    }

    private fun measureFrameAge(
        engine: StreamingEngine, context: android.content.Context, mode: CameraMode,
        duration: Long, ageLimit: Double, effects: Boolean, stallMs: Long,
    ) {
        val camera = field(engine, "camera") as Camera2Base
        val source = context.getSystemService(CameraManager::class.java)
            .getCameraCharacteristics(camera.currentCameraId)
            .get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        assertEquals("Absolute frame age needs a REALTIME sensor clock", CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME, source)
        val gl = camera.glInterface as GlStreamInterface
        if (effects) gl.addFilter(TextObjectFilterRender().apply {
            setText("Vivid GL latency test", 64f, Color.RED)
            setDefaultScale(1920, 1080)
            setPosition(5f, 5f)
        })
        val executor = field(gl, "executor") as ExecutorService
        val queue = field(gl, "threadQueue") as BlockingQueue<*>
        val capture = field(engine, "experimentalCapture")
        val encodedInfo = capture?.let { field(it, "pendingInfo") as MediaCodec.BufferInfo }
        val decodedInfo = capture?.let { field(it, "decodedInfo") as MediaCodec.BufferInfo }
        capture?.let {
            for (name in listOf("encoder", "decoder")) {
                val codec = field(it, name) as MediaCodec
                Log.i("VividLatencyTest", "$name name=${codec.name} format=${codec.outputFormat}")
            }
        }
        val file = File(context.filesDir, "latency-evidence/${mode.name}.csv").apply { parentFile?.mkdirs() }
        val ages = mutableListOf<Double>()
        file.bufferedWriter().use { out ->
            out.write("elapsed_ns,monotonic_ns,texture_ns,frame_age_ms,gl_queue,encoded_fps,source_pts_us,decoded_pts_us\n")
            val start = SystemClock.elapsedRealtime()
            val end = start + duration
            var stalled = false
            while (SystemClock.elapsedRealtime() < end) {
                if (!stalled && stallMs > 0 && SystemClock.elapsedRealtime() - start > 10_000) {
                    stalled = true
                    executor.execute { Thread.sleep(stallMs) }
                }
                val result = executor.submit<LongArray> { longArrayOf(SystemClock.elapsedRealtimeNanos(), System.nanoTime(), gl.surfaceTexture.timestamp) }.get(5, TimeUnit.SECONDS)
                // Camera encoder Surfaces use monotonic/uptime PTS; direct Camera2
                // SurfaceTextures retain the REALTIME sensor clock (Android timebase conversion).
                val age = (result[if (mode == CameraMode.STANDARD) 0 else 1] - result[2]) / 1_000_000.0
                if (SystemClock.elapsedRealtime() - start > 5_000) ages.add(age)
                out.write("${result[0]},${result[1]},${result[2]},$age,${queue.size},${engine.measuredEncoderFps.value},${encodedInfo?.presentationTimeUs},${decodedInfo?.presentationTimeUs}\n")
                Thread.sleep(20)
            }
        }
        assertTrue("No post-warmup age measurements", ages.isNotEmpty())
        val sorted = ages.sorted()
        val p95 = sorted[(sorted.size * .95).toInt()]
        Log.i("VividLatencyTest", "$mode medianMs=${sorted[sorted.size/2]} p95Ms=$p95 maxMs=${sorted.last()} evidence=${file.absolutePath} fps=${engine.measuredEncoderFps.value}")
        assertTrue("Timestamp clock mismatch", sorted.first() >= 0)
        assertTrue("$mode capture-to-GL p95 $p95 ms exceeds $ageLimit ms", p95 < ageLimit)
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!condition() && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
        assertTrue("Camera transition timed out", condition())
    }
}
