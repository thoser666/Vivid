package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.InlineEmote
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Liest den öffentlichen Twitch-Chat über **EventSub WebSocket**
 * (`channel.chat.message`, Version 1) und liefert die Nachrichten als
 * [ChatMessage] — der IRC-Nachfolger für Chat-Overlay und KI-Bot.
 *
 * Im Gegensatz zum alten anonymen IRC-Leser (`justinfan`) braucht der Reader
 * einen **authentifizierten User-Token** (Bot-Konto) mit dem Scope
 * `user:read:chat` sowie die Twitch-App-Client-ID. Die Subscription-Condition
 * setzt die User-ID des Kanals (`broadcaster_user_id`) und die des Bots
 * (`user_id`); Twitch liefert damit alle Nachrichten des Kanals — auch die
 * des Bots selbst (im Overlay gewünscht, in der Engine gefiltert).
 *
 * Ablauf: WebSocket öffnen → `session_welcome` (Session-ID) → Subscribe per
 * Helix-API → `notification`-Events werden als Chat-Nachrichten weitergereicht.
 * Beim `session_reconnect` übernimmt die neue Session die Abos automatisch,
 * bei hartem Verbindungsverlust wird nach dem Reconnect neu subscribt.
 */
@Singleton
class TwitchChatEventSubReader @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val socketFactory: EventSubSocketFactory,
    private val whisperClient: TwitchWhisperClient,
    private val http: HttpClient,
) {
    private val _state = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
    val state: StateFlow<ChatConnectionState> = _state

    private val _messages = MutableSharedFlow<ChatMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: Flow<ChatMessage> = _messages.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private var socketJob: Job? = null
    private var active = false
    private var config: TwitchEventSubConfig? = null

    /** Abo für diese Session aktiv? Wird bei hartem Reconnect zurückgesetzt. */
    private var subscribed = false

    /** Graceful-Reconnect (session_reconnect): Abos wandern automatisch mit. */
    private var reconnecting = false
    private var reconnectUrl: String? = null

    fun start(config: TwitchEventSubConfig) {
        stop()
        if (!config.isConfigured) return
        this.config = config
        active = true
        subscribed = false
        reconnecting = false
        reconnectUrl = null
        socketJob = scope.launch { runLoop() }
    }

    fun stop() {
        active = false
        socketJob?.cancel()
        socketJob = null
        config = null
        subscribed = false
        reconnecting = false
        reconnectUrl = null
        _state.value = ChatConnectionState.Disconnected
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (active && currentCoroutineContext().isActive) {
            _state.value = ChatConnectionState.Connecting
            try {
                socketFactory.create().use { conn ->
                    conn.connect(reconnectUrl ?: DEFAULT_EVENTSUB_URL)
                    reconnectUrl = null
                    attempt = 0
                    conn.incoming.collect { line ->
                        // Einzelne fehlerhafte Nachrichten (z. B. JSON-Dekodierung)
                        // brechen die Verbindung nicht ab — der Fehler wird
                        // verworfen und die nächste Nachricht verarbeitet.
                        runCatching { handle(config ?: return@collect, line) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Verbindung abgebrochen oder Handshake fehlgeschlagen → Reconnect.
            } finally {
                // Nur bei hartem Abbruch neu subscriben — beim graceful
                // Reconnect übernimmt die neue Session die Abos automatisch.
                if (!reconnecting) subscribed = false
            }
            if (!active) break
            delay(backoffMillis(attempt++))
        }
        _state.value = ChatConnectionState.Disconnected
    }

    private suspend fun handle(cfg: TwitchEventSubConfig, line: String) {
        val envelope = json.decodeFromString<ChatEventSubEnvelope>(line)
        when (envelope.metadata.message_type) {
            "session_welcome" -> {
                reconnecting = false
                val sessionId = envelope.payload.session?.id
                if (sessionId != null && !subscribed) {
                    subscribe(cfg, sessionId)
                    if (subscribed) _state.value = ChatConnectionState.Connected(cfg.channel)
                }
            }
            "session_reconnect" -> {
                // Die neue Session (reconnect_url) übernimmt die Subscriptions
                // automatisch — subscribed bleibt true, kein erneuter Subscribe.
                reconnectUrl = envelope.payload.session?.reconnect_url
                if (reconnectUrl != null) reconnecting = true
            }
            "notification" -> envelope.payload.event?.let { event ->
                val text = event.message.text.trim()
                if (text.isNotEmpty()) {
                    _messages.tryEmit(toChatMessage(cfg, event))
                }
            }
            // session_keepalive / revocation / ... → ignorieren.
            else -> Unit
        }
    }

    /**
     * Baut aus dem `channel.chat.message`-Event eine [ChatMessage] (öffentlich,
     * `isWhisper = false`). Badges kommen als `set_id`/`id`-Objekte und werden
     * auf das IRC-Format `"set_id/id"` abgebildet (z. B. `broadcaster/1`) —
     * damit bleibt die Owner-Erkennung der Engine unverändert. Emotes werden
     * aus den Message-Fragments auf das IRC-`emotesTag`-Format gemappt.
     */
    private fun toChatMessage(cfg: TwitchEventSubConfig, event: ChatMessageEvent): ChatMessage {
        val badges = event.badges.map { "${it.set_id}/${it.id}" }
        val login = event.chatter_user_login.trim().lowercase()
        val emotesTag = buildEmotesTag(event.message.fragments)
        return ChatMessage(
            id = event.message_id,
            channel = event.broadcaster_user_login.ifBlank { cfg.channel }.lowercase(),
            userId = event.chatter_user_id,
            userLogin = login,
            displayName = event.chatter_user_name.ifBlank { login },
            color = event.color?.takeIf { it.isNotBlank() },
            text = event.message.text.trim(),
            badges = badges,
            emotesTag = emotesTag,
            timestamp = parseTimestamp(event.message_timestamp),
            isModerator = event.badges.any { it.set_id == "moderator" },
            isSubscriber = event.badges.any { it.set_id == "subscriber" },
            isBroadcaster = event.badges.any { it.set_id == "broadcaster" },
            isWhisper = false,
            inlineEmotes = InlineEmote.parseFromEmotesTag(emotesTag),
        )
    }

    /** Emote-Fragments → IRC-`emotesTag`-Format (`id:start-end,start2-end2/id2:…`). */
    private fun buildEmotesTag(fragments: List<ChatEventFragment>): String {
        var offset = 0
        val byId = LinkedHashMap<String, MutableList<String>>()
        for (fragment in fragments) {
            val length = fragment.text.length
            if (fragment.type == "emote" && fragment.emote != null && length > 0) {
                byId.getOrPut(fragment.emote.id) { mutableListOf() }
                    .add("$offset-${offset + length - 1}")
            }
            offset += length
        }
        return byId.entries.joinToString("/") { (id, ranges) -> "$id:${ranges.joinToString(",")}" }
    }

    /**
     * `message_timestamp` (ISO-8601, z. B. `2023-08-22T20:06:02.029203596Z`) →
     * Epochen-Millis. Bewusst `SimpleDateFormat` statt `java.time`: minSdk 24
     * ohne Core-Library-Desugaring (java.time bräuchte API 26).
     */
    private fun parseTimestamp(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        return runCatching {
            val body = iso.trim().removeSuffix("Z").substringBefore('.')
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(body)?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    /** Subscribt `channel.chat.message` für den Kanal (Broadcaster) als dieser Session. */
    private suspend fun subscribe(cfg: TwitchEventSubConfig, sessionId: String) {
        val auth = TwitchWhisperConfig(
            botLogin = cfg.botLogin,
            oauthToken = cfg.oauthToken,
            clientId = cfg.clientId,
        )
        val botUserId = whisperClient.resolveUserId(auth, cfg.botLogin) ?: return
        val broadcasterUserId = whisperClient.resolveUserId(auth, cfg.channel) ?: return
        val response = http.post("$HELIX_API/eventsub/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer ${cfg.oauthToken.trim().removePrefix("oauth:")}")
            header(CLIENT_ID_HEADER, cfg.clientId)
            contentType(ContentType.Application.Json)
            setBody(
                EventSubSubscribeRequest(
                    type = CHAT_SUBSCRIPTION_TYPE,
                    version = CHAT_SUBSCRIPTION_VERSION,
                    condition = ChatEventSubCondition(
                        broadcaster_user_id = broadcasterUserId,
                        user_id = botUserId,
                    ),
                    transport = EventSubTransport(method = "websocket", session_id = sessionId),
                ),
            )
        }
        // Nur bei Erfolg als subscribed markieren — sonst beim nächsten
        // session_welcome (nach Reconnect) erneut versuchen. Bei 403
        // („subscription missing proper authorization“, z. B. Scope
        // user:read:chat fehlt) bleibt der Reader im Reconnect-Zustand.
        subscribed = response.status.isSuccess()
    }

    private fun backoffMillis(attempt: Int): Long {
        val factor = 1L shl attempt.coerceAtMost(5)
        return (1_000L * factor).coerceAtMost(30_000L)
    }

    companion object {
        internal const val DEFAULT_EVENTSUB_URL = "wss://eventsub.wss.twitch.tv/ws"
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"
        private const val CHAT_SUBSCRIPTION_TYPE = "channel.chat.message"
        private const val CHAT_SUBSCRIPTION_VERSION = "1"
    }
}

// --- EventSub-Envelope für channel.chat.message (Envelope-Modelle sind lokal,
//     weil das Event eine komplett andere Form hat als beim Whisper-Client) ---

@Serializable
internal data class ChatEventSubEnvelope(
    val metadata: EventSubMetadata = EventSubMetadata(),
    val payload: ChatEventSubPayload = ChatEventSubPayload(),
)

@Serializable
internal data class ChatEventSubPayload(
    val session: EventSubSession? = null,
    val event: ChatMessageEvent? = null,
)

@Serializable
internal data class ChatMessageEvent(
    val broadcaster_user_id: String = "",
    val broadcaster_user_login: String = "",
    val broadcaster_user_name: String = "",
    val chatter_user_id: String = "",
    val chatter_user_login: String = "",
    val chatter_user_name: String = "",
    val message_id: String = "",
    val message: ChatEventMessage = ChatEventMessage(),
    val color: String? = null,
    val badges: List<ChatEventBadge> = emptyList(),
    val message_type: String = "",
    val message_timestamp: String = "",
)

@Serializable
internal data class ChatEventMessage(
    val text: String = "",
    val fragments: List<ChatEventFragment> = emptyList(),
)

@Serializable
internal data class ChatEventFragment(
    val type: String = "",
    val text: String = "",
    val emote: ChatEventEmote? = null,
)

@Serializable
internal data class ChatEventEmote(
    val id: String = "",
    val emote_set_id: String = "",
    val owner_id: String = "",
)

@Serializable
internal data class ChatEventBadge(
    val set_id: String = "",
    val id: String = "",
    val info: String = "",
)

// --- Helix-Subscribe-Condition für channel.chat.message ---

@Serializable
internal data class ChatEventSubCondition(
    val broadcaster_user_id: String,
    val user_id: String,
)
