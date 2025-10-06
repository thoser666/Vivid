// File: D:/dev/AndroidProjects/Vivid/features-obs-control/src/main/java/com/vivid/features/obs/control/ObsControlViewModel.kt

package com.vivid.features.obs.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.network.obs.OBSWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val obsClient: OBSWebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<ObsControlUiState>(ObsControlUiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        obsClient.connectionState
            .onEach { state ->
                // CORRECTED: Use direct comparison for object states.
                val newUiState = when (state) {
                    OBSWebSocketClient.ConnectionState.Connected -> ObsControlUiState.Connected
                    OBSWebSocketClient.ConnectionState.Connecting -> ObsControlUiState.Connecting
                    OBSWebSocketClient.ConnectionState.Disconnected -> ObsControlUiState.Idle
                    is OBSWebSocketClient.ConnectionState.Error -> ObsControlUiState.Error(state.message)
                }
                _uiState.value = newUiState
            }
            .launchIn(viewModelScope)
    }

    fun connectToObs() {
        viewModelScope.launch {
            // IMPORTANT: Real connection data must be loaded here.
            val host = "192.168.1.100"
            val port = 4455
            val password = "your_password"

            val config = OBSWebSocketClient.OBSConfig(host, port, password)
            obsClient.connect(config)
        }
    }

    fun dismissError() {
        _uiState.value = ObsControlUiState.Idle
    }
}
