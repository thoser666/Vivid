package com.vivid.core.network.obs

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OBSWebSocketClient @Inject constructor() {

    // ========================================================================
    // Sealed Interfaces
    // ========================================================================

    sealed interface ConnectionState {
        data object Connected : ConnectionState
        data object Connecting : ConnectionState
        data object Disconnected : ConnectionState
        data class Error(val message: String) : ConnectionState
    }

    sealed interface StreamState {
        data object Inactive : StreamState
        data object Starting : StreamState
        data object Active : StreamState
        data object Stopping : StreamState
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    data class OBSConfig(
        val host: String,
        val port: Int = 4455,
        val password: String? = null,
    )

    // ========================================================================
    // Private Properties
    // ========================================================================

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Inactive)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    // ========================================================================
    // Public Methods
    // ========================================================================

    fun connect(config: OBSConfig) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected
        ) {
            Timber.w("Already connected or connecting.")
            return
        }

        scope.launch {
            try {
                _connectionState.value = ConnectionState.Connecting

                val url = "ws://${config.host}:${config.port}"
                val request = Request.Builder().url(url).build()

                webSocket = client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Timber.i("WebSocket opened")
                            scope.launch {
                                // OBS v5 Protocol: Send Identify message
                                val identifyMsg = mapOf(
                                    "op" to 1,
                                    "d" to mapOf(
                                        "rpcVersion" to 1,
                                        "authentication" to config.password,
                                        "eventSubscriptions" to 33, // Subscribe to events
                                    ),
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
                            _streamState.value = StreamState.Inactive
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            Timber.e(t, "WebSocket error")
                            _connectionState.value = ConnectionState.Error(
                                "Verbindung fehlgeschlagen: ${t.message}",
                            )
                        }
                    },
                )
            } 
//            catch (e: Exception) {
//                Timber.e(e, "Failed to connect to OBS")
//                _connectionState.value = ConnectionState.Error(
//                    "Initialisierung fehlgeschlagen: ${e.message}",
//                )
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                webSocket?.close(1000, "Client disconnect")
                webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                _streamState.value = StreamState.Inactive
                Timber.i("Disconnected from OBS")
            } 
//            catch (e: Exception) {
//                Timber.e(e, "Error during disconnect")
//            }
        }
    }

    fun startStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.Starting
                sendRequest("StartStream")
                delay(1000) // Wait for confirmation
                _streamState.value = StreamState.Active
            } 
//            catch (e: Exception) {
//                Timber.e(e, "Failed to start stream")
//                _streamState.value = StreamState.Inactive
//            }
        }
    }

    fun stopStream() {
        scope.launch {
            try {
                _streamState.value = StreamState.Stopping
                sendRequest("StopStream")
                delay(1000) // Wait for confirmation
                _streamState.value = StreamState.Inactive
            } 
//            catch (e: Exception) {
//                Timber.e(e, "Failed to stop stream")
//            }
        }
    }

    fun cleanup() {
        scope.cancel()
        webSocket?.close(1000, "Client cleanup")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _streamState.value = StreamState.Inactive
    }

    // ========================================================================
    // Private Methods
    // ========================================================================

    private fun handleMessage(text: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val message = gson.fromJson(text, Map::class.java) as Map<String, Any>
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
                else -> {
                    Timber.w("Unknown opcode: $op")
                }
            }
        } 
//        catch (e: Exception) {
//            Timber.e(e, "Error handling message")
//        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleEvent(message: Map<String, Any>) {
        try {
            val eventData = message["d"] as? Map<String, Any>
            val eventType = eventData?.get("eventType") as? String

            when (eventType) {
                "StreamStateChanged" -> {
                    val outputData = eventData["eventData"] as? Map<String, Any>
                    val active = outputData?.get("outputActive") as? Boolean
                    _streamState.value = if (active == true) {
                        StreamState.Active
                    } else {
                        StreamState.Inactive
                    }
                    Timber.d("Stream state changed: active=$active")
                }
                else -> {
                    Timber.d("Unhandled event: $eventType")
                }
            }
        } 
//        catch (e: Exception) {
//            Timber.e(e, "Error handling event")
//        }
    }

    private fun handleResponse(message: Map<String, Any>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val responseData = message["d"] as? Map<String, Any>
            val requestType = responseData?.get("requestType") as? String
            val requestStatus = responseData?.get("requestStatus") as? Map<String, Any>
            val result = requestStatus?.get("result") as? Boolean

            Timber.d("Response: requestType=$requestType, result=$result")
        } 
//        catch (e: Exception) {
//            Timber.e(e, "Error handling response")
//        }
    }

    private fun sendRequest(requestType: String, requestData: Map<String, Any>? = null) {
        try {
            val request = mutableMapOf<String, Any>(
                "op" to 6,
                "d" to mutableMapOf<String, Any>(
                    "requestType" to requestType,
                    "requestId" to UUID.randomUUID().toString(),
                ),
            )

            requestData?.let {
                @Suppress("UNCHECKED_CAST")
                (request["d"] as MutableMap<String, Any>)["requestData"] = it
            }

            val json = gson.toJson(request)
            webSocket?.send(json)
            Timber.d("Sent request: $requestType")
        } 
//        catch (e: Exception) {
//            Timber.e(e, "Error sending request: $requestType")
//        }
    }
}
