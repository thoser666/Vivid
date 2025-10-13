package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.Request
import com.vivid.core.network.obs.requests.RequestBatch
import com.vivid.core.network.obs.requests.RequestType
import com.vivid.core.network.obs.security.AuthenticationChallenge
import com.vivid.core.network.obs.security.AuthenticationResponse
import com.vivid.core.network.obs.security.generateAuthenticationString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
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

@Singleton // Sorgt dafür, dass es nur eine Instanz in der App gibt
class OBSWebSocketClient @Inject constructor( // Fügt die Klasse dem Hilt-Graphen hinzu
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private val requestIdCounter = AtomicInteger(1)

    // Private, veränderbare Version für den internen Gebrauch
    private val _isConnected = MutableStateFlow(false)

    // Öffentliche, nur lesbare Version für externe Klassen
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow() // <-- DAS IST DIE WICHTIGE ÄNDERUNG

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("WebSocket connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Timber.d("Received message: $text")
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("WebSocket closing: $reason")
            _isConnected.value = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "WebSocket failure")
            _isConnected.value = false
        }
    }

    fun connect(password: String, ip: String, port: Int) {
        val request = okhttp3.Request.Builder()
            .url("ws://$ip:$port")
            .build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
    }

    private fun handleMessage(message: String) {
        try {
            val opCode = gson.fromJson(message, Map::class.java)["op"] as? Double
            when (opCode?.toInt()) {
                0 -> handleHello(message)
                2 -> handleIdentified()
                // Handle other opcodes
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing message")
        }
    }

    private fun handleHello(message: String) {
        val challenge = gson.fromJson(message, AuthenticationChallenge::class.java)
        challenge.d.authentication?.let {
            val authString = generateAuthenticationString("YOUR_PASSWORD", it.salt, it.challenge)
            val response = AuthenticationResponse(
                op = 1,
                d = AuthenticationResponse.Data(
                    rpcVersion = 1,
                    authentication = authString,
                    eventSubscriptions = 0
                )
            )
            send(response)
        }
    }

    private fun handleIdentified() {
        _isConnected.value = true
        Timber.d("Successfully identified with OBS")
        // Now you can send other requests
        sendRequest(GetVersion(), RequestType.GetVersion)
    }

    fun sendRequest(request: Request, requestType: RequestType) {
        val requestWithId = request.toRequestWithId(requestIdCounter.getAndIncrement().toString())
        send(requestWithId)
    }

    fun sendRequestBatch(requests: List<Request>, haltOnFailure: Boolean, executionType: Int) {
        val batch = RequestBatch(
            requests = requests.map { it.toRequestWithId(requestIdCounter.getAndIncrement().toString()) },
            haltOnFailure = haltOnFailure,
            executionType = executionType
        )
        val requestWithId = batch.toRequestWithId(requestIdCounter.getAndIncrement().toString())
        send(requestWithId)
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
    private fun send(data: Any) {
        try {
            val json = gson.toJson(data)
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
            Timber.d("Sent message: $json")
        } catch (e: Exception) {
            Timber.e(e, "Error sending message")
        }
            Timber.d("Sent request: $requestType")
        }
//        catch (e: Exception) {
//            Timber.e(e, "Error sending request: $requestType")
//        }
    }
}
