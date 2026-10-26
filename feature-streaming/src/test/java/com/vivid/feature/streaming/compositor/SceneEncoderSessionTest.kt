package com.vivid.feature.streaming.compositor

import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.*
import com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession
import com.vivid.feature.streaming.compositor.audio.SceneAudioInput
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.vivid.feature.streaming.compositor.audio.SceneAudioInputKind
import com.vivid.feature.streaming.compositor.audio.SceneAudioRouterSource
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
        session.startTarget(0, SCENE_RTMP_ONE_LIVE)
        session.startTarget(1, "rtmp://two/live")
        assertTrue(session.startRecording(File(directory, "both.mp4"), true))
        session.stopTarget(0)
        verify(exactly = 0) { stream.stopStream() }
        session.stopTarget(1)
        assertTrue(session.isRecording)
        verify(exactly = 1) { stream.stopStream() }
        verify(exactly = 1) { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { stream.startRecord(any(), RecordController.RecordTracks.ALL, any()) }
        assertEquals(SceneAudioInputKind.MICROPHONE, session.audioInputKey)
        session.stopRecording()
        assertEquals(SceneAudioInputKind.DISABLED, session.audioInputKey)
        verify(exactly = 0) { stream.changeAudioSource(any()) }
    }

    @Test fun `video only recording retains video track and never opens microphone`() {
        val session = session()
        assertTrue(session.startRecording(File(directory, "silent.mp4"), false))
        verify { stream.startRecord(any(), RecordController.RecordTracks.VIDEO, any()) }
        assertEquals(SceneAudioInputKind.DISABLED, session.audioInputKey)
        session.startTarget(0, SCENE_RTMP_ONE_LIVE)
        assertEquals(SceneAudioInputKind.MICROPHONE, session.audioInputKey)
        session.stopTarget(0)
        assertTrue(session.isRecording)
        assertEquals(SceneAudioInputKind.DISABLED, session.audioInputKey)
        session.close()
        session.close()
        verify(exactly = 1) { stream.release() }
    }

    @Test fun `failed connection returns microphone ownership to video only recording`() {
        val session = session()
        session.startRecording(File(directory, "silent.mp4"), false)
        every { stream.startStream(MultiType.RTMP, 0, any()) } throws IllegalStateException("Transport unavailable")
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, SCENE_RTMP_ONE_LIVE) }
        assertFalse(session.isStreaming)
        assertTrue(session.isRecording)
        assertEquals(SceneAudioInputKind.DISABLED, session.audioInputKey)
    }

    @Test fun `audio cleanup failure cannot leave a transport connected`() {
        val audio = mockk<SceneAudioRouterSource>(relaxed = true)
        val session = SceneEncoderSession(stream, 2, audio)
        session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.HD30, false))
        session.startTarget(0, SCENE_RTMP_ONE_LIVE)
        every { audio.select(match { it.key == SceneAudioInputKind.DISABLED }, any()) } throws IllegalStateException("Audio shutdown failed")
        assertThrows(IllegalStateException::class.java) { session.stopTarget(0) }
        verify { stream.stopStream(MultiType.RTMP, 0); stream.stopStream() }
        session.close()
        verify { stream.release(); audio.release() }
    }

    @Test fun `failed preparation cannot start output and hardware video is required`() {
        val session = SceneEncoderSession(stream, 2)
        every { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns false
        assertFalse(session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.HD30, false)))
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, SCENE_RTMP_ONE_LIVE) }
        verify { stream.forceCodecType(com.pedro.encoder.utils.CodecUtil.CodecType.HARDWARE,
            com.pedro.encoder.utils.CodecUtil.CodecType.FIRST_COMPATIBLE_FOUND) }
        verify(exactly = 0) { stream.startStream(any(), any(), any()) }
    }

    @Test fun `live configuration cannot replace encoders and bitrate remains in kilobits`() {
        val session = session()
        session.startTarget(0, SCENE_RTMP_ONE_LIVE)
        assertThrows(IllegalStateException::class.java) {
            session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.entries.first(), false))
        }
        session.setBitrateKbps(1_234)
        verify { stream.setVideoBitrateOnFly(1_234_000) }
        session.close()
        assertThrows(IllegalStateException::class.java) { session.startTarget(0, SCENE_RTMP_ONE_LIVE) }
    }
    @Test fun `idle preview needs no encoder preparation and close releases its GL surface`() {
        val session = SceneEncoderSession(stream, 2)
        val surface = mockk<android.view.Surface>(relaxed = true)
        session.startPreview(surface, 640, 360)
        verify { stream.startPreview(surface, 640, 360) }
        verify(exactly = 0) { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        every { gl.isRunning } returns true
        session.close()
        verify { gl.deAttachPreview(); gl.stop(); stream.release() }
        assertThrows(IllegalStateException::class.java) { session.startPreview(surface, 640, 360) }
    }

    @Test fun `live file input changes retain the same video and audio encoder preparation`() {
        class FileInput : AudioSource() {
            var starts = 0
            var stops = 0
            var releases = 0
            override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean) = true
            override fun start(getMicrophoneData: GetMicrophoneData) { starts++ }
            override fun stop() { stops++ }
            override fun isRunning() = starts > stops
            override fun release() { releases++ }
        }
        val audio = SceneAudioRouterSource()
        val session = SceneEncoderSession(stream, 2, audio)
        assertTrue(session.prepare(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.HD30, false)))
        assertTrue(audio.init(32_000, true, false, false))
        val receiver = object : GetMicrophoneData { override fun inputPCMData(frame: Frame) = Unit }
        every { stream.startStream(any(), any(), any()) } answers { audio.start(receiver) }
        val first = FileInput(); val second = FileInput()
        session.setAudioInput(SceneAudioInput("first-file") { first })
        session.startTarget(0, SCENE_RTMP_ONE_LIVE)
        session.startTarget(1, "rtmp://two/live")
        assertTrue(session.startRecording(File(directory, "files.mp4"), true))
        assertEquals(1, first.starts)
        session.setAudioInput(SceneAudioInput("second-file") { second })
        assertEquals(1, first.releases); assertEquals(1, second.starts)
        assertEquals(32_000, second.sampleRate); assertTrue(second.isStereo)
        session.stopTarget(0); session.stopTarget(1)
        assertEquals(0, second.stops) // Recording is still consuming the selected file.
        session.stopRecording()
        assertEquals(1, second.releases)
        verify(exactly = 1) { stream.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { stream.prepareAudio(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { stream.changeAudioSource(any()) }
        session.close()
    }

    @Test fun `root release failure still retires the stable audio router and GL`() {
        val audio = mockk<SceneAudioRouterSource>(relaxed = true)
        val session = SceneEncoderSession(stream, 2, audio)
        every { stream.release() } throws IllegalStateException("Root release failed")
        every { gl.isRunning } returns true
        assertThrows(IllegalStateException::class.java) { session.close() }
        verify { audio.release(); gl.deAttachPreview(); gl.stop() }
    }

}

private const val SCENE_RTMP_ONE_LIVE = "rtmp://one/live"
