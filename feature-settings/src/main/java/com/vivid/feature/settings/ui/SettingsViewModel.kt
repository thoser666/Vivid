package com.vivid.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings
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
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettings())
    val uiState: StateFlow<AppSettings> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.value = settings
            }
        }
    }

    fun onStreamUrlChange(newUrl: String) {
        _uiState.update { it.copy(streamUrl = newUrl) }
    }

    fun onStreamKeyChange(newKey: String) {
        _uiState.update { it.copy(streamKey = newKey) }
    }

    fun onObsHostChange(newHost: String) {
        _uiState.update { it.copy(obsHost = newHost) }
    }

    fun onObsPortChange(newPort: String) {
        _uiState.update { it.copy(obsPort = newPort) }
    }

    fun onObsPasswordChange(newPassword: String) {
        _uiState.update { it.copy(obsPassword = newPassword) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            settingsRepository.updateStreamSettings(
                url = currentSettings.streamUrl,
                key = currentSettings.streamKey,
            )
            settingsRepository.updateObsSettings(
                host = currentSettings.obsHost,
                port = currentSettings.obsPort,
                password = currentSettings.obsPassword,
            )
        }
    }
}
