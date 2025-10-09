package com.vivid.core.network.obs

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import timber.log.Timber
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
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
        val port: Int = 4455,
        val password: String? = null
    )

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.STOPPED)
    val streamState = _streamState.asStateFlow()

    fun connect(config: OBSConfig) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            Timber.w("Already connected or connecting.")
            return
        }

        scope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting

                val url = "ws://${config.host}:${config.port}"
                val request = Request.Builder().url(url).build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Timber.i("WebSocket opened")
                        scope.launch {
                            // OBS v5 Protocol: Send Identify message
                            val identifyMsg = mapOf(
                                "op" to 1,
                                "d" to mapOf(
                                    "rpcVersion" to 1,
                                    "authentication" to config.password,
                                    "eventSubscriptions" to 33 // Subscribe to events
                                )
                            )
                            webSocket.send(gson.toJson(identifyMsg))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Timber.d("Message received: $text")
                        handleMessage(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Timber.i("WebSocket closing: $code $reason")
                        webSocket.close(1000, null)
                        _connectionState.value = ConnectionState.Disconnected
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Timber.e(t, "WebSocket error")
                        _connectionState.value = ConnectionState.Error(
                            "Verbindung fehlgeschlagen: ${t.message}"
                        )
                    }
                })

            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to OBS")
                _connectionState.value = ConnectionState.Error(
                    "Initialisierung fehlgeschlagen: ${e.message}"
                )
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val message = gson.fromJson(text, Map::class.java)
            val op = (message["op"] as? Double)?.toInt()

            when (op) {
                0 -> { // Hello
                    Timber.d("Received Hello from OBS")
                }
                2 -> { // Identified
                    _connectionState.value = ConnectionState.Connected
                    Timber.i("Successfully connected to OBS")
                }
                5 -> { // Event
                    handleEvent(message)
                }
                7 -> { // RequestResponse
                    handleResponse(message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling message")
        }
    }

    private fun handleEvent(message: Map<*, *>) {
        val eventData = message["d"] as? Map<*, *>
        val eventType = eventData?.get("eventType") as? String

        when (eventType) {
            "StreamStateChanged" -> {
                val outputActive = eventData["eventData"] as? Map<*, *>
                val active = outputActive?.get("outputActive") as? Boolean
                _streamState.value = if (active == true) {
                    StreamState.STREAMING
                } else {
                    StreamState.STOPPED
                }
            }
        }
    }

    private fun handleResponse(message: Map<*, *>) {
        Timber.d("Response received: $message")
    }

    fun disconnect() {
        scope.launch {
            try {
                webSocket?.close(1000, "Client disconnect")
                webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                Timber.i("Disconnected from OBS")
            } catch (e: Exception) {
                Timber.e(e, "Error during disconnect")
            }
        }
    }

    fun startStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.STARTING
                sendRequest("StartStream")
                delay(1000) // Wait for confirmation
                _streamState.value = StreamState.STREAMING
            } catch (e: Exception) {
                Timber.e(e, "Failed to start stream")
                _streamState.value = StreamState.STOPPED
            }
        }
    }

    fun stopStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.STOPPING
                sendRequest("StopStream")
                delay(1000) // Wait for confirmation
                _streamState.value = StreamState.STOPPED
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop stream")
            }
        }
    }

    private fun sendRequest(requestType: String, requestData: Map<String, Any>? = null) {
        val request = mutableMapOf(
            "op" to 6,
            "d" to mutableMapOf(
                "requestType" to requestType,
                "requestId" to UUID.randomUUID().toString()
            )
        )

        requestData?.let {
            (request["d"] as MutableMap<String, Any>)["requestData"] = it
        }

        webSocket?.send(gson.toJson(request))
    }
}