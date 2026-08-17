package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.irc.IrcMessage
import com.vivid.feature.chat.irc.IrcMessageParser
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class TwitchChatClient @Inject constructor(
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

    private var connectJob: Job? = null
    private var active = false

    fun start(channel: String) {
        stop()
        active = true
        connectJob = scope.launch { runLoop(channel) }
    }

    fun stop() {
        active = false
        connectJob?.cancel()
        connectJob = null
        _state.value = ChatConnectionState.Disconnected
    }

    private suspend fun runLoop(channel: String) {
        var attempt = 0
        while (active && currentCoroutineContext().isActive) {
            _state.value = ChatConnectionState.Connecting
            try {
                connectionFactory.create().use { connection ->
                    connection.connect()
                    writeHandshake(connection, channel)
                    attempt = 0
                    _state.value = ChatConnectionState.Connected(channel)
                    connection.incoming.collect { line ->
                        parser.parse(line)?.let { handle(connection, it, channel) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connection dropped or handshake failed; reconnect below.
            }
            if (!active) break
            delay(backoffMillis(attempt++))
        }
        _state.value = ChatConnectionState.Disconnected
    }

    private fun writeHandshake(connection: IrcConnection, channel: String) {
        connection.write("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
        connection.write("PASS SCHMOOPIIE")
        connection.write("NICK ${anonymousNick()}")
        connection.write("JOIN #$channel")
    }

    private fun handle(connection: IrcConnection, message: IrcMessage, channel: String) {
        when (message.command) {
            "PING" -> connection.write("PONG :${message.trailing.orEmpty()}")
            "PRIVMSG" -> {
                val chatMessage = toChatMessage(message, channel)
                if (chatMessage.text.isNotEmpty()) {
                    _messages.tryEmit(chatMessage)
                }
            }
        }
    }

    private fun toChatMessage(message: IrcMessage, channel: String): ChatMessage {
        val tags = message.tags
        val login = message.prefix?.substringBefore('!').orEmpty()
        val displayName = tags["display-name"]?.takeIf { it.isNotEmpty() } ?: login.ifEmpty { "unknown" }
        val badges = tags["badges"].orEmpty().split(',').filter { it.isNotEmpty() }
        return ChatMessage(
            id = tags["id"].orEmpty(),
            channel = channel,
            userId = tags["user-id"].orEmpty(),
            userLogin = login,
            displayName = displayName,
            color = tags["color"]?.takeIf { it.isNotEmpty() },
            text = message.trailing.orEmpty(),
            badges = badges,
            emotesTag = tags["emotes"].orEmpty(),
            timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
            isModerator = tags["mod"] == "1",
            isSubscriber = tags["subscriber"] == "1",
            isBroadcaster = BROADCASTER_BADGE in badges,
        )
    }

    private fun backoffMillis(attempt: Int): Long {
        val factor = 1L shl attempt.coerceAtMost(5)
        return (1_000L * factor).coerceAtMost(30_000L)
    }

    private fun anonymousNick(): String = "justinfan${Random.nextInt(10000, 99999)}"

    private companion object {
        const val BROADCASTER_BADGE = "broadcaster/1"
    }
}
