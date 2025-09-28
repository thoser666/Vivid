package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.features.obs.control.ObsSettingsState // Stellen Sie sicher, dass dieser Import korrekt ist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


// State-Klasse bleibt unverändert
data class ObsSettingsState(
    val host: String = "",
    val port: String = "",
    val password: String = "",
    val isSaving: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    // private val settingsRepository: ObsSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObsSettingsState())
    val uiState = _uiState.asStateFlow()

    init {
        // Ladeinitialeinstellungen...
    }

    // --- NEU: Spezifische Event-Handler ---
    // Ersetzt die alte onSettingsChange-Funktion

    fun onHostChanged(newHost: String) {
        _uiState.update { it.copy(host = newHost) }
    }

    fun onPortChanged(newPort: String) {
        _uiState.update { it.copy(port = newPort) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword) }
    }


    fun saveObsSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            // Speicherlogik...
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}