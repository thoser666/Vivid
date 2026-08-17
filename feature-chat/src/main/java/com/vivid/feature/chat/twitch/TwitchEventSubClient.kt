package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.ChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Konfiguration für den EventSub-Empfang (Twitch-Whisper). Voraussetzungen
 * sind dieselben wie beim Senden: Bot-Token mit Scope `user:manage:whispers`
 * (bzw. `user:read:whispers`) und die Twitch-App-Client-ID.
 */
data class TwitchEventSubConfig(
    val botLogin: String,
    val oauthToken: String,
    val clientId: String,
    val channel: String,
) {
    val isConfigured: Boolean
        get() = botLogin.isNotBlank() && oauthToken.isNotBlank() && clientId.isNotBlank()
}

/**
 * Empfängt Twitch-Whispers über **EventSub WebSocket** (`user.whisper.message`)
 * und liefert sie als [ChatMessage] mit `isWhisper = true` — damit kann der
 * Streamer dem Bot auch privat Befehle schicken (z. B. `!diag` vom Zweitaccount).
 *
 * Ablauf: WebSocket öffnen → `session_welcome` (Session-ID) → Subscribe per
 * Helix-API → `notification`-Events werden als Whispers weitergereicht. Beim
 * `session_reconnect` übernimmt die neue Session die Abos automatisch (Twitch),
 * bei hartem Verbindungsverlust wird nach dem Reconnect neu subscribt.
 */
@Singleton
class TwitchEventSubClient @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val socketFactory: EventSubSocketFactory,
    private val whisperClient: TwitchWhisperClient,
    private val http: HttpClient,
) {
    private val _whispers = MutableSharedFlow<ChatMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val whispers: Flow<ChatMessage> = _whispers.asSharedFlow()

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
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (active && currentCoroutineContext().isActive) {
            try {
                socketFactory.create().use { conn ->
                    conn.connect(reconnectUrl ?: DEFAULT_EVENTSUB_URL)
                    reconnectUrl = null
                    attempt = 0
                    conn.incoming.collect { line ->
                        // Einzelne fehlerhafte Nachrichten (z. B. JSON-Dekodierung)
                        // brechen die Verbindung nicht ab — der Fehler wird geloggt
                        // und die nächste Nachricht verarbeitet.
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
    }

    private suspend fun handle(cfg: TwitchEventSubConfig, line: String) {
        val envelope = json.decodeFromString<EventSubEnvelope>(line)
        when (envelope.metadata.message_type) {
            "session_welcome" -> {
                reconnecting = false
                val sessionId = envelope.payload.session?.id
                if (sessionId != null && !subscribed) {
                    subscribe(cfg, sessionId)
                }
            }
            "session_reconnect" -> {
                // Die neue Session (reconnect_url) übernimmt die Subscriptions
                // automatisch — subscribed bleibt true, kein erneuter Subscribe.
                reconnectUrl = envelope.payload.session?.reconnect_url
                if (reconnectUrl != null) reconnecting = true
            }
            "notification" -> envelope.payload.event?.let { event ->
                val text = event.whisper?.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    _whispers.tryEmit(toChatMessage(cfg, event, text))
                }
            }
            // session_keepalive / revocation / ... → ignorieren.
            else -> Unit
        }
    }

    /** Baut aus dem EventSub-Whisper-Event eine ChatMessage (privat, isWhisper). */
    private fun toChatMessage(cfg: TwitchEventSubConfig, event: WhisperEvent, text: String): ChatMessage {
        val login = event.from_user_login.trim().lowercase()
        return ChatMessage(
            id = event.whisper_id,
            channel = cfg.channel,
            userId = event.from_user_id,
            userLogin = login,
            displayName = event.from_user_name.ifBlank { login },
            color = null,
            text = text,
            badges = emptyList(),
            emotesTag = "",
            timestamp = System.currentTimeMillis(),
            isModerator = false,
            isSubscriber = false,
            // Whisper vom Kanal-Inhaber (Login == Kanal) gilt als Broadcaster —
            // Twitch verifiziert die Absender-Identität; nur der Inhaber des
            // Logins kann von diesem Account whisperm.
            isBroadcaster = login == cfg.channel.trim().lowercase(),
            isWhisper = true,
        )
    }

    /** Subscribt `user.whisper.message` für die Bot-User-ID auf dieser Session. */
    private suspend fun subscribe(cfg: TwitchEventSubConfig, sessionId: String) {
        val botUserId = whisperClient.resolveUserId(
            TwitchWhisperConfig(botLogin = cfg.botLogin, oauthToken = cfg.oauthToken, clientId = cfg.clientId),
            cfg.botLogin,
        )
        if (botUserId == null) return
        val response = http.post("$HELIX_API/eventsub/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer ${cfg.oauthToken.trim().removePrefix("oauth:")}")
            header(CLIENT_ID_HEADER, cfg.clientId)
            contentType(ContentType.Application.Json)
            setBody(
                EventSubSubscribeRequest(
                    type = WHISPER_SUBSCRIPTION_TYPE,
                    version = WHISPER_SUBSCRIPTION_VERSION,
                    condition = EventSubCondition(user_id = botUserId),
                    transport = EventSubTransport(method = "websocket", session_id = sessionId),
                ),
            )
        }
        // Nur bei Erfolg als subscribed markieren — sonst beim nächsten
        // session_welcome (nach Reconnect) erneut versuchen.
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
        private const val WHISPER_SUBSCRIPTION_TYPE = "user.whisper.message"
        private const val WHISPER_SUBSCRIPTION_VERSION = "1"
    }
}

// --- EventSub-WebSocket-Nachrichten (kotlinx.serialization) ---

@Serializable
internal data class EventSubEnvelope(
    val metadata: EventSubMetadata = EventSubMetadata(),
    val payload: EventSubPayload = EventSubPayload(),
)

@Serializable
internal data class EventSubMetadata(
    val message_type: String = "",
    val message_id: String = "",
)

@Serializable
internal data class EventSubPayload(
    val session: EventSubSession? = null,
    val event: WhisperEvent? = null,
)

@Serializable
internal data class EventSubSession(
    val id: String = "",
    val status: String = "",
    val reconnect_url: String? = null,
)

@Serializable
internal data class WhisperEvent(
    val from_user_id: String = "",
    val from_user_login: String = "",
    val from_user_name: String = "",
    val to_user_id: String = "",
    val whisper_id: String = "",
    val whisper: WhisperContent? = null,
)

@Serializable
internal data class WhisperContent(val text: String = "")

// --- Helix-Subscribe-Request ---

@Serializable
internal data class EventSubSubscribeRequest(
    val type: String,
    val version: String,
    val condition: EventSubCondition,
    val transport: EventSubTransport,
)

@Serializable
internal data class EventSubCondition(val user_id: String)

@Serializable
internal data class EventSubTransport(
    val method: String,
    val session_id: String,
)
