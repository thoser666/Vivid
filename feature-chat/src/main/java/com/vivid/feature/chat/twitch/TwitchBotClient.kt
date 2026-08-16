package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.irc.IrcMessage
import com.vivid.feature.chat.irc.IrcMessageParser
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Authentifizierte Twitch-Chat-Verbindung für den KI-Bot. Handshake mit
 * OAuth-Token (`PASS oauth:<token>`) statt anonymem justinfan-Nick, kann
 * Nachrichten senden (`PRIVMSG`) und liest parallel den Chat ein.
 */
@Singleton
class TwitchBotClient @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val connectionFactory: IrcConnectionFactory,
    private val parser: IrcMessageParser = IrcMessageParser(),
) {
    private val _state = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
    val state: StateFlow<ChatConnectionState> = _state

    private val _messages = MutableSharedFlow<ChatMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<ChatMessage> = _messages

    @Volatile
    private var connection: IrcConnection? = null

    private var connectJob: Job? = null
    private var active = false
    private var channel = ""
    private var login = ""
    private var oauthToken = ""

    fun connect(channel: String, login: String, oauthToken: String) {
        disconnect()
        if (channel.isBlank() || login.isBlank() || oauthToken.isBlank()) return
        active = true
        this.channel = channel.trim().lowercase()
        this.login = login.trim().lowercase()
        this.oauthToken = oauthToken.trim().removePrefix("oauth:")
        connectJob = scope.launch { runLoop() }
    }

    fun disconnect() {
        active = false
        connectJob?.cancel()
        connectJob = null
        connection?.let { runCatching { it.close() } }
        connection = null
        _state.value = ChatConnectionState.Disconnected
    }

    fun sendMessage(text: String) {
        if (!active || channel.isBlank()) return
        val sanitized = text
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .take(500)
        if (sanitized.isEmpty()) return
        connection?.write("PRIVMSG #$channel :$sanitized")
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (active && currentCoroutineContext().isActive) {
            _state.value = ChatConnectionState.Connecting
            try {
                connectionFactory.create().use { conn ->
                    connection = conn
                    conn.connect()
                    writeHandshake(conn)
                    attempt = 0
                    _state.value = ChatConnectionState.Connected(channel)
                    conn.incoming.collect { line ->
                        parser.parse(line)?.let { handle(conn, it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Verbindung abgebrochen oder Handshake fehlgeschlagen → Reconnect.
            } finally {
                connection = null
            }
            if (!active) break
            delay(backoffMillis(attempt++))
        }
        _state.value = ChatConnectionState.Disconnected
    }

    private fun writeHandshake(connection: IrcConnection) {
        connection.write("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
        connection.write("PASS oauth:$oauthToken")
        connection.write("NICK $login")
        connection.write("JOIN #$channel")
    }

    private fun handle(connection: IrcConnection, message: IrcMessage) {
        when (message.command) {
            "PING" -> connection.write("PONG :${message.trailing.orEmpty()}")
            "PRIVMSG" -> {
                val chatMessage = toChatMessage(message)
                if (chatMessage.text.isNotEmpty()) {
                    _messages.tryEmit(chatMessage)
                }
            }
        }
    }

    private fun toChatMessage(message: IrcMessage): ChatMessage {
        val tags = message.tags
        val login = message.prefix?.substringBefore('!').orEmpty()
        val displayName = tags["display-name"]?.takeIf { it.isNotEmpty() } ?: login.ifEmpty { "unknown" }
        return ChatMessage(
            id = tags["id"].orEmpty(),
            channel = channel,
            userId = tags["user-id"].orEmpty(),
            userLogin = login,
            displayName = displayName,
            color = tags["color"]?.takeIf { it.isNotEmpty() },
            text = message.trailing.orEmpty(),
            badges = tags["badges"].orEmpty().split(',').filter { it.isNotEmpty() },
            emotesTag = tags["emotes"].orEmpty(),
            timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
            isModerator = tags["mod"] == "1",
            isSubscriber = tags["subscriber"] == "1",
        )
    }

    private fun backoffMillis(attempt: Int): Long {
        val factor = 1L shl attempt.coerceAtMost(5)
        return (1_000L * factor).coerceAtMost(30_000L)
    }
}
