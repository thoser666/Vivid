package com.vivid.feature.streaming

import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedro.encoder.BaseEncoder
import com.pedro.encoder.Frame
import com.pedro.encoder.utils.CodecUtil
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.vivid.core.data.AdaptiveBitrateController
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic hardware input; no network, camera, microphone, or user recordings. */
@RunWith(AndroidJUnit4::class)
class EncoderBitrateInstrumentedTest {
    @Test
    fun selectedHardwareEncoderReportsModeAndAcceptsAdaptiveTargetsInBitsPerSecond() {
        val frames = AtomicInteger()
        val encoder = VideoEncoder(object : GetVideoData {
            override fun onVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) = Unit
            override fun onVideoFormat(mediaFormat: MediaFormat) = Unit
            override fun getVideoData(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) frames.incrementAndGet()
            }
        })
        encoder.forceCodecType(CodecUtil.CodecType.HARDWARE)
        try {
            assertTrue(encoder.prepareVideoEncoder(1280, 720, 30, 6_000_000, 0, 2, FormatVideoEncoder.YUV420Dynamical))
            val codec = BaseEncoder::class.java.getDeclaredField("codec").apply { isAccessible = true }.get(encoder) as MediaCodec
            Log.i("VividBitrateTest", "input=${codec.inputFormat} output=${codec.outputFormat}")
            val diagnostic = RootEncoderBitrateDiagnostics.read(codec, encoder.type, encoder.bitRate / 1_000)
            Log.i("VividBitrateTest", "configured=$diagnostic format=${codec.outputFormat}")
            assertTrue("Actual mode unavailable: $diagnostic", diagnostic.mode != EncoderBitrateMode.UNKNOWN)
            if (diagnostic.cbrSupported) assertEquals(EncoderBitrateMode.CBR, diagnostic.mode)
            else assertEquals(EncoderBitrateMode.VBR, diagnostic.mode)
            encoder.start()
            val controller = AdaptiveBitrateController().apply { reset(6_000) }
            repeat(2) { assertNull(controller.onSample(2_000_000L / 1_000)) }
            val reduced = requireNotNull(controller.onSample(2_000_000L / 1_000))
            assertEquals(4_200, reduced)
            encoder.setVideoBitrateOnFly(reduced * 1_000)
            assertEquals(4_200_000, encoder.bitRate)
            feedFrames(encoder)
            repeat(9) { assertNull(controller.onSample(4_200_000L / 1_000)) }
            val recovered = requireNotNull(controller.onSample(4_200_000L / 1_000))
            assertEquals(4_700, recovered)
            encoder.setVideoBitrateOnFly(recovered * 1_000)
            assertEquals(4_700_000, encoder.bitRate)
            val before = frames.get()
            feedFrames(encoder)
            assertTrue("Encoder stopped producing output after bitrate update", frames.get() > before)
            Log.i("VividBitrateTest", "adaptive targets=4200000->4700000 bits/s frames=${frames.get()}")
        } finally {
            encoder.stop()
        }
    }

    private fun feedFrames(encoder: VideoEncoder) {
        val pixels = ByteArray(1280 * 720 * 3 / 2)
        pixels.fill(128.toByte(), 1280 * 720)
        repeat(30) {
            encoder.inputYUVData(Frame(pixels, 0, false, ImageFormat.NV21, SystemClock.elapsedRealtimeNanos() / 1_000))
            Thread.sleep(34)
        }
        Thread.sleep(200)
    }
}
