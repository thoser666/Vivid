package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data class holding the UI state for the settings screen.
data class ObsSettingsState(
    val host: String = "",
    val port: String = "",
    val password: String = "",
    val isSaving: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    // TODO: Inject your actual settings repository here.
    // private val settingsRepository: ObsSettingsRepository
) : ViewModel() {

    // Private mutable state flow to hold the UI state.
    private val _uiState = MutableStateFlow(ObsSettingsState())
    // Public immutable state flow exposed to the UI.
    val uiState = _uiState.asStateFlow()

    init {
        // Load initial settings when the ViewModel is created.
    }

    // --- Event Handlers ---
    // These functions are called from the UI to update the state.

    fun onHostChanged(newHost: String) {
        _uiState.update { it.copy(host = newHost) }
    }

    fun onPortChanged(newPort: String) {
        _uiState.update { it.copy(port = newPort) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword) }
    }

    /**
     * Saves the current settings to the repository.
     */
    fun saveObsSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // TODO: Implement the actual saving logic with the repository.
            // For example:
            // settingsRepository.saveObsSettings(
            //     host = _uiState.value.host,
            //     port = _uiState.value.port,
            //     password = _uiState.value.password
            // )

            _uiState.update { it.copy(isSaving = false) }
        }
    }
}