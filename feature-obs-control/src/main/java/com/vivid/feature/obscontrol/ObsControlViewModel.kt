package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.repository.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val uiState: StateFlow<ConnectionState> = _uiState.asStateFlow()

    init {
        streamingRepository.isConnectedToObs
            .onEach { isConnected ->
                _uiState.value = if (isConnected) ConnectionState.Connected else ConnectionState.Disconnected
            }
            .launchIn(viewModelScope)
    }

    fun connect(password: String, ip: String, port: String) {
        val portNumber = port.toIntOrNull()
        if (portNumber == null) {
            _uiState.value = ConnectionState.Error("Invalid port number")
            return
        }

        _uiState.value = ConnectionState.Connecting
        try {
            streamingRepository.connectToObs(password, ip, portNumber)
        } catch (e: Exception) {
            _uiState.value = ConnectionState.Error(e.message ?: "Failed to connect")
        }
    }

    fun disconnect() {
        streamingRepository.disconnectFromObs()
    }
}
