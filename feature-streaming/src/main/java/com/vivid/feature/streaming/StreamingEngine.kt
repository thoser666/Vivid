package com.vivid.feature.streaming

import android.content.Context
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Ein Interface, das es uns erlaubt, die Kameraerstellung zu mocken
interface CameraFactory {
    fun create(openGlView: OpenGlView, connectCheckerRtmp: ConnectCheckerRtmp): RtmpCamera2
}

// Die echte Implementierung für die App
class RtmpCamera2Factory @Inject constructor(@ApplicationContext private val context: Context) : CameraFactory {
    override fun create(openGlView: OpenGlView, connectCheckerRtmp: ConnectCheckerRtmp): RtmpCamera2 {
        // Der Kontext wird jetzt hier korrekt von Hilt bereitgestellt
        return RtmpCamera2(openGlView, connectCheckerRtmp)
    }
}

@Singleton // Die Engine sollte ein Singleton sein, da sie die Kamera steuert
class StreamingEngine @Inject constructor(
    private val cameraFactory: CameraFactory, // <-- WIR INJIZIEREN EINE FACTORY
) {
    private var rtmpCamera: RtmpCamera2? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val connectChecker = object : ConnectCheckerRtmp {
        override fun onConnectionSuccessRtmp() {
            _streamingState.value = StreamingState.Streaming
        }

        override fun onConnectionFailedRtmp(reason: String) {
            _streamingState.value = StreamingState.Failed(reason)
            rtmpCamera?.stopStream()
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
            // Optional: Handle bitrate changes
        }

        override fun onDisconnectRtmp() {
            _streamingState.value = StreamingState.Idle
        }

        override fun onAuthErrorRtmp() {
            _streamingState.value = StreamingState.Failed("RTMP Auth Error")
        }

        override fun onAuthSuccessRtmp() {
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
