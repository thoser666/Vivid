package com.vivid.feature.streaming

import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedro.encoder.Frame
import com.pedro.encoder.utils.CodecUtil
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.vivid.core.data.EncoderPreset
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware-only, synthetic NV21 input: no camera, microphone, network, or user recordings. */
@RunWith(AndroidJUnit4::class)
class KeyframeIntervalInstrumentedTest {
    @Test
    fun selectedThirtyFpsProducesIdrEveryTwoSeconds() = verifyIdrCadence(inputFps = 30, enableFallback = false)

    @Test
    fun timeFallbackKeepsIdrNearTwoSecondsWhenInputDropsToFourteenFps() =
        verifyIdrCadence(inputFps = 14, enableFallback = true)

    private fun verifyIdrCadence(inputFps: Int, enableFallback: Boolean) {
        val preset = EncoderPreset.FHD30
        val idrTimesUs = Collections.synchronizedList(mutableListOf<Long>())
        val requests = AtomicInteger()
        val nonIdrSyncFrames = AtomicInteger()
        val controller = KeyframeIntervalController()
        lateinit var encoder: VideoEncoder
        encoder = VideoEncoder(object : GetVideoData {
            override fun onVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) = Unit
            override fun onVideoFormat(mediaFormat: MediaFormat) = Unit
            override fun getVideoData(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                if (info.size <= 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
                val keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                if (keyframe) {
                    if (hasH264Idr(buffer)) idrTimesUs.add(info.presentationTimeUs) else nonIdrSyncFrames.incrementAndGet()
                }
                if (enableFallback && controller.onFrame(info.presentationTimeUs, keyframe, SystemClock.elapsedRealtime())) {
                    requests.incrementAndGet()
                    encoder.requestKeyframe()
                }
            }
        })
        encoder.forceCodecType(CodecUtil.CodecType.HARDWARE)
        try {
            assertTrue("Hardware encoder preparation failed", encoder.prepareVideoEncoder(
                preset.width, preset.height, preset.fps, preset.videoBitrateKbps * 1_000, 0,
                KeyframeIntervalController.INTERVAL_SECONDS, FormatVideoEncoder.YUV420Dynamical,
            ))
            encoder.start()
            // Reuse an immutable dark NV21 frame; present it at the actual input rate.
            val pixels = ByteArray(preset.width * preset.height * 3 / 2)
            pixels.fill(128.toByte(), preset.width * preset.height)
            val startMs = SystemClock.elapsedRealtime()
            for (frame in 0 until inputFps * 8) {
                val dueMs = startMs + frame * 1_000L / inputFps
                val waitMs = dueMs - SystemClock.elapsedRealtime()
                if (waitMs > 0) Thread.sleep(waitMs)
                encoder.inputYUVData(Frame(pixels, 0, false, ImageFormat.NV21, SystemClock.elapsedRealtimeNanos() / 1_000))
            }
            Thread.sleep(300)
        } finally {
            encoder.stop()
        }
        val times = synchronized(idrTimesUs) { idrTimesUs.toList() }
        val gapsMs = times.zipWithNext { previous, current -> (current - previous) / 1_000 }
        Log.i("VividKeyframeTest", "inputFps=$inputFps configuredFps=${preset.fps} IDR gapsMs=$gapsMs requests=${requests.get()}")
        assertTrue("Sync frames were not H.264 IDR", nonIdrSyncFrames.get() == 0)
        assertTrue("Too few IDR frames: $times", gapsMs.size >= 2)
        assertTrue("IDR gaps outside 1.7–2.5 seconds: $gapsMs", gapsMs.all { it in 1_700L..2_500L })
    }

    private fun hasH264Idr(buffer: ByteBuffer): Boolean {
        val bytes = buffer.duplicate()
        for (i in bytes.position() until bytes.limit() - 3) {
            if (bytes.get(i).toInt() == 0 && bytes.get(i + 1).toInt() == 0 &&
                bytes.get(i + 2).toInt() == 1 && bytes.get(i + 3).toInt() and 31 == 5
            ) return true
        }
        return false
    }
}
