// ObsControlViewModel.kt
package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.model.ObsQrCodeData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ajalt.obs.ws.OBSClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

// ... (ObsControlUiState sealed interface remains the same)

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    // No longer needs SettingsRepository for connection details
) : ViewModel() {

    private val _uiState = MutableStateFlow<ObsControlUiState>(ObsControlUiState.Idle)
    val uiState: StateFlow<ObsControlUiState> = _uiState.asStateFlow()

    private var obsClient: OBSClient? = null

    fun processQrCodeResult(qrCodeJson: String) {
        viewModelScope.launch {
            try {
                val obsData = Json.decodeFromString<ObsQrCodeData>(qrCodeJson)
                connectToObs(obsData)
            } catch (e: Exception) {
                _uiState.update { ObsControlUiState.Error("Ungültiger QR-Code.") }
            }
        }
    }

    private fun connectToObs(obsData: ObsQrCodeData) {
        viewModelScope.launch {
            _uiState.update { ObsControlUiState.Connecting }

            try {
                obsClient = OBSClient.builder()
                    .host(obsData.host)
                    .port(obsData.port)
                    .password(obsData.password)
                    .build()

                obsClient?.connect()
                _uiState.update { ObsControlUiState.Connected }

            } catch (e: Exception) {
                _uiState.update { ObsControlUiState.Error("Verbindung fehlgeschlagen: ${e.message}") }
            }
        }
    }

    fun disconnectFromObs() {
        viewModelScope.launch {
            obsClient?.disconnect()
            _uiState.update { ObsControlUiState.Idle }
        }
    }

    fun dismissError() {
        _uiState.update { ObsControlUiState.Idle }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            obsClient?.disconnect()
        }
    }
}