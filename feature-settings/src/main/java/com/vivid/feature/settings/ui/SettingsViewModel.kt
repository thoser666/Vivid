package com.vivid.feature.settings.ui // Der Paketname sollte zu Ihrer Struktur passen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettings())
    val uiState = _uiState.asStateFlow()

    // SharedFlow für einmalige Events wie Navigation oder Snackbars
    private val _saveEvent = MutableSharedFlow<Unit>()
    val saveEvent = _saveEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.value = settings
            }
        }
    }

    // ... (onStreamUrlChange, onStreamKeyChange, etc. bleiben unverändert)
    fun onStreamUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(streamUrl = newUrl) }
    fun onStreamKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(streamKey = newKey) }
    fun onObsHostChange(newHost: String) { _uiState.value = _uiState.value.copy(obsHost = newHost) }
    fun onObsPortChange(newPort: String) { _uiState.value = _uiState.value.copy(obsPort = newPort) }
    fun onObsPasswordChange(newPassword: String) { _uiState.value = _uiState.value.copy(obsPassword = newPassword) }


    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            settingsRepository.updateStreamSettings(
                url = currentSettings.streamUrl,
                key = currentSettings.streamKey
            )
            settingsRepository.updateObsSettings(
                host = currentSettings.obsHost,
                port = currentSettings.obsPort,
                password = currentSettings.obsPassword
            )
            // Event auslösen, nachdem das Speichern abgeschlossen ist
            _saveEvent.emit(Unit)
        }
    }
}