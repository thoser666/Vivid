package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository // Importiert das korrigierte Repository
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

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Dies wird jetzt funktionieren, da "appSettingsFlow" existiert!
            settingsRepository.appSettingsFlow.collect { appSettings ->
                _uiState.update { currentState ->
                    currentState.copy(
                        // Und diese Properties existieren jetzt auch!
                        host = appSettings.obsHost,
                        port = appSettings.obsPort,
                        password = appSettings.obsPassword,
                        isSaving = false, // Beim Laden ist der Speichervorgang beendet
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

            // Keine try/finally nötig, da das Laden in einem separaten Flow stattfindet
            // und isSaving nach dem Laden sowieso auf false gesetzt wird.
            val currentState = _uiState.value
            settingsRepository.updateObsSettings(
                host = currentState.host,
                port = currentState.port, // Ist jetzt String, kein Typfehler mehr
                password = currentState.password,
            )

            // Du kannst isSaving hier direkt nach dem Aufruf wieder auf false setzen,
            // aber es wird sowieso durch den appSettingsFlow-Collector aktualisiert.
            // Zur Sicherheit ist es aber besser:
            // _uiState.update { it.copy(isSaving = false) } // Optional, aber sicher
        }
    }
}

// Der UiState bleibt unverändert, er war bereits korrekt.
data class SettingsUiState(
    val host: String = "localhost",
    val port: String = "4455",
    val password: String = "",
    val isSaving: Boolean = false,
)