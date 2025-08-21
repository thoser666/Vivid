package com.vivid.feature.streaming

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtmp.RtmpCamera1
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingEngine @Inject constructor() {

    private var outputStreams = mutableListOf<String>()
    private var inputPlayer: ExoPlayer? = null
    private var rtmpCamera: RtmpCamera1? = null

    // Streaming State
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _streamingError = MutableStateFlow<String?>(null)
    val streamingError = _streamingError.asStateFlow()

    fun addOutputStream(rtmpUrl: String) {
        if (!outputStreams.contains(rtmpUrl)) {
            outputStreams.add(rtmpUrl)
        }
    }

    fun removeOutputStream(rtmpUrl: String) {
        outputStreams.remove(rtmpUrl)
    }

    fun initializeCamera(context: Context) {
        try {
            rtmpCamera = RtmpCamera1(context) { connectionResult ->
                when (connectionResult) {
                    true -> _streamingError.value = null
                    false -> _streamingError.value = "Failed to connect to RTMP server"
                }
            }
        } catch (e: CameraOpenException) {
            _streamingError.value = "Camera initialization failed: ${e.message}"
        }
    }

    fun startStreaming() {
        if (outputStreams.isEmpty()) {
            _streamingError.value = "No output streams configured"
            return
        }

        try {
            outputStreams.forEach { rtmpUrl ->
                rtmpCamera?.let { camera ->
                    if (!camera.isStreaming) {
                        camera.startStream(rtmpUrl)
                        _isStreaming.value = true
                    }
                }
            }
        } catch (e: Exception) {
            _streamingError.value = "Streaming start failed: ${e.message}"
        }
    }

    fun stopStreaming() {
        try {
            rtmpCamera?.let { camera ->
                if (camera.isStreaming) {
                    camera.stopStream()
                    _isStreaming.value = false
                }
            }
            outputStreams.clear()
            inputPlayer?.stop()
            _streamingError.value = null
        } catch (e: Exception) {
            _streamingError.value = "Stop streaming failed: ${e.message}"
        }
    }

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun setVideoSettings(width: Int, height: Int, bitrate: Int, fps: Int) {
        rtmpCamera?.prepareVideo(width, height, fps, bitrate, 0)
    }

    fun setAudioSettings(sampleRate: Int, isStereo: Boolean, bitrate: Int) {
        rtmpCamera?.prepareAudio(sampleRate, isStereo, bitrate)
    }

    fun release() {
        stopStreaming()
        rtmpCamera?.release()
        rtmpCamera = null
        inputPlayer?.release()
        inputPlayer = null
    }

    fun start() { /* ... */ }
    fun stop() { /* ... */ }
}
