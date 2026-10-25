package com.vivid.feature.streaming.compositor

import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.*
import com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession
import io.mockk.*
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SceneEncoderSessionTest {
    @TempDir lateinit var directory: File
    private val gl = mockk<GlStreamInterface>(relaxed = true)
    private val stream = mockk<MultiStream>(relaxed = true) {
        every { getGlInterface() } returns gl
        every { prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true
        every { prepareAudio(any(), any(), any(), any(), any()) } returns true
        every { stopStream() } returns true
        every { stopRecord() } returns true
    }
    private fun session(): SceneEncoderSession = SceneEncoderSession(stream, 2).also {
        assertTrue(it.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.entries.first(), false)))
    }

    @Test fun `targets and recording share one preparation and last disconnect preserves recording`() {
        val session = session()
        session.startTarget(0, "rtmp://one/live")
        session.startTarget(1, "rtmp://two/live")
        assertTrue(session.startRecording(File(directory, "both.mp4"), true))
        session.stopTarget(0)
        verify(exactly = 0) { stream.stopStream() }
        session.stopTarget(1)
        assertTrue(session.isRecording)
        verify(exactly = 1) { stream.stopStream() }
        verify(exactly = 1) { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { stream.startRecord(any(), RecordController.RecordTracks.ALL, any()) }
        verify(exactly = 0) { stream.changeAudioSource(ofType<NoAudioSource>()) }
        session.stopRecording()
        verify(exactly = 1) { stream.changeAudioSource(ofType<NoAudioSource>()) }
    }

    @Test fun `video only recording retains video track and never opens microphone`() {
        val session = session()
        assertTrue(session.startRecording(File(directory, "silent.mp4"), false))
        verify { stream.startRecord(any(), RecordController.RecordTracks.VIDEO, any()) }
        verify(exactly = 0) { stream.changeAudioSource(ofType<MicrophoneSource>()) }
        session.startTarget(0, "rtmp://one/live")
        verify(exactly = 1) { stream.changeAudioSource(ofType<MicrophoneSource>()) }
        session.stopTarget(0)
        assertTrue(session.isRecording)
        verify(exactly = 1) { stream.changeAudioSource(ofType<NoAudioSource>()) }
        session.close()
        session.close()
        verify(exactly = 1) { stream.release() }
    }

    @Test fun `failed connection returns microphone ownership to video only recording`() {
        val session = session()
        session.startRecording(File(directory, "silent.mp4"), false)
        every { stream.startStream(MultiType.RTMP, 0, any()) } throws IllegalStateException("Transport unavailable")
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, "rtmp://one/live") }
        assertFalse(session.isStreaming)
        assertTrue(session.isRecording)
        verify { stream.changeAudioSource(ofType<MicrophoneSource>()); stream.changeAudioSource(ofType<NoAudioSource>()) }
    }

    @Test fun `microphone cleanup failure cannot leave a transport connected`() {
        val session = session()
        session.startTarget(0, "rtmp://one/live")
        every { stream.changeAudioSource(ofType<NoAudioSource>()) } throws IllegalStateException("Audio shutdown failed")
        assertThrows(IllegalStateException::class.java) { session.stopTarget(0) }
        verify { stream.stopStream(MultiType.RTMP, 0); stream.stopStream() }
        session.close()
        verify { stream.release() }
    }

    @Test fun `failed preparation cannot start output and hardware video is required`() {
        val session = SceneEncoderSession(stream, 2)
        every { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns false
        assertFalse(session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.HD30, false)))
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, "rtmp://one/live") }
        verify { stream.forceCodecType(com.pedro.encoder.utils.CodecUtil.CodecType.HARDWARE,
            com.pedro.encoder.utils.CodecUtil.CodecType.FIRST_COMPATIBLE_FOUND) }
        verify(exactly = 0) { stream.startStream(any(), any(), any()) }
    }

    @Test fun `live configuration cannot replace encoders and bitrate remains in kilobits`() {
        val session = session()
        session.startTarget(0, "rtmp://one/live")
        assertThrows(IllegalStateException::class.java) {
            session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.entries.first(), false))
        }
        session.setBitrateKbps(1_234)
        verify { stream.setVideoBitrateOnFly(1_234_000) }
        session.close()
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, "rtmp://one/live") }
    }
}
