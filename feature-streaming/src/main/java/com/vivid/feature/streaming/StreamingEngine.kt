package com.vivid.feature.streaming

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Ein Interface, das es uns erlaubt, die Kameraerstellung zu mocken
interface CameraFactory {
    fun create(openGlView: OpenGlView, connectChecker: ConnectChecker): RtmpCamera2
}

// Die echte Implementierung für die App
class RtmpCamera2Factory @Inject constructor() : CameraFactory {
    override fun create(openGlView: OpenGlView, connectChecker: ConnectChecker): RtmpCamera2 {
        return RtmpCamera2(openGlView, connectChecker)
    }
}

@Singleton // Die Engine sollte ein Singleton sein, da sie die Kamera steuert
class StreamingEngine @Inject constructor(
    private val cameraFactory: CameraFactory, // <-- WIR INJIZIEREN EINE FACTORY
) {
    private var rtmpCamera: RtmpCamera2? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            _streamingState.value = StreamingState.Preparing
        }

        override fun onConnectionSuccess() {
            _streamingState.value = StreamingState.Streaming
        }

        override fun onConnectionFailed(reason: String) {
            _streamingState.value = StreamingState.Failed(reason)
            rtmpCamera?.stopStream()
        }

        override fun onNewBitrate(bitrate: Long) {
            // Optional: Handle bitrate changes
        }

        override fun onDisconnect() {
            _streamingState.value = StreamingState.Idle
        }

        override fun onAuthError() {
            _streamingState.value = StreamingState.Failed("RTMP Auth Error")
        }

        override fun onAuthSuccess() {
            // Optional: Handle auth success
        }
    }

    fun initializeCamera(openGlView: OpenGlView) {
        // Wir verwenden jetzt die Factory, um die Kamera zu erstellen
        rtmpCamera = cameraFactory.create(openGlView, connectChecker)
    }

    fun startStream(url: String) {
        if (url.isBlank()) return

        if (rtmpCamera?.isStreaming == false) {
            _streamingState.value = StreamingState.Preparing
            if (rtmpCamera?.prepareAudio() == true && rtmpCamera?.prepareVideo() == true) {
                rtmpCamera?.startStream(url)
            } else {
                _streamingState.value = StreamingState.Failed("Failed to prepare audio/video")
            }
        }
    }

    fun stopStream() {
        if (rtmpCamera?.isStreaming == true) {
            rtmpCamera?.stopStream()
            _streamingState.value = StreamingState.Idle
        }
    }
}
