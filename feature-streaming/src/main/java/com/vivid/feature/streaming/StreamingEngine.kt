package com.vivid.feature.streaming

import android.hardware.camera2.CameraAccessException
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.common.ConnectChecker // <-- Potentially this import, verify based on your library version
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class StreamingEngine @Inject constructor() : ConnectChecker {

    private var outputStreams = mutableListOf<String>()
    private var inputPlayer: ExoPlayer? = null
    private var rtmpCamera: RtmpCamera2? = null

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

    override fun onConnectionStarted(url: String) {
        // Called when the connection process has started for a specific URL.
        // You can log this or update UI if needed.
        // For example:
        // Log.d("StreamingEngine", "Connection started to: $url")
        _streamingError.value = null // Clear previous errors
    }

    // Make sure to implement all other methods from ConnectChecker interface
    override fun onConnectionSuccess() {
        _streamingError.value = null
        // Potentially update _isStreaming if connection implies streaming has started
        // or is ready to start.
    }

    override fun onConnectionFailed(reason: String) {
        _streamingError.value = "Connection failed: $reason"
        _isStreaming.value = false
    }

    override fun onNewBitrate(bitrate: Long) {
        // Handle new bitrate if necessary
        // Log.d("StreamingEngine", "New bitrate: $bitrate")
    }

    override fun onDisconnect() {
        _streamingError.value = "Disconnected" // Or null if this is an expected state
        _isStreaming.value = false
    }

    override fun onAuthError() {
        _streamingError.value = "Authentication error"
        _isStreaming.value = false
    }

    override fun onAuthSuccess() {
        // Handle successful authentication if necessary
        // Log.d("StreamingEngine", "Authentication successful")
    }

    fun initializeCamera(openGlView: OpenGlView) { // Changed parameter type
        if (rtmpCamera != null) {
            release()
        }
        try {
            rtmpCamera = RtmpCamera2(openGlView, this) // Pass 'this' as the ConnectChecker
            // The lambda you had was likely intended for a different callback or was a misunderstanding
            // of the constructor. The ConnectChecker interface methods (onConnectionSuccess, onConnectionFailed, etc.)
            // will be called on 'this' (StreamingEngine) instance.
            rtmpCamera?.startPreview()
        } catch (e: CameraAccessException) { // Or whatever specific exception RtmpCamera2 might throw
            _streamingError.value = "Camera access denied or unavailable: ${e.message}"
            // Potentially request permissions or inform the user
        } catch (e: IOException) {
            _streamingError.value = "Camera I/O error during initialization: ${e.message}"
            // Handle network or file system issues
        } catch (e: RuntimeException) { // Catch other potential runtime issues from the library
            _streamingError.value = "Camera initialization failed: ${e.message}"
            // Log this with more details for debugging
            Log.e("StreamingEngine", "Unexpected error during camera init", e)
        }
    }
    fun startStreaming(rtmpUrl: String) {
        if (rtmpUrl.isBlank()) {
            _streamingError.value = "No output stream URL configured"
            return
        }
        try {
            rtmpCamera?.let { camera ->
                if (!camera.isStreaming) {
                    // Die URL wird hier gesetzt, nicht in einer separaten Liste
                    camera.startStream(rtmpUrl)
                    _isStreaming.value = true
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
            _streamingError.value = null
        } catch (e: Exception) {
            _streamingError.value = "Stop streaming failed: ${e.message}"
        }
    }

    fun switchCamera() {
        try {
            rtmpCamera?.switchCamera()
        } catch (e: CameraOpenException) {
            _streamingError.value = "Failed to switch camera: ${e.message}"
        }
    }

    fun setVideoSettings(width: Int, height: Int, bitrate: Int, fps: Int, rotation: Int) {
        try {
            if (rtmpCamera?.prepareVideo(width, height, fps, bitrate, rotation) == false) {
                _streamingError.value = "Prepare video failed. Check parameters."
            }
        } catch (e: IllegalArgumentException) {
            val errorMsg = "Invalid parameters for video settings: ${e.message}. Values: w=$width, h=$height, fps=$fps, br=$bitrate"
            Log.e("StreamingEngine", errorMsg, e)
            _streamingError.value = "Invalid video settings: ${e.localizedMessage}"
        } catch (e: Exception) { // Catch-all for unexpected issues
            val errorMsg =
                "Unexpected error setting video settings: ${e.message}. Parameters: w=$width, h=$height, fps=$fps, br=$bitrate"
            Log.e("StreamingEngine", errorMsg, e) // Log the full stack trace
            _streamingError.value = "An unexpected error occurred while setting video settings."
        }
    }

    fun setAudioSettings(sampleRate: Int, isStereo: Boolean, bitrate: Int) {
        try {
            // Correct signature: prepareAudio(bitRate: Int, sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean)
            if (rtmpCamera?.prepareAudio(bitrate, sampleRate, isStereo, true, true) == false) {
                _streamingError.value = "Prepare audio failed. Check parameters."
            }
        } catch (iae: IllegalArgumentException) {
            _streamingError.value = "Invalid audio parameters: ${iae.message}. Please check your inputs."
            Log.e("StreamingEngine", "IllegalArgumentException during audio prep: ${iae.message}", iae)
        } catch (e: Exception) {
            _streamingError.value = "An unexpected error occurred while configuring audio. Please try again."
            Log.e("StreamingEngine", "Exception during audio prep: ${e.message}", e)
        }
    }

    // ... inside your StreamingEngine class
// fun re // This was the incomplete line

    fun releaseResources() { // Or a more descriptive name like cleanup, dispose, etc.
        stopStreaming() // Good practice to stop streaming before releasing

        rtmpCamera?.stopPreview() // If you start a preview, stop it
        // Depending on the RtmpCamera library version and how you've used it,
        // there might not be a specific 'release' method on rtmpCamera itself,
        // as stopping the stream and preview often handles resource cleanup.
        // Check the RtmpCamera1 documentation for specific cleanup methods if needed.
        rtmpCamera = null

        inputPlayer?.release()
        inputPlayer = null

        outputStreams.clear()
        _streamingError.value = null
        _isStreaming.value = false // Ensure state is reset
    }

    fun release() {
        if (rtmpCamera?.isStreaming == true) {
            stopStreaming()
        }
        rtmpCamera?.stopPreview()
        rtmpCamera = null
    }

    fun startPreview() {
        try {
            rtmpCamera?.startPreview()
        } catch (e: Exception) {
            _streamingError.value = "Failed to start camera preview: ${e.message}"
        }
    }
    fun stopPreview() {
        try {
            rtmpCamera?.stopPreview()
        } catch (e: Exception) {
            // Dies kann fehlschlagen, wenn die Kamera bereits freigegeben ist,
            // daher ist es oft sicher, diesen Fehler zu ignorieren oder nur zu protokollieren.
            Log.w("StreamingEngine", "Exception while stopping preview: ${e.message}")
        }
    }

    // These seem like placeholder methods, ensure they are implemented or removed if not needed.
    fun start() { /* ... */ }
    fun stop() { /* ... */ }
}
