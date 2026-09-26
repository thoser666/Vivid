package com.vivid.feature.chat.kick

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.session.ChatReader
import com.vivid.feature.chat.session.ChatSendResult
import com.vivid.feature.chat.session.ChatSender
import com.vivid.feature.chat.session.ChatSessionConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Eine Pusher-WebSocket-Verbindung (Empfang + Protokoll-Frames): die
 * eingehenden Nachrichten kommen als JSON-Zeilen über [incoming]. [send]
 * schickt einen Protokoll-Frame (z. B. `pusher:ping` oder den Channel-
 * Subscribe); `false` bedeutet, dass die Verbindung schon geschlossen ist.
 * Der Flow endet, wenn die Verbindung schließt (Fehler oder normales Ende) —
 * der Reader verbindet dann neu.
 *
 * Analog zu [com.vivid.feature.chat.twitch.EventSubSocket] (Twitch), aber
 * mit Send-Richtung — Pusher braucht den Subscribe-Frame über den Socket.
 */
interface KickSocket : AutoCloseable {
    suspend fun connect(url: String)
    val incoming: Flow<String>
    fun send(text: String): Boolean
}

fun interface KickSocketFactory {
    fun create(): KickSocket
}

class OkHttpKickSocketFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : KickSocketFactory {
    override fun create(): KickSocket = OkHttpKickSocket(okHttpClient)
}

class OkHttpKickSocket(
    private val okHttpClient: OkHttpClient,
) : KickSocket {
    private var webSocket: WebSocket? = null
    private val channel = Channel<String>(Channel.BUFFERED)

    override suspend fun connect(url: String) = withContext(Dispatchers.IO) {
        close()
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    override val incoming: Flow<String> = channel.receiveAsFlow()

    override fun send(text: String): Boolean = webSocket?.send(text) ?: false

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
            runCatching { channel.close() }
        }
    }

    override fun close() {
        runCatching { webSocket?.close(1000, null) }
        webSocket = null
    }
}

/**
 * Kick-Lese-Adapter (Multi-Plattform-Chat, Skizze P2,
 * docs/architecture/multi-platform-chat.md): liest den Chat eines Kanals
 * **anonym** über das Pusher-Protokoll — kein Login, kein Token (bewusst
 * Lesen vor Senden, P4).
 *
 * Protokoll (Community-Stand, drift-tolerant):
 *  1. GET `https://kick.com/api/v2/channels/{slug}` — Kanal-Auflösung:
 *     `chatroom.id` (Zahl) für die Pusher-Subscription.
 *  2. WebSocket `wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679` —
 *     Pusher-App-Key der Kick-Web-App (öffentlich, Community-dokumentiert).
 *     `pusher:connection_established` öffnet die Subscription
 *     `chatrooms.<id>.v2`; `pusher_internal:subscription_succeeded`
 *     bestätigt sie (fehlender Ack → Retry mit Backoff).
 *  3. Chat-Nachrichten als `App\Events\ChatMessageEvent` (Payload-JSON:
 *     `id`, `chatroom_id`, `user { id, username, identity.color }`,
 *     `content`). `pusher:ping` wird mit `pusher:pong` beantwortet.
 *
 * Drift-Strategie (Skizze §8): `ignoreUnknownKeys`, unbekannte Events werden
 * übersprungen, Pflichtfelder nur, wo sie eindeutig sind (`event`-String,
 * `id`, `username`, `content`). Ein Protokoll-Bruch ist maximal ein
 * Kick-Chat-Ausfall — Twitch/Overlay bleiben unberührt, da der Manager je
 * Plattform isoliert.
 *
 * Alerts werden **nicht** überschrieben: der Interface-Default
 * (`emptyFlow()`) gilt — Kick-Gifts (SubscriptionEvent) folgen mit P6.
 * Senden folgt mit P4 ([KickChatSender] antwortet kontrolliert mit `Failed`).
 */
@Singleton
class KickChatReader @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val socketFactory: KickSocketFactory,
    private val http: HttpClient,
) : ChatReader {

    private val _state = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
    override val state: StateFlow<ChatConnectionState> = _state

    private val _messages = MutableSharedFlow<ChatMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private var connectJob: Job? = null
    private var active = false
    private var sessionConfig: ChatSessionConfig.Kick? = null

    /** Startet die Session (plattformneutrales [ChatReader]-Interface, P0). */
    override fun start(config: ChatSessionConfig) {
        val kick = config as? ChatSessionConfig.Kick
            ?: throw IllegalArgumentException(
                "Kick-Reader erwartet ChatSessionConfig.Kick, got ${config::class.simpleName}",
            )
        sessionConfig = kick
        start(kick.channel)
    }

    /** Startet (oder startet neu) den Chat-Reader für den Kanal-Slug. */
    fun start(channel: String) {
        stop()
        if (channel.isBlank()) return
        active = true
        connectJob = scope.launch { runLoop(channel.trim()) }
    }

    /** Beendet die Session (plattformneutrales [ChatReader]-Interface, P0). */
    override fun stop() {
        active = false
        connectJob?.cancel()
        connectJob = null
        sessionConfig = null
        _state.value = ChatConnectionState.Disconnected
    }

    /** Aktive Session-Konfiguration (Diagnose). */
    fun currentConfig(): ChatSessionConfig.Kick? = sessionConfig

    private suspend fun runLoop(channel: String) {
        var backoffMs = INITIAL_BACKOFF_MS
        while (kotlin.coroutines.coroutineContext.isActive && active) {
            try {
                _state.value = ChatConnectionState.Connecting
                val chatroomId = resolveChatroomId(channel)
                if (chatroomId == null) {
                    // Kanal unbekannt / API-Störung → Disconnected, Retry.
                    _state.value = ChatConnectionState.Disconnected
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                val connected = runSocket(channel, chatroomId)
                _state.value = ChatConnectionState.Disconnected
                if (connected) {
                    // Sauberer Socket-Ende-Zyklus: kurze Pause, frischer Start.
                    delay(RETRY_DELAY_MS)
                    backoffMs = INITIAL_BACKOFF_MS
                } else {
                    // Verbindung/Subscription fehlgeschlagen → Backoff.
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Netz-/Protokollfehler: Status zurücksetzen und mit Backoff
                // erneut — der Flow versendet nichts, Twitch bleibt unberührt.
                _state.value = ChatConnectionState.Disconnected
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /** Kanal-Auflösung: Slug → Chatroom-ID (Kick-Public-API v2, anonym). */
    internal suspend fun resolveChatroomId(channel: String): Long? {
        val response = http.get(channelUrl(channel))
        if (!response.status.isSuccess()) return null
        return parseChatroomId(response.bodyAsText())
    }

    /**
     * Socket-Verbindung + Subscribe + Lesen. Liefert `true`, wenn die
     * Subscription bestätigt wurde (dann Ende des Socket-Runs — Retry),
     * `false` bei Verbindungs-/Subscribe-Fehlern (Backoff). Internal für
     * Contract-Tests.
     */
    internal suspend fun runSocket(channel: String, chatroomId: Long): Boolean {
        val socket = socketFactory.create()
        var subscribed = false
        try {
            socket.connect(PUSHER_APP_URL)
            socket.incoming.collect { line ->
                val frame = parsePusherFrame(line) ?: return@collect
                when (frame.event) {
                    PUSHER_CONNECTION_ESTABLISHED ->
                        socket.send(buildSubscribeFrame(chatroomId).toString())
                    PUSHER_SUBSCRIPTION_SUCCEEDED -> {
                        subscribed = true
                        _state.value = ChatConnectionState.Connected(channel)
                    }
                    KICK_CHAT_EVENT -> {
                        mapKickMessage(frame.data, channel, chatroomId)
                            ?.let { _messages.tryEmit(it) }
                    }
                    PUSHER_PING -> socket.send(PUSHER_PONG_FRAME)
                }
            }
            return subscribed
        } finally {
            runCatching { socket.close() }
        }
    }

    internal companion object {
        /** Pusher-App-Key der Kick-Web-App (öffentlich, Community-dokumentiert). */
        const val PUSHER_APP_KEY = "32cbd69e4b950bf97679"
        const val PUSHER_APP_URL = "wss://ws-us2.pusher.com/app/$PUSHER_APP_KEY"
        const val PUSHER_CONNECTION_ESTABLISHED = "pusher:connection_established"
        const val PUSHER_SUBSCRIPTION_SUCCEEDED = "pusher_internal:subscription_succeeded"
        const val PUSHER_PING = "pusher:ping"
        const val PUSHER_PONG_FRAME = "{\"event\":\"pusher:pong\",\"data\":{}}"
        const val KICK_CHAT_EVENT = "App\\Events\\ChatMessageEvent"

        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val RETRY_DELAY_MS = 5_000L

        /** Kanal-Auflösung über die öffentliche Kick-API (v2, anonym). */
        fun channelUrl(channel: String): String =
            "https://kick.com/api/v2/channels/" +
                java.net.URLEncoder.encode(channel, Charsets.UTF_8.name())

        /** Subscribe-Frame für einen Chatroom (v2-Kanal, Message-Events). */
        fun buildSubscribeFrame(chatroomId: Long): JsonObject = buildJsonObject {
            put("event", JsonPrimitive("pusher:subscribe"))
            put(
                "data",
                buildJsonObject {
                    put("auth", JsonPrimitive(""))
                    put("channel", JsonPrimitive("chatrooms.$chatroomId.v2"))
                },
            )
        }
    }
}

/** Pusher-Frame: `event` + beliebig geformtes `data` (drift-tolerant). */
internal data class KickPusherFrame(
    val event: String,
    val data: String?,
)

/**
 * Parst einen Pusher-Frame: `{"event":"…","data":…}`. `data` ist im
 * Pusher-Protokoll üblicherweise ein JSON-**String**, kann drift-tolerant
 * aber auch als Objekt kommen — beides wird als Roh-JSON-Text zurückgegeben.
 * Unbekannte/leere Frames ergeben `null` (Event wird übersprungen).
 */
internal fun parsePusherFrame(line: String): KickPusherFrame? {
    val root = runCatching {
        Json.parseToJsonElement(line).jsonObject
    }.getOrNull() ?: return null
    val event = (root["event"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        ?: return null
    val data = when (val element = root["data"]) {
        null -> null
        is JsonPrimitive -> element.contentOrNull
        else -> element.toString()
    }
    return KickPusherFrame(event = event, data = data)
}

/**
 * Parst die Kanal-Auflösung (api/v2/channels): `chatroom.id` (Zahl; drift-
 * toleranter String-Fallback). `null`, wenn Kanal offline/nicht parsebar.
 */
internal fun parseChatroomId(body: String): Long? {
    val root = runCatching {
        Json.parseToJsonElement(body).jsonObject
    }.getOrNull() ?: return null
    val id = (root["chatroom"] as? JsonObject)?.get("id") as? JsonPrimitive
        ?: return null
    return id.longOrNull ?: id.contentOrNull?.trim()?.toLongOrNull()
}

/**
 * Mappt ein `ChatMessageEvent`-Payload auf eine [ChatMessage]
 * (platform = KICK). Felder (Community-Stand): `id`, `chatroom_id`,
 * `user { id, username, identity { color, badges[] } }`, `content`
 * (Markdown-ähnlich, wird als Rohtext übernommen), optional `created_at`
 * (ISO-8601 UTC). `identity.badges` sind Strings — als Roh-Werte übernommen
 * (Overlay zeigt sie als Fallback-Texte); Moderator/Broadcaster bleiben
 * bewusst `false` (P2: Bot-Aktionen laufen nur auf Twitch).
 */
internal fun mapKickMessage(data: String?, channel: String, chatroomId: Long): ChatMessage? {
    if (data.isNullOrBlank()) return null
    val root = runCatching {
        Json.parseToJsonElement(data).jsonObject
    }.getOrNull() ?: return null
    val id = (root["id"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        ?: return null
    // Drift-Guard: fremder Chatroom (z. B. nach Subscription-Wechsel) → skip.
    val eventChatroomId = (root["chatroom_id"] as? JsonPrimitive)?.longOrNull
    if (eventChatroomId != null && eventChatroomId != chatroomId) return null
    val user = root["user"] as? JsonObject
    val username = (user?.get("username") as? JsonPrimitive)?.contentOrNull
        ?.takeIf(String::isNotBlank) ?: return null
    val userId = (user?.get("id") as? JsonPrimitive)?.longOrNull
    val content = (root["content"] as? JsonPrimitive)?.contentOrNull
    if (content.isNullOrBlank()) return null
    val identity = user?.get("identity") as? JsonObject
    val color = (identity?.get("color") as? JsonPrimitive)?.contentOrNull
        ?.takeIf(String::isNotBlank)
    val badges = (identity?.get("badges") as? JsonArray)
        ?.mapNotNull { badge -> (badge as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    val createdAt = (root["created_at"] as? JsonPrimitive)?.contentOrNull
    return ChatMessage(
        id = id,
        channel = channel,
        userId = userId?.toString() ?: username,
        userLogin = username,
        displayName = username,
        color = color,
        text = content,
        badges = badges,
        emotesTag = "",
        timestamp = parseIsoToEpochMs(createdAt) ?: 0L,
        isModerator = false,
        isSubscriber = false,
        platform = ChatPlatform.KICK,
    )
}

/**
 * ISO-8601 UTC (`2024-08-22T20:06:02Z`, optional `.mmm`/`+hh:mm`-Suffix) →
 * Epoch-ms. Bewusst ohne `java.time` (minSdk 24 ohne Desugaring-Garantie im
 * Modul): festes `YYYY-MM-DDTHH:MM:SS[.fff]`-Schema über Tages-Akkumulation
 * (Howard-Hinnant-Algorithmus). Nicht-parsebar → `null` (Timestamp 0 — das
 * Overlay sortiert nach Eingang, nicht nach Timestamp).
 */
internal fun parseIsoToEpochMs(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val datePart = iso.substringBefore('T')
    val timePart = iso.substringAfter('T', "").substringBefore('Z').substringBefore('+').trim()
    if (timePart.isBlank()) return null
    val date = datePart.split('-')
    val time = timePart.split(':')
    if (date.size != 3 || time.size < 3) return null
    val year = date[0].toIntOrNull() ?: return null
    val month = date[1].toIntOrNull() ?: return null
    val day = date[2].toIntOrNull() ?: return null
    val hour = time[0].toIntOrNull() ?: return null
    val minute = time[1].toIntOrNull() ?: return null
    val secondAndFraction = time[2]
    val second = secondAndFraction.substringBefore('.').toIntOrNull() ?: return null
    val millis = secondAndFraction.substringAfter('.', "")
        .take(3).padEnd(3, '0').toIntOrNull() ?: 0
    if (month !in 1..12 || day !in 1..31 ||
        hour !in 0..23 || minute !in 0..59 || second !in 0..60
    ) {
        return null
    }
    return daysFromCivil(year, month, day) * 86_400_000L +
        hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
}

/** Tage seit Epoch (proleptisch gregorianisch, Howard-Hinnant-Algorithmus). */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = year - (if (month <= 2) 1 else 0)
    val m = month.toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (m + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

/**
 * Platzhalter-Sender für Kick (Skizze P4): Senden braucht die offizielle
 * OAuth 2.1 API (`api.kick.com`) — bis dahin schlägt ein Send-Versuch
 * kontrolliert mit [ChatSendResult.Failed] fehl, statt das Multibinding des
 * Session-Managers zu erzwingen.
 */
@Singleton
class KickChatSender @Inject constructor() : ChatSender {
    override suspend fun send(config: ChatSessionConfig, text: String) =
        ChatSendResult.Failed(
            "Kick-Senden folgt mit P4 (OAuth 2.1, api.kick.com)",
        )
}
