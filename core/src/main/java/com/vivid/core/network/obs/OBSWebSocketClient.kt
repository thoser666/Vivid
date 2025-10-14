package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.obs.requests.GetVersion
import com.vivid.core.network.obs.requests.Request
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
class OBSWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private var webSocket: WebSocket? = null
    private val requestIdCounter = AtomicInteger(1)

    // Variable zum Speichern des Passworts
    private var obsPassword = ""

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
        // Passwort in der Klassenvariable speichern
        this.obsPassword = password

        val request = okhttp3.Request.Builder()
            .url("ws://$ip:$port")
            .build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        this.obsPassword = "" // Passwort beim Trennen zurücksetzen
    }

    private fun handleMessage(message: String) {
        try {
            val opCode = gson.fromJson(message, Map::class.java)["op"] as? Double
            when (opCode?.toInt()) {
                0 -> handleHello(message)
                2 -> handleIdentified()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing message")
        }
    }

    private fun handleHello(message: String) {
        // Das gespeicherte Passwort verwenden
        if (this.obsPassword.isBlank()) {
            Timber.e("OBS Password is not set, cannot authenticate.")
            disconnect()
            return
        }

        val challenge = gson.fromJson(message, AuthenticationChallenge::class.java)
        challenge.d.authentication?.let {
            val authString = generateAuthenticationString(this.obsPassword, it.salt, it.challenge)
            val response = AuthenticationResponse(
                op = 1,
                d = AuthenticationResponse.Data(
                    rpcVersion = challenge.d.rpcVersion,
                    authentication = authString,
                    eventSubscriptions = 0,
                ),
            )
            send(response)
        }
    }

    private fun handleIdentified() {
        _isConnected.value = true
        Timber.d("Successfully identified with OBS")
        sendRequest(GetVersion(), RequestType.GetVersion)
    }

    fun sendRequest(request: Request, requestType: RequestType) {
        // requestType wird jetzt korrekt an die Helfer-Funktion übergeben
        val requestWithId = request.toRequestWithId(
            requestId = requestIdCounter.getAndIncrement().toString(),
            requestType = requestType
        )
        send(requestWithId)
    }

    private fun send(data: Any) {
        try {
            val json = gson.toJson(data)
            webSocket?.send(json)
            Timber.d("Sent message: $json")
        } catch (e: Exception) {
            Timber.e(e, "Error sending message")
        }
    }
}
