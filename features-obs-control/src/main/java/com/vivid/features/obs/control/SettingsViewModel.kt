package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Ein einziger "State"-Objekt, der den gesamten Zustand des Bildschirms darstellt.
data class ObsSettingsState(
    val host: String = "",
    val port: String = "",
    val password: String = "",
    val isSaving: Boolean = false // Um z.B. einen Ladeindikator anzuzeigen
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    // private val settingsRepository: ObsSettingsRepository
) : ViewModel() {

    // 2. Nur ein einziger StateFlow, der das State-Objekt hält.
    private val _uiState = MutableStateFlow(ObsSettingsState())
    val uiState = _uiState.asStateFlow()

    init {
        loadObsSettings()
    }

    private fun loadObsSettings() {
        viewModelScope.launch {
            // Logik zum Laden der Einstellungen aus dem Repository
            // val savedSettings = settingsRepository.getSettings()
            // _uiState.update {
            //     it.copy(
            //         host = savedSettings.host,
            //         port = savedSettings.port,
            //         password = savedSettings.password
            //     )
            // }
        }
    }

    // 3. Eine einzige Funktion, die auf alle UI-Änderungen reagiert.
    fun onSettingsChange(host: String, port: String, password: String) {
        _uiState.update {
            it.copy(host = host, port = port, password = password)
        }
    }

    fun saveObsSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            _uiState.value
            // Logik zum Speichern im Repository
            // settingsRepository.saveSettings(
            //     host = currentState.host,
            //     port = currentState.port,
            //     password = currentState.password
            // )

            _uiState.update { it.copy(isSaving = false) }
            // Hier könnte man ein Event für "Speichern erfolgreich" senden (z.B. für Navigation)
        }
    }
}