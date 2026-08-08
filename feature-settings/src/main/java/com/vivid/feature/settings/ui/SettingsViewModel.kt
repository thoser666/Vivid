package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings // Importiert die vollständige Klasse
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Der StateFlow verwendet jetzt die vollständige AppSettings-Klasse.
    private val _uiState = MutableStateFlow(AppSettings())
    val uiState = _uiState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<Unit>()
    val saveEvent = _saveEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            // Sammelt die Daten vom neuen, kombinierten Flow im Repository.
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.value = settings
            }
        }
    }

    // Diese Funktionen aktualisieren den State. Sie funktionieren dank .copy() perfekt.
    fun onStreamUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(streamUrl = newUrl) }
    fun onStreamKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(streamKey = newKey) }
    fun onObsHostChange(newHost: String) { _uiState.value = _uiState.value.copy(obsHost = newHost) }
    fun onObsPortChange(newPort: String) { _uiState.value = _uiState.value.copy(obsPort = newPort) }
    fun onObsPasswordChange(newPassword: String) { _uiState.value = _uiState.value.copy(obsPassword = newPassword) }
    fun onObsUseTlsChange(newUseTls: Boolean) { _uiState.value = _uiState.value.copy(obsUseTls = newUseTls) }

    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            // Speichere beide Einstellungs-Typen.
            settingsRepository.updateStreamSettings(
                url = currentSettings.streamUrl,
                key = currentSettings.streamKey,
            )
            settingsRepository.updateObsSettings(
                host = currentSettings.obsHost,
                port = currentSettings.obsPort,
                password = currentSettings.obsPassword,
                useTls = currentSettings.obsUseTls,
            )
            _saveEvent.emit(Unit)
        }
    }
}
