package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { appSettings ->
                _uiState.update { currentState ->
                    currentState.copy(
                        host = appSettings.obsHost,
                        port = appSettings.obsPort,
                        password = appSettings.obsPassword,
                        isSaving = false
                    )
                }
            }
        }
    }

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

            try {
                val currentState = _uiState.value
                settingsRepository.updateObsSettings(
                    host = currentState.host,
                    port = currentState.port,
                    password = currentState.password
                )
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

data class SettingsUiState(
    val host: String = "localhost",
    val port: String = "4455",
    val password: String = "",
    val isSaving: Boolean = false
)