package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.network.obs.OBSWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val obsClient: OBSWebSocketClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ObsControlUiState>(ObsControlUiState.Idle)
    val uiState: StateFlow<ObsControlUiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<OBSWebSocketClient.ConnectionState> = obsClient.connectionState
    val streamState: StateFlow<OBSWebSocketClient.StreamState> = obsClient.streamState

    init {
        observeConnectionState()
        loadSettings()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            obsClient.connectionState.collect { state ->
                _uiState.value = when (state) {
                    is OBSWebSocketClient.ConnectionState.Connecting ->
                        ObsControlUiState.Connecting
                    is OBSWebSocketClient.ConnectionState.Connected ->
                        ObsControlUiState.Connected
                    is OBSWebSocketClient.ConnectionState.Error ->
                        ObsControlUiState.Error(state.message)
                    is OBSWebSocketClient.ConnectionState.Disconnected ->
                        ObsControlUiState.Idle
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { appSettings ->
                Timber.d("OBS Settings loaded: host=${appSettings.obsHost}, port=${appSettings.obsPort}")
            }
        }
    }

    fun connectToObs(host: String, port: Int, password: String?) {
        Timber.d("Connecting to OBS: $host:$port")

        // Optional: Einstellungen speichern
        viewModelScope.launch {
            settingsRepository.updateObsSettings(
                host = host,
                port = port.toString(),
                password = password ?: "",
            )
        }

        val config = OBSWebSocketClient.OBSConfig(host, port, password)
        obsClient.connect(config)
    }

    fun disconnect() {
        Timber.d("Disconnecting from OBS")
        obsClient.disconnect()
    }

    fun startStream() {
        Timber.d("Starting stream")
        when (val currentState = obsClient.streamState.value) {
            is OBSWebSocketClient.StreamState.Inactive -> {
                obsClient.startStream()
            }
            else -> {
                Timber.w("Cannot start stream, current state: $currentState")
            }
        }
    }

    fun stopStream() {
        Timber.d("Stopping stream")
        when (val currentState = obsClient.streamState.value) {
            is OBSWebSocketClient.StreamState.Active -> {
                obsClient.stopStream()
            }
            else -> {
                Timber.w("Cannot stop stream, current state: $currentState")
            }
        }
    }

    fun toggleObsStream() {
        when (val currentState = obsClient.streamState.value) {
            is OBSWebSocketClient.StreamState.Active -> {
                Timber.d("Toggling stream: stopping")
                obsClient.stopStream()
            }
            is OBSWebSocketClient.StreamState.Inactive -> {
                Timber.d("Toggling stream: starting")
                obsClient.startStream()
            }
            is OBSWebSocketClient.StreamState.Starting -> {
                Timber.d("Stream is starting, ignoring toggle")
            }
            is OBSWebSocketClient.StreamState.Stopping -> {
                Timber.d("Stream is stopping, ignoring toggle")
            }
        }
    }

    fun dismissError() {
        _uiState.value = ObsControlUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        obsClient.cleanup()
    }
}
