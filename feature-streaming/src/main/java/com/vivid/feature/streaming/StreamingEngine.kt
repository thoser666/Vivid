package com.vivid.feature.streaming

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingEngine @Inject constructor() {

    private var rtmpCamera: RtmpCamera2? = null
    private var currentUrl: String? = null

    // KORREKTUR 1: Den neuen StateFlow verwenden
    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    fun initializeCamera(openGlView: OpenGlView) {
        if (rtmpCamera != null) return // Verhindert mehrfache Initialisierung

        rtmpCamera = RtmpCamera2(openGlView, object : ConnectChecker {
            override fun onConnectionStarted(url: String) {
                _streamingState.value = StreamingState.Preparing
            }

            override fun onConnectionSuccess() {
                _streamingState.value = StreamingState.Streaming
            }

            override fun onConnectionFailed(reason: String) {
                rtmpCamera?.stopStream() // Encoder stoppen und zurücksetzen
                _streamingState.value = StreamingState.Failed(reason)
            }

            override fun onDisconnect() {
                _streamingState.value = StreamingState.Idle
            }

            override fun onAuthError() {
                rtmpCamera?.stopStream()
                _streamingState.value = StreamingState.Failed("Authentication error")
            }

            override fun onAuthSuccess() {
                // Wird vor onConnectionSuccess aufgerufen
            }

            override fun onNewBitrate(bitrate: Long) {
                // Optional: Für UI-Anzeigen der Bitrate
            }
        })
    }

    // KORREKTUR 2: Überarbeitete startStream-Methode
    fun startStream(url: String) {
        if (rtmpCamera == null) {
            _streamingState.value = StreamingState.Failed("Camera not initialized")
            return
        }
        if (streamingState.value is StreamingState.Streaming || streamingState.value is StreamingState.Preparing) {
            return // Verhindert mehrfaches Starten
        }

        currentUrl = url
        try {
            // Dies ist der entscheidende asynchrone Schritt!
            // Wir bereiten den Encoder mit Standardwerten vor.
            val videoPrepared = rtmpCamera!!.prepareVideo(1920, 1080, 30, 2 * 1024 * 1024, 0)
            val audioPrepared = rtmpCamera!!.prepareAudio(128 * 1024, 44100, true)

            if (videoPrepared && audioPrepared) {
                _streamingState.value = StreamingState.Preparing
                // Die Bibliothek ruft jetzt intern `onConnectionStarted` auf,
                // und wenn die Verbindung steht, wird `onConnectionSuccess` getriggert.
                rtmpCamera?.startStream(url)
            } else {
                _streamingState.value = StreamingState.Failed("Failed to prepare encoders.")
            }
        } catch (e: Exception) {
            _streamingState.value = StreamingState.Failed(e.message ?: "Unknown error during preparation")
        }
    }

    fun stopStream() {
        if (rtmpCamera?.isStreaming == true) {
            rtmpCamera?.stopStream()
        }
        _streamingState.value = StreamingState.Idle
    }

    fun release() {
        stopStream()
        rtmpCamera = null
        _streamingState.value = StreamingState.Idle
    }
}