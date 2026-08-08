package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.repository.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val uiState: StateFlow<ConnectionState> = _uiState.asStateFlow()

    // Gespeichertes Scheme aus den Einstellungen, damit das Quick-Connect-Panel
    // nicht still auf ws:// zurücksetzt, wenn wss:// konfiguriert wurde.
    private val _savedUseTls = MutableStateFlow(false)
    val savedUseTls: StateFlow<Boolean> = _savedUseTls.asStateFlow()

    init {
        streamingRepository.isConnectedToObs
            .onEach { isConnected ->
                _uiState.value = if (isConnected) ConnectionState.Connected else ConnectionState.Disconnected
            }
            .launchIn(viewModelScope)

        settingsRepository.appSettingsFlow
            .map { it.obsUseTls }
            .onEach { _savedUseTls.value = it }
            .launchIn(viewModelScope)
    }

    fun connect(password: String, ip: String, port: String, useTls: Boolean = false) {
        val portNumber = port.toIntOrNull()
        if (portNumber == null) {
            _uiState.value = ConnectionState.Error("Invalid port number")
            return
        }

        _uiState.value = ConnectionState.Connecting
        try {
            streamingRepository.connectToObs(password, ip, portNumber, useTls)
        } catch (e: Exception) {
            _uiState.value = ConnectionState.Error(e.message ?: "Failed to connect")
        }
    }

    fun disconnect() {
        streamingRepository.disconnectFromObs()
    }
}
