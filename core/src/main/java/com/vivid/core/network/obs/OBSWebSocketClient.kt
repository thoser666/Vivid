// Datei: core/src/.../com/vivid/core/network/obs/OBSWebSocketClient.kt

package com.vivid.core.network.obs

// KORREKTUR: Fehlende Importe hinzugefügt
import io.github.ajalt.obs.websocket.client.ObsWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber // <-- Import für Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OBSWebSocketClient @Inject constructor() {

    sealed interface ConnectionState {
        object Connected : ConnectionState
        object Connecting : ConnectionState
        object Disconnected : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    enum class StreamState {
        STREAMING, STOPPED, STARTING, STOPPING
    }

    data class OBSConfig(
        val host: String,
        val port: Int,
        val password: String?
    )

    // KORREKTUR: Der Typ muss der der importierten Bibliothek sein
    private var client: ObsWebSocketClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.STOPPED)
    val streamState = _streamState.asStateFlow()

    fun connect(config: OBSConfig) {
        if (_connectionState.value is ConnectionState.Connecting || _connectionState.value is ConnectionState.Connected) {
            Timber.w("Already connected or connecting.")
            return
        }

        scope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting
                // KORREKTUR: Der Klassenname muss der der importierten Bibliothek sein
                client = ObsWebSocketClient.builder()
                    .host(config.host)
                    .port(config.port)
                    .password(config.password)
                    .build()

                client?.connect()
                _connectionState.value = ConnectionState.Connected
                Timber.i("Successfully connected to OBS.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to OBS")
                _connectionState.value = ConnectionState.Error("Verbindung fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            client?.disconnect()
            client = null
            _connectionState.value = ConnectionState.Disconnected
            Timber.i("Disconnected from OBS.")
        }
    }

    fun startStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.STARTING
                client?.api?.startStream() // API kann sich je nach Lib-Version ändern
                _streamState.value = StreamState.STREAMING
            } catch(e: Exception) {
                Timber.e(e, "Failed to start stream")
                _streamState.value = StreamState.STOPPED
            }
        }
    }

    fun stopStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.STOPPING
                client?.api?.stopStream() // API kann sich je nach Lib-Version ändern
                _streamState.value = StreamState.STOPPED
            } catch(e: Exception) {
                Timber.e(e, "Failed to stop stream")
            }
        }
    }
}