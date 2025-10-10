package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.network.obs.OBSWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

//sealed interface ObsControlUiState {
//    data object Idle : ObsControlUiState
//    data object Connecting : ObsControlUiState
//    data object Connected : ObsControlUiState
//    data class Error(val message: String) : ObsControlUiState
//}

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val obsClient: OBSWebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<ObsControlUiState>(ObsControlUiState.Idle)
    val uiState: StateFlow<ObsControlUiState> = _uiState.asStateFlow()

    val connectionState = obsClient.connectionState
    val streamState = obsClient.streamState

    init {
        observeConnectionState()
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

    fun connectToObs(host: String, port: Int, password: String?) {
        Timber.d("Connecting to OBS: $host:$port")
        val config = OBSWebSocketClient.OBSConfig(host, port, password)
        obsClient.connect(config)
    }

    fun disconnect() {
        Timber.d("Disconnecting from OBS")
        obsClient.disconnect()
    }

    fun startStream() {
        Timber.d("Starting stream")
        obsClient.startStream()
    }

    fun stopStream() {
        Timber.d("Stopping stream")
        obsClient.stopStream()
    }

    fun dismissError() {
        _uiState.value = ObsControlUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        obsClient.disconnect()
    }
}