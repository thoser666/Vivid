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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Verbindet mit OBS. Standard ist [useTls] = false → `ws://` (OBS Studio
     * liefert ohne TLS-Konfiguration nur Klartext-WebSockets auf Port 4455).
     * Für Remote-Verbindungen mit TLS kann [useTls] auf true gesetzt werden → `wss://`.
     */
    fun connect(password: String, ip: String, port: Int, useTls: Boolean = false) {
        // Das Passwort wird NICHT als Klartext-Feld gespeichert, sondern nur
        // lokal an den Listener dieser Verbindung übergeben.
        val scheme = if (useTls) "wss" else "ws"
        val request = okhttp3.Request.Builder()
            .url("$scheme://$ip:$port")
            .build()

        val connectionPassword = password
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received message: $text")
                handleMessage(text, connectionPassword)
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

        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
    }

    private fun handleMessage(message: String, password: String) {
        try {
            val opCode = gson.fromJson(message, Map::class.java)["op"] as? Double
            when (opCode?.toInt()) {
                0 -> handleHello(message, password)
                2 -> handleIdentified()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing message")
        }
    }

    private fun handleHello(message: String, password: String) {
        if (password.isBlank()) {
            Timber.e("OBS Password is not set, cannot authenticate.")
            disconnect()
            return
        }

        val challenge = gson.fromJson(message, AuthenticationChallenge::class.java)
        challenge.d.authentication?.let {
            val authString = generateAuthenticationString(password, it.salt, it.challenge)
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
        val requestWithId = request.toRequestWithId(
            requestId = requestIdCounter.getAndIncrement().toString(),
            requestType = requestType,
        )
        send(requestWithId)
    }

    private fun send(data: Any) {
        try {
            webSocket?.send(gson.toJson(data))
            // Bewusst ohne Payload loggen: Nachrichten wie die Identify-Antwort
            // enthalten den aus dem Passwort abgeleiteten Auth-String.
            Timber.d("Sent WebSocket message")
        } catch (e: Exception) {
            Timber.e(e, "Error sending message")
        }
    }
}
