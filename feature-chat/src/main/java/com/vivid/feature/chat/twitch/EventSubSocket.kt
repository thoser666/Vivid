package com.vivid.feature.chat.twitch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

/**
 * Eine Twitch-EventSub-WebSocket-Verbindung (nur Empfang — Subscriptions
 * gehen über die Helix-API per HTTP). Die eingehenden Nachrichten kommen als
 * JSON-Zeilen über [incoming]; der Flow endet, wenn die Verbindung schließt
 * (Fehler oder normales Ende) — der Client verbindet dann neu.
 */
interface EventSubSocket : AutoCloseable {
    suspend fun connect(url: String)
    val incoming: Flow<String>
}

fun interface EventSubSocketFactory {
    fun create(): EventSubSocket
}

class OkHttpEventSubSocketFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : EventSubSocketFactory {
    override fun create(): EventSubSocket = OkHttpEventSubSocket(okHttpClient)
}

class OkHttpEventSubSocket(
    private val okHttpClient: OkHttpClient,
) : EventSubSocket {
    private var webSocket: WebSocket? = null
    private val channel = Channel<String>(Channel.BUFFERED)

    override suspend fun connect(url: String) = withContext(Dispatchers.IO) {
        close()
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    override val incoming: Flow<String> = channel.receiveAsFlow()

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { channel.trySend(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { channel.close() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            runCatching { channel.close(t) }
        }
    }

    override fun close() {
        runCatching { webSocket?.close(1000, null) }
        webSocket = null
        // Idempotent: auch bei mehrfachem close (use{} + Fehlerpfad) sicher.
        runCatching { channel.close() }
    }
}
