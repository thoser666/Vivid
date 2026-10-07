package com.vivid.feature.streaming

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import com.pedro.library.base.recording.RecordController
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test
import com.pedro.library.multiple.MultiCamera2
import com.vivid.feature.streaming.source.VideoSourceRegistry
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyframeMonitoringRecordControllerTest {
    @Test
    fun engineInstallsMonitorAndRequestsOverdueKeyframeOnlyWhileEncoding() {
        val camera = mockk<MultiCamera2>(relaxed = true)
        val installed = slot<RecordController>()
        every { camera.setRecordController(capture(installed)) } just runs
        val context = mockk<Context>(relaxed = true)
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        val factory = mockk<CameraFactory>()
        every { factory.create(any()) } returns camera
        val engine = StreamingEngine(context, factory, mockk(relaxed = true), mockk(relaxed = true), VideoSourceRegistry())
        var nowMs = 0L
        engine.timeSource = { nowMs }
        engine.initializeCamera()
        every { camera.isStreaming } returns true
        val buffer = ByteBuffer.allocate(16)
        fun frame(keyframe: Boolean, timeMs: Long) {
            nowMs = timeMs
            installed.captured.recordVideo(buffer, MediaCodec.BufferInfo().apply {
                set(0, 16, timeMs * 1_000, if (keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            })
        }
        frame(true, 0)
        frame(false, 2_100)
        frame(false, 2_150)
        verify(exactly = 1) { camera.requestKeyFrame() }
        frame(true, 2_200)
        assertEquals(2_200L, engine.measuredKeyframeIntervalMs.value)

        every { camera.isStreaming } returns false
        frame(false, 5_000)
        verify(exactly = 1) { camera.requestKeyFrame() }
    }

    @Test
    fun observesVideoFramesWithoutConsumingOrChangingTheBuffer() {
        val inner = mockk<RecordController>(relaxed = true)
        val seen = mutableListOf<Long>()
        val controller = KeyframeMonitoringRecordController(inner) { seen.add(it.presentationTimeUs) }
        val data = ByteBuffer.allocate(16).apply { position(4); limit(12) }
        val info = MediaCodec.BufferInfo().apply { set(4, 8, 2_000_000, MediaCodec.BUFFER_FLAG_KEY_FRAME) }

        controller.recordVideo(data, info)

        assertEquals(listOf(2_000_000L), seen)
        assertEquals(4, data.position())
        assertEquals(12, data.limit())
        verify(exactly = 1) { inner.recordVideo(data, info) }
    }

    @Test
    fun ignoresConfigurationAndEosButStillForwardsThemToTheRecorder() {
        val inner = mockk<RecordController>(relaxed = true)
        var observed = 0
        val controller = KeyframeMonitoringRecordController(inner) { observed++ }
        val data = ByteBuffer.allocate(16)
        for (flags in listOf(MediaCodec.BUFFER_FLAG_CODEC_CONFIG, MediaCodec.BUFFER_FLAG_END_OF_STREAM)) {
            controller.recordVideo(data, MediaCodec.BufferInfo().apply { set(0, 16, 0, flags) })
        }
        controller.recordVideo(data, MediaCodec.BufferInfo().apply { set(0, 0, 0, 0) })
        assertEquals(0, observed)
        verify(exactly = 3) { inner.recordVideo(data, any()) }
    }
}
