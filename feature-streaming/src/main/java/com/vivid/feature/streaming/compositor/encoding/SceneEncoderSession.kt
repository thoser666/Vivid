package com.vivid.feature.streaming.compositor.encoding

import android.content.Context
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.utils.CodecUtil
import com.vivid.feature.streaming.KeyframeIntervalController
import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import java.io.Closeable
import java.io.File

/**
 * One compositor output encoder shared by all RTMP targets and the MP4 muxer.
 * Owns no camera; sources render into [gl]. Calls must be serialized by the owner.
 * StreamingEngine owns routing and borrows source controls from the capture backend.
 */
class SceneEncoderSession internal constructor(
    private val stream: MultiStream,
    private val targetCount: Int,
) : Closeable {
    init { require(targetCount > 0) }
    val gl: GlStreamInterface get() = stream.getGlInterface()
    private val targets = mutableSetOf<Int>()
    private var recordingAudio: Boolean? = null
    private var microphone = false
    private var prepared = false
    private var closed = false
    val isStreaming: Boolean get() = targets.isNotEmpty()
    val isRecording: Boolean get() = recordingAudio != null

    /** No second video encoder or recording resolution is requested. */
    fun prepare(config: ResolvedEncoderConfig): Boolean {
        check(!closed && !isStreaming && !isRecording && !gl.isRunning)
        if (microphone) setMicrophone(false)
        prepared = false
        val codec = when (config.codec) {
            VideoCodecPreference.H265 -> VideoCodec.H265
            VideoCodecPreference.AV1 -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        stream.forceCodecType(CodecUtil.CodecType.HARDWARE, CodecUtil.CodecType.FIRST_COMPATIBLE_FOUND)
        stream.setVideoCodec(codec)
        val preset = config.preset
        gl.setForceRender(true, preset.fps)
        prepared = stream.prepareVideo(preset.width, preset.height,
            preset.videoBitrateKbps * 1_000, preset.fps, KeyframeIntervalController.INTERVAL_SECONDS, 0) &&
            stream.prepareAudio(32_000, true, 64_000)
        return prepared
    }

    fun startTarget(index: Int, url: String) {
        checkReady()
        require(index in 0 until targetCount && url.isNotBlank())
        check(index !in targets)
        targets.add(index)
        try {
            syncMicrophone()
            stream.startStream(MultiType.RTMP, index, url)
        } catch (failure: Exception) {
            targets.remove(index)
            runCatching { stream.stopStream(MultiType.RTMP, index) }
            if (!isStreaming) runCatching { prepared = stream.stopStream() }
            runCatching { syncMicrophone() }
            throw failure
        }
    }

    fun stopTarget(index: Int) {
        if (!targets.remove(index)) return
        // Release the microphone before RootEncoder re-prepares stopped encoders.
        try { syncMicrophone() }
        finally {
            try { stream.stopStream(MultiType.RTMP, index) }
            finally {
                // The indexed overload checks clients before disconnecting them.
                // Finish the shared encoder explicitly after the last disconnect.
                if (!isStreaming) prepared = stream.stopStream()
            }
        }
    }

    fun startRecording(file: File, includeAudio: Boolean,
        listener: RecordController.Listener = RecordController.Listener { }): Boolean {
        checkReady()
        check(!isRecording)
        file.parentFile?.mkdirs()
        recordingAudio = includeAudio
        return try {
            syncMicrophone()
            // StreamBase defaults NoVideoSource to AUDIO. Composited video must
            // always be explicitly selected, including for video-only recording.
            val tracks = if (includeAudio) RecordController.RecordTracks.ALL
                else RecordController.RecordTracks.VIDEO
            stream.startRecord(file.absolutePath, tracks, listener)
            true
        } catch (_: Exception) {
            recordingAudio = null
            runCatching { stream.stopRecord() }
            runCatching { syncMicrophone() }
            false
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        recordingAudio = null
        try { syncMicrophone() }
        finally { prepared = stream.stopRecord() }
    }

    /** Idle preview starts GL/NoVideoSource without allocating hardware encoders. */
    fun startPreview(surface: Surface, width: Int, height: Int) {
        check(!closed)
        if (stream.isOnPreview) stream.stopPreview()
        stream.startPreview(surface, width, height)
    }

    fun stopPreview() {
        if (stream.isOnPreview) stream.stopPreview()
    }

    fun setFpsListener(listener: (Int) -> Unit) { stream.setFpsListener { listener(it) } }

    fun setRecordController(controller: RecordController) { stream.setRecordController(controller) }

    fun setBitrateKbps(kbps: Int) {
        require(kbps > 0 && kbps <= Int.MAX_VALUE / 1_000)
        checkReady()
        stream.setVideoBitrateOnFly(kbps * 1_000)
    }

    private fun checkReady() { check(prepared && !closed) { "Scene encoder is not prepared" } }
    private fun syncMicrophone() = setMicrophone(isStreaming || recordingAudio == true)
    private fun setMicrophone(enabled: Boolean) {
        if (microphone == enabled) return
        val source = if (enabled) MicrophoneSource() else NoAudioSource()
        try {
            stream.changeAudioSource(source)
            microphone = enabled
        } catch (failure: Exception) {
            runCatching { source.stop() }
            runCatching { source.release() }
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        try {
            targets.toList().forEach { index -> runCatching { stopTarget(index) } }
            runCatching { stopRecording() }
        } finally {
            closed = true
            prepared = false
            try { stream.release() }
            finally {
                try { gl.deAttachPreview() }
                finally { if (gl.isRunning) gl.stop() }
            }
        }
    }

    companion object {
        /** The silent source avoids opening a microphone for video-only recording. */
        fun create(context: Context, checkers: List<ConnectChecker>): SceneEncoderSession {
            require(checkers.isNotEmpty())
            return SceneEncoderSession(MultiStream(context, checkers.toTypedArray(),
                emptyArray(), emptyArray(), emptyArray(), NoVideoSource(), NoAudioSource()), checkers.size)
        }
    }
}
