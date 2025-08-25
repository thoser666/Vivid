package com.vivid.feature.streaming

import android.content.Context
import android.hardware.camera2.CameraAccessException
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.pedro.common.ConnectChecker // <-- Potentially this import, verify based on your library version
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.library.rtmp.RtmpCamera1
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class StreamingEngine @Inject constructor() : ConnectChecker {

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

    fun initializeCamera(context: Context) {
        try {
            // Create an instance of the generic ConnectChecker
            val connectChecker = object : ConnectChecker { // <-- This is the object causing the error
                // Add the missing method here
                override fun onConnectionStarted(url: String) {
                    // Implement what should happen when a connection starts for this specific checker
                    // For example, you might log it or update a specific UI element if this
                    // checker instance had its own state to manage.
                    // If it should behave the same as the StreamingEngine's main onConnectionStarted,
                    // you can call that, or simply replicate the logic.
                    // Log.d("StreamingEngine.initializeCamera", "Connection started to: $url")
                    _streamingError.value = null // Example: Clear previous errors
                }

                override fun onConnectionSuccess() {
                    _streamingError.value = null
                    // Potentially update _isStreaming if connection implies streaming has started
                    // or is ready to start. However, RtmpCamera usually manages this with its own state.
                }

                override fun onConnectionFailed(reason: String) {
                    _streamingError.value = "Connection failed: $reason"
                    _isStreaming.value = false
                }

                override fun onNewBitrate(bitrate: Long) {
                    // Handle new bitrate if necessary
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
                }
            }
            // Pass the context and the connectChecker instance
            rtmpCamera = RtmpCamera1(context, connectChecker)
        } catch (e: CameraOpenException) {
            _streamingError.value = "Camera initialization failed: ${e.message}"
        } catch (e: Exception) { // Catch more general exceptions during initialization
            _streamingError.value = "Error initializing camera: ${e.message}"
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
                        // Ensure video and audio are prepared before starting stream
                        // You might need to call prepareAudio and prepareVideo here
                        // if they haven't been called, or if settings can change.
                        // For example:
                        // if (!camera.isAudioPrepared || !camera.isVideoPrepared) {
                        //    camera.prepareAudio() // Default or configured settings
                        //    camera.prepareVideo() // Default or configured settings
                        // }
                        camera.startStream(rtmpUrl)
                        _isStreaming.value = true
                    }
                }
            }
        } catch (e: Exception) {
            _streamingError.value = "Streaming start failed: ${e.message}"
            _isStreaming.value = false
        }
    }

    fun stopStreaming() {
        try {
            rtmpCamera?.let { camera ->
                if (camera.isStreaming) {
                    camera.stopStream()
                }
            }
            // It's usually good practice to set _isStreaming.value to false
            // immediately after calling stopStream or if an error occurs.
            _isStreaming.value = false
            outputStreams.clear() // Consider if you want to clear streams on every stop
            inputPlayer?.stop()
            _streamingError.value = null
        } catch (e: Exception) {
            _streamingError.value = "Stop streaming failed: ${e.message}"
            _isStreaming.value = false // Ensure state is updated on error
        }
    }

    fun switchCamera() {
        try {
            rtmpCamera?.switchCamera()
        } catch (e: CameraOpenException) {
            _streamingError.value = "Failed to switch camera: ${e.message}"
        } catch (e: CameraAccessException) {
            Log.e("CameraSwitcher", "Camera access error", e)
            _streamingError.value = "Error accessing the camera: ${e.message}"
        } catch (e: IOException) {
            Log.e("CameraSwitcher", "I/O error during camera switch", e)
            _streamingError.value = "A problem occurred with camera input/output: ${e.message}"
        } catch (e: Exception) { // Fallback for truly unexpected errors
            Log.e("CameraSwitcher", "Unexpected error switching camera", e)
            _streamingError.value = "An unexpected error occurred while switching camera: ${e.message}"
        }
    }

    fun setVideoSettings(width: Int, height: Int, bitrate: Int, fps: Int) {
        // The RtmpCamera1 prepareVideo method also takes rotation, iFrameInterval (deprecated),
        // and force (deprecated) parameters.
        // Assuming default rotation (0) and handling potential exceptions.
        try {
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation,
            // int iFrameInterval, FormatVideoEncoder formatVideoEncoder, int avcProfile, int avcProfileLevel)
            // Simpler version: prepareVideo(int width, int height, int fps, int bitRate, int rotation)
            // Or the one you were likely using: prepareVideo(int width, int height, int fps, int bitRate, int iFrameInterval, int rotation)
            // The method signature from the library version you are using (2.4.8) is:
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation, int iFrameInterval, FormatVideoEncoder formatVideoEncoder)
            // Or with profile and level:
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation, int iFrameInterval, FormatVideoEncoder formatVideoEncoder, int avcProfile, int avcProfileLevel)

            // For simplicity, let's assume you want to use the basic prepareVideo.
            // Check the exact signature in your version of the library.
            // The library `com.github.pedroSG94:RootEncoder:2.4.8` for RtmpCamera1 has:
            // fun prepareVideo(width: Int, height: Int, fps: Int, bitRate: Int, rotation: Int, iFrameInterval: Int, formatVideoEncoder: FormatVideoEncoder)
            // You are missing rotation, iFrameInterval and formatVideoEncoder.
            // Let's use sensible defaults or allow them to be configured.
            // For now, I'll use a common default for rotation (0) and iFrameInterval (e.g., 2 seconds).
            // You'll need to decide on the FormatVideoEncoder (e.g., FormatVideoEncoder.SURFACE or FormatVideoEncoder.YUV).
            // This needs a View to render the preview, which you are not passing to RtmpCamera1.
            // If you are not showing a preview, you might need a different setup or RtmpCamera (e.g. RtmpCamera2 with GlInterface).

            // Given you are using RtmpCamera1(context, connectChecker), it implies you are not using a SurfaceView/TextureView directly with it.
            // This usually means you want to stream the camera without a visible preview directly tied to the RtmpCamera1 instance,
            // or you handle the preview separately.

            // The `prepareVideo` without a `FormatVideoEncoder` often defaults, but it's better to be explicit or ensure your
            // library version has an overload that matches.
            // The version 2.4.8 of RootEncoder has:
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation)
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation, int iFrameInterval)
            // prepareVideo(int width, int height, int fps, int bitRate, int rotation, int iFrameInterval, FormatVideoEncoder formatVideoEncoder)
            // etc.

            // Your current call `rtmpCamera?.prepareVideo(width, height, fps, bitrate, 0)`
            // seems to intend to use `prepareVideo(width, height, fps, bitrate, rotation)`
            // where the last 0 is for rotation.
            if (rtmpCamera?.prepareVideo(width, height, fps, bitrate, 0) == false) { // Assuming 0 for rotation
                _streamingError.value = "Prepare video failed. Check parameters."
            }
        } catch (e: Exception) {
            _streamingError.value = "Setting video settings failed: ${e.message}"
        }
    }

    fun setAudioSettings(sampleRate: Int, isStereo: Boolean, bitrate: Int) {
        try {
            // prepareAudio(int bitRate, int sampleRate, boolean isStereo, boolean echoCanceler, boolean noiseSuppressor)
            // Your call: rtmpCamera?.prepareAudio(sampleRate, isStereo, bitrate) is incorrect based on the common signature.
            // It should likely be:
            if (rtmpCamera?.prepareAudio(bitrate, sampleRate, isStereo, true, true) == false) { // Added echoCanceler and noiseSuppressor defaults
                _streamingError.value = "Prepare audio failed. Check parameters."
            }
        } catch (e: Exception) {
            _streamingError.value = "Setting audio settings failed: ${e.message}"
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
        stopStreaming() // Ensure stream is stopped first
//        rtmpCamera?.release()
        rtmpCamera = null
        inputPlayer?.release()
        inputPlayer = null
    }

    // These seem like placeholder methods, ensure they are implemented or removed if not needed.
    fun start() { /* ... */ }
    fun stop() { /* ... */ }
}
