package com.vivid.core.network.obs

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OBSWebSocketClient @Inject constructor() {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val requestId = AtomicInteger(1)

    // State flows for UI
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.STOPPED)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    // Connection configuration
    data class OBSConfig(
        val host: String = "localhost",
        val port: Int = 4455,
        val password: String? = null,
    )

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    enum class StreamState {
        STOPPED, STARTING, STREAMING, STOPPING
    }

    // Connect to OBS WebSocket
    fun connect(config: OBSConfig) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        _connectionState.value = ConnectionState.CONNECTING
        _errorState.value = null

        val url = "ws://${config.host}:${config.port}"
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // OBS WebSocket v5 sends Hello message on connection
                    // We'll handle authentication in onMessage
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text, config.password)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = ConnectionState.ERROR
                    _errorState.value = "Connection failed: ${t.message}"
                }
            },
        )
    }

    // Disconnect from OBS
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // Start streaming
    fun startStream() {
        if (_streamState.value == StreamState.STREAMING) return

        _streamState.value = StreamState.STARTING
        sendRequest("StartStream")
    }

    // Stop streaming
    fun stopStream() {
        if (_streamState.value == StreamState.STOPPED) return

        _streamState.value = StreamState.STOPPING
        sendRequest("StopStream")
    }

    // Get current stream status
    fun getStreamStatus() {
        sendRequest("GetStreamStatus")
    }

    // Handle incoming messages from OBS
    private fun handleMessage(message: String, password: String?) {
        try {
            val json = JSONObject(message)

            when {
                // Hello message - start authentication
                json.has("d") && json.getJSONObject("d").has("rpcVersion") -> {
                    handleHello(json, password)
                }

                // Response to our requests
                json.has("d") && json.has("op") && json.getInt("op") == 7 -> {
                    handleResponse(json)
                }

                // Events from OBS
                json.has("d") && json.has("op") && json.getInt("op") == 5 -> {
                    handleEvent(json)
                }
            }
        } catch (e: Exception) {
            _errorState.value = "Message parsing error: ${e.message}"
        }
    }

    // Handle Hello message and authenticate
    private fun handleHello(json: JSONObject, password: String?) {
        val d = json.getJSONObject("d")

        if (password != null && d.has("authentication")) {
            val auth = d.getJSONObject("authentication")
            val challenge = auth.getString("challenge")
            val salt = auth.getString("salt")

            // Generate authentication response
            val secret = generateSecret(password, salt)
            val authResponse = generateAuthResponse(secret, challenge)

            // Send Identify with authentication
            sendIdentify(authResponse)
        } else {
            // No password required
            sendIdentify(null)
        }
    }

    // Handle responses from OBS
    private fun handleResponse(json: JSONObject) {
        val d = json.getJSONObject("d")
        val requestType = d.optString("requestType", "")
        val requestStatus = d.getJSONObject("requestStatus")
        val result = requestStatus.getBoolean("result")

        if (!result) {
            _errorState.value = "Request failed: ${requestStatus.optString("comment", "Unknown error")}"
            return
        }

        when (requestType) {
            "Identify" -> {
                _connectionState.value = ConnectionState.CONNECTED
                // Get initial stream status
                getStreamStatus()
            }

            "StartStream" -> {
                _streamState.value = StreamState.STREAMING
            }

            "StopStream" -> {
                _streamState.value = StreamState.STOPPED
            }

            "GetStreamStatus" -> {
                val responseData = d.optJSONObject("responseData")
                val isStreaming = responseData?.optBoolean("outputActive", false) ?: false
                _streamState.value = if (isStreaming) StreamState.STREAMING else StreamState.STOPPED
            }
        }
    }

    // Handle events from OBS
    private fun handleEvent(json: JSONObject) {
        val d = json.getJSONObject("d")
        val eventType = d.optString("eventType", "")

        when (eventType) {
            "StreamStateChanged" -> {
                val eventData = d.getJSONObject("eventData")
                val isActive = eventData.getBoolean("outputActive")
                _streamState.value = if (isActive) StreamState.STREAMING else StreamState.STOPPED
            }
        }
    }

    // Send Identify message
    private fun sendIdentify(authentication: String?) {
        val identify = JSONObject().apply {
            put("op", 1)
            put(
                "d",
                JSONObject().apply {
                    put("rpcVersion", 1)
                    if (authentication != null) {
                        put("authentication", authentication)
                    }
                    put("eventSubscriptions", 33) // Subscribe to stream events
                },
            )
        }
        webSocket?.send(identify.toString())
    }

    // Send request to OBS
    private fun sendRequest(requestType: String, requestData: JSONObject? = null) {
        val request = JSONObject().apply {
            put("op", 6)
            put(
                "d",
                JSONObject().apply {
                    put("requestType", requestType)
                    put("requestId", requestId.getAndIncrement().toString())
                    if (requestData != null) {
                        put("requestData", requestData)
                    }
                },
            )
        }
        webSocket?.send(request.toString())
    }

    // Generate secret for authentication
    private fun generateSecret(password: String, salt: String): String {
        val combined = password + salt
        return sha256(combined)
    }

    // Generate authentication response
    private fun generateAuthResponse(secret: String, challenge: String): String {
        val combined = secret + challenge
        return sha256(combined)
    }

    // SHA256 hash function
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
}
