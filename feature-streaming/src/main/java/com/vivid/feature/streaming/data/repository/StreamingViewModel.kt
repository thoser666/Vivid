// feature-streaming/src/main/java/com/vivid/feature/streaming/data/repository/StreamingViewModel.kt
package com.vivid.feature.streaming.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository // Importieren Sie Ihr SettingsRepository
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.feature.streaming.StreamingEngine // Stellen Sie sicher, dass der Import korrekt ist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    // KORREKTUR 1: Injizieren Sie sowohl die StreamingEngine als auch das SettingsRepository
    val streamingEngine: StreamingEngine,
    private val settingsRepository: SettingsRepository,
    private val obsClient: OBSWebSocketClient
) : ViewModel() {

    // --- Lokale Kamera-Zustände (von der StreamingEngine) ---
    // Diese sind für die Kameravorschau und das direkte Streaming vom Gerät.
    val isStreaming: StateFlow<Boolean> = streamingEngine.isStreaming
    val streamingError: StateFlow<String?> = streamingEngine.streamingError

    // --- RTMP-URL aus den Einstellungen ---
    // Holt die URL aus dem DataStore und stellt sie der UI zur Verfügung.
    val rtmpUrl: StateFlow<String> = settingsRepository.streamSettingsFlow
        .map { it.url } // Wir benötigen nur die URL für den Start-Button
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    // --- OBS WebSocket Zustände (vom OBSWebSocketClient) ---
    // Diese sind NUR für die Fernsteuerung von OBS Studio relevant.
    val obsConnectionState: StateFlow<OBSWebSocketClient.ConnectionState> = obsClient.connectionState

    // HINWEIS: Es gibt keine 'streamState' oder 'errorState' im OBSWebSocketClient.
    // Diese Zustände kamen fälschlicherweise aus der Annahme, der Client würde den Stream-Status verwalten.
    // Der tatsächliche lokale Stream-Status kommt von der StreamingEngine (siehe oben).

    // --- OBS Verbindungs-Management ---
    fun connectToOBS(host: String, port: Int, password: String) {
      //  obsClient.connect(host, port, password)
        val config = OBSWebSocketClient.OBSConfig(host = host, port = port, password = password)
        obsClient.connect(config)
    }

    fun disconnectFromOBS() {
        obsClient.disconnect()
    }

    // --- OBS Stream-Steuerung (Beispiele) ---
    fun startOBSStream() {
 //       obsClient.setCurrentScene("Live Scene") // Beispiel: Szene wechseln
    }

    override fun onCleared() {
        super.onCleared()
        // Räumt sowohl die Engine-Ressourcen als auch die OBS-Verbindung auf
        streamingEngine.release()
        obsClient.disconnect()
    }
}