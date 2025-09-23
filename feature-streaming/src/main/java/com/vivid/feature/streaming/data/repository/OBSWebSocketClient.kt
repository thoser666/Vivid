package com.vivid.feature.streaming.data.repository // Oder wo auch immer deine UI-Logik liegt

import androidx.lifecycle.ViewModel
import com.vivid.core.network.obs.OBSWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    // Hilt injiziert hier automatisch die Singleton-Instanz von OBSWebSocketClient
    val obsClient: OBSWebSocketClient,
) : ViewModel() {

    // Die Konfigurationsdaten könnten aus den SharedPreferences oder einer Einstellungs-UI kommen.
    // Hier als Beispiel hartcodiert.
    private val obsConfig = OBSWebSocketClient.OBSConfig(
        host = "10.0.0.244",
        port = 4455,
        password = "y5TYnQxz3zaRpR5e",
    )

    fun connectToObs() {
        obsClient.connect(obsConfig)
    }

    fun disconnectFromObs() {
        obsClient.disconnect()
    }

    fun toggleObsStream() {
        // Logik zum Starten/Stoppen des Streams in OBS
        val currentStreamState = obsClient.streamState.value
        if (currentStreamState == OBSWebSocketClient.StreamState.STREAMING) {
            obsClient.stopStream()
        } else if (currentStreamState == OBSWebSocketClient.StreamState.STOPPED) {
            obsClient.startStream()
        }
    }

    // Das ViewModel wird automatisch aufgeräumt, wenn es nicht mehr benötigt wird.
    // Ein explizites onCleared ist hier nicht nötig, da der Singleton weiterleben soll.
}
