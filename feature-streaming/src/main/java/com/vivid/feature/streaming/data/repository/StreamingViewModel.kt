// feature-streaming/src/main/java/com/vivid/feature/streaming/data/repository/StreamingViewModel.kt
package com.vivid.feature.streaming.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.network.obs.OBSWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val obsClient: OBSWebSocketClient
    // streamingEngine: StreamingEngine // Temporarily commented out
) : ViewModel() {

    // OBS WebSocket States - exposed to UI
    val connectionState: StateFlow<OBSWebSocketClient.ConnectionState> = obsClient.connectionState
    val streamState: StateFlow<OBSWebSocketClient.StreamState> = obsClient.streamState
    val errorState: StateFlow<String?> = obsClient.errorState

    // TODO: Add streamingEngine back when it's available
    // val streamingEngine = streamingEngine

    // OBS Connection Management
    fun connectToOBS(host: String = "localhost", port: Int = 4455, password: String? = null) {
        val config = OBSWebSocketClient.OBSConfig(host, port, password)
        obsClient.connect(config)
    }

    fun disconnectFromOBS() {
        obsClient.disconnect()
    }

    // OBS Stream Control
    fun startOBSStream() {
        obsClient.startStream()
    }

    fun stopOBSStream() {
        obsClient.stopStream()
    }

    fun getOBSStreamStatus() {
        obsClient.getStreamStatus()
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup OBS connection when ViewModel is destroyed
        obsClient.disconnect()
    }
}