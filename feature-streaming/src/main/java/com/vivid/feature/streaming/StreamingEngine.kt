package com.vivid.feature.streaming

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView // Wichtig: Referenz auf die Basisklasse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingEngine @Inject constructor() {

    private var rtmpCamera: RtmpCamera2? = null

    private val _isStreamingFlow = MutableStateFlow(false) // Renamed for clarity
    val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow.asStateFlow() // Expose as StateFlow

    private val _streamingError = MutableStateFlow<String?>(null)
    val streamingError: StateFlow<String?> = _streamingError.asStateFlow()

    // Die UI-Schicht ruft diese Methode auf, wenn die OpenGlView erstellt wurde.
    fun initializeCamera(openGlView: OpenGlView) {
        // Erstellt die Kamera-Instanz genau dann, wenn sie gebraucht wird.
        rtmpCamera = RtmpCamera2(openGlView, object : ConnectChecker {
            // HIER IST DIE KORREKTUR: onConnectionStarted wurde hinzugefügt.
            override fun onConnectionStarted(url: String) {
                // Diese Methode wird aufgerufen, wenn der Verbindungsprozess beginnt.
                // Du könntest hier z.B. einen Lade-Indikator in der UI starten.

            }

            override fun onAuthError() {
                // Wird bei Authentifizierungsfehlern aufgerufen.
            }

            override fun onAuthSuccess() {
                // Wird bei erfolgreicher Authentifizierung aufgerufen.
            }

            override fun onConnectionSuccess() {
                // Wird aufgerufen, wenn die Verbindung zum Server erfolgreich hergestellt wurde.
                _isStreamingFlow.value = true // Update the flow
            }

            override fun onConnectionFailed(reason: String) {
                // Wird aufgerufen, wenn die Verbindung fehlschlägt.
                // Hier könntest du dem Benutzer eine Fehlermeldung anzeigen.
                _streamingError.value = reason
                _isStreamingFlow.value = false // Update the flow
                stopStream() // Stream-Zustand zurücksetzen
            }

            override fun onNewBitrate(bitrate: Long) {
                // Wird aufgerufen, wenn sich die Bitrate ändert (z.B. bei adaptivem Streaming).
            }

            override fun onDisconnect() {
                // Wird aufgerufen, wenn die Verbindung getrennt wird.
                _isStreamingFlow.value = false // Update the flow
            }
        })
    }

    fun startStream(url: String) {
        if (rtmpCamera?.isStreaming == false) {
            rtmpCamera?.startStream(url)
        }
    }

    fun stopStream() {
        if (rtmpCamera?.isStreaming == true) {
            rtmpCamera?.stopStream()
            _isStreamingFlow.value = false // Update the flow when stopping

        }
    }

    // You might still want a way to get the current status synchronously,
    // but it should have a different name if you keep the StateFlow as `isStreamingFlow`
    fun getCurrentStreamingStatus(): Boolean {
        return rtmpCamera?.isStreaming ?: false
    }

    // Gibt die Kamera frei und zerstört die Referenz.
    fun release() {
        stopStream()
        rtmpCamera = null
    }

//    val isStreaming: Boolean
//        get() = rtmpCamera?.isStreaming ?: false
}