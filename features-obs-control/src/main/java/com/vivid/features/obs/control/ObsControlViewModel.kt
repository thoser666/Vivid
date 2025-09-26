package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Definiert den UI-Zustand. Diese 'sealed interface' MUSS in derselben Datei
// oder demselben Paket sein und korrekt importiert werden.
sealed interface ObsControlUiState {
    object Idle : ObsControlUiState
    object Connecting : ObsControlUiState
    object Connected : ObsControlUiState
    data class Error(val message: String) : ObsControlUiState
}

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
    // private val obsWebSocketClient: OBSWebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<ObsControlUiState>(ObsControlUiState.Idle)
    val uiState: StateFlow<ObsControlUiState> = _uiState.asStateFlow()

    fun connectToObs() {
        viewModelScope.launch {
            _uiState.update { ObsControlUiState.Connecting }

            val obsSettings = settingsRepository.appSettingsFlow.first()
            val host = obsSettings.obsHost
            val port = obsSettings.obsPort

            if (host.isBlank()) {
                _uiState.update { ObsControlUiState.Error("OBS Host ist nicht konfiguriert.") }
                return@launch
            }
            if (port.isBlank() || port.toIntOrNull() == null) {
                _uiState.update { ObsControlUiState.Error("OBS Port ist ungültig.") }
                return@launch
            }

            try {
                // Hier die echte Verbindungslogik einfügen
                _uiState.update { ObsControlUiState.Error("Verbindung fehlgeschlagen (Beispiel).") }
            } catch (e: Exception) {
                _uiState.update { ObsControlUiState.Error("Fehler: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { ObsControlUiState.Idle }
    }
}