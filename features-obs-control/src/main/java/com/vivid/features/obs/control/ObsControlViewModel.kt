package com.vivid.features.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    // Fügen Sie hier Ihre OBSWebSocketClient-Abhängigkeit hinzu
) : ViewModel() {

    init {
        // Beispiel: Verbindung beim Initialisieren des ViewModels herstellen
        connectToObs()
    }

    private fun connectToObs() {
        viewModelScope.launch {
            // Holen Sie sich die neuesten Einstellungen aus dem Repository
            val obsSettings = settingsRepository.appSettingsFlow.first()

            val host = obsSettings.obsHost
            val port = obsSettings.obsPort.toIntOrNull() ?: 4455 // Sichere Konvertierung
            val password = obsSettings.obsPassword

            // Verwenden Sie die Einstellungen, um die Verbindung mit Ihrem Client herzustellen
            // z.B. obsWebSocketClient.connect(host, port, password)
        }
    }

    // ... Restliche Logik zur Steuerung von OBS
}
