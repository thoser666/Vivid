package com.vivid.feature.chat.youtube

import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.session.ChatReader
import com.vivid.feature.chat.session.ChatSender
import com.vivid.feature.chat.session.ChatSessionConfig
import com.vivid.feature.chat.di.ChatScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.BufferOverflow

/**
 * YouTube-Lese-Adapter (Multi-Plattform-Chat, Skizze P1,
 * docs/architecture/multi-platform-chat.md): liest den Live-Chat eines
 * Kanals **anonym** über die inoffizielle innertube-API — kein OAuth,
 * keine Quota (bewusst Lesen vor Senden, P4).
 *
 * Protokoll (Community-Stand, drift-tolerant):
 *  1. GET `https://www.youtube.com/channel/{id}/live` — redirectet auf die
 *     Watch-Seite des aktiven Streams; das HTML enthält `var ytInitialData =
 *     {...}`. Daraus extrahiert der balanced-Scanner das JSON, der Deep-Find
 *     das Chat-Continuation-Token (nicht_pfadgebunden — tolerant gegen
 *     Struktur-Umbauten der Seite).
 *  2. POST `youtubei/v1/live_chat/get_live_chat` mit dem Token — Antwort:
 *     `continuationContents.liveChatContinuation` mit `actions[]
 *     (addChatItemAction → liveChatTextMessageRenderer)` und dem nächsten
 *     Continuation-Token inkl. `timeoutMs` (Poll-Intervall der Plattform).
 *  3. Fehlt das Folge-Token, ist der Stream zu Ende → Disconnected.
 *
 * Drift-Strategie (Skizze §8): `ignoreUnknownKeys`, Deep-Find statt fester
 * Pfade, unbekannte Aktionen/Renderer werden übersprungen (Contract-Tests
 * frieren die realen Payload-Formen ein; ein Bruch ist maximal ein
 * YouTube-Chat-Ausfall — Twitch/Overlay bleiben unberührt, da der Manager
 * je Plattform isoliert).
 *
 * Alerts werden **nicht** überschrieben: der Interface-Default
 * (`emptyFlow()`) gilt — YouTube-Alerts (Superchat) folgen mit P6.
 * Senden folgt mit P4 ([YoutubeChatSender] antwortet kontrolliert mit
 * `Failed`).
 */
@Singleton
class YoutubeChatReader @Inject constructor(
    @param:ChatScope private val scope: kotlinx.coroutines.CoroutineScope,
    private val http: HttpClient,
) : ChatReader {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
    override val state: StateFlow<ChatConnectionState> = _state

    private val _messages = MutableSharedFlow<ChatMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private var pollJob: Job? = null
    private var active = false
    private var sessionConfig: ChatSessionConfig.Youtube? = null

    /** Startet die Session (plattformneutrales [ChatReader]-Interface, P0). */
    override fun start(config: ChatSessionConfig) {
        val yt = config as? ChatSessionConfig.Youtube
            ?: throw IllegalArgumentException(
                "YouTube-Reader erwartet ChatSessionConfig.Youtube, got ${config::class.simpleName}",
            )
        sessionConfig = yt
        start(yt.channel)
    }

    /** Startet (oder startet neu) den Chat-Reader für die Kanal-ID (UC…). */
    fun start(channelId: String) {
        stop()
        if (channelId.isBlank()) return
        active = true
        pollJob = scope.launch { runLoop(channelId.trim()) }
    }

    /** Beendet die Session (plattformneutrales [ChatReader]-Interface, P0). */
    override fun stop() {
        active = false
        pollJob?.cancel()
        pollJob = null
        sessionConfig = null
        _state.value = ChatConnectionState.Disconnected
    }

    /** Aktive Session-Konfiguration (Diagnose). */
    fun currentConfig(): ChatSessionConfig.Youtube? = sessionConfig

    private suspend fun runLoop(channelId: String) {
        var backoffMs = INITIAL_BACKOFF_MS
        while (kotlin.coroutines.coroutineContext.isActive && active) {
            try {
                _state.value = ChatConnectionState.Connecting
                val bootstrap = fetchBootstrap(channelId)
                if (bootstrap == null) {
                    // Kanal offline / Seite ohne Live-Chat → Disconnected, Retry.
                    _state.value = ChatConnectionState.Disconnected
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                backoffMs = INITIAL_BACKOFF_MS
                pollChat(channelId, bootstrap.continuation)
                _state.value = ChatConnectionState.Disconnected
                delay(RETRY_DELAY_MS)
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

    /** Holt Kanal-Live-Seite und leitet daraus Chat-Continuation + videoId ab. */
    internal suspend fun fetchBootstrap(channelId: String): YoutubeLiveChatBootstrap? {
        val response = http.get(streamUrl(channelId))
        if (!response.status.isSuccess()) return null
        val html = response.bodyAsText()
        val initialData = extractYtInitialData(html) ?: return null
        return parseLiveChatBootstrap(initialData)
    }

    /** Poll-Schleife über get_live_chat — endet bei Folgetoken-Ende (Stream aus). */
    private suspend fun pollChat(channelId: String, bootstrapContinuation: String) {
        _state.value = ChatConnectionState.Connected(channelId)
        var continuation: String? = bootstrapContinuation
        while (kotlin.coroutines.coroutineContext.isActive && active && continuation != null) {
            val requestBody = buildGetLiveChatBody(continuation, CLIENT_VERSION).toString()
            val response = http.post(getLiveChatUrl()) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                header(X_CLIENT_NAME_HEADER, "1")
                header(X_CLIENT_VERSION_HEADER, CLIENT_VERSION)
            }
            if (response.status == HttpStatusCode.TooManyRequests ||
                response.status.value >= 500
            ) {
                // Rate-Limit/Server-Fehler: kurze Pause, Token behalten.
                delay(RETRY_DELAY_MS)
                continue
            }
            if (!response.status.isSuccess()) return
            val page = parseGetLiveChatResponse(response.bodyAsText(), channelId)
            page.messages.forEach { _messages.tryEmit(it) }
            continuation = page.nextContinuation
            if (continuation == null) break
            delay(page.timeoutMs)
        }
    }

    internal companion object {
        const val CLIENT_NAME = "WEB"
        const val CLIENT_VERSION = "2.20240701.00.00"
        const val DEFAULT_POLL_MS = 3_000L
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val RETRY_DELAY_MS = 5_000L
        const val X_CLIENT_NAME_HEADER = "X-Youtube-Client-Name"
        const val X_CLIENT_VERSION_HEADER = "X-Youtube-Client-Version"

        /** Kanal-Live-Seite (redirectet auf die Watch-Seite des Streams). */
        fun streamUrl(channelId: String): String =
            "https://www.youtube.com/channel/$channelId/live"

        /** innertube-Endpunkt der Chat-Abfrage (Body-Anhänge über [buildGetLiveChatBody]). */
        fun getLiveChatUrl(): String =
            "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?prettyPrint=false"
    }
}

/** Bootstrap-Ergebnis der Kanal-Live-Seite (P1). */
internal data class YoutubeLiveChatBootstrap(
    /** Aktiver Stream (11-Zeichen-ID) — Diagnose/Future, nicht kritisch. */
    val videoId: String?,
    /** Continuation-Token für den ersten get_live_chat-Aufruf. */
    val continuation: String,
)

/** Eine abgerufene Live-Chat-Seite: Nachrichten, Folgetoken, Poll-Intervall. */
internal data class YoutubeChatPage(
    val messages: List<ChatMessage>,
    val nextContinuation: String?,
    val timeoutMs: Long,
)

/** innertube-Request-Body für get_live_chat (kompakt, ohne Serializer-Magie). */
internal fun buildGetLiveChatBody(continuation: String, clientVersion: String): JsonObject =
    buildJsonObject {
        put(
            "context",
            buildJsonObject {
                put(
                    "client",
                    buildJsonObject {
                        put("clientName", JsonPrimitive(YoutubeChatReader.CLIENT_NAME))
                        put("clientVersion", JsonPrimitive(clientVersion))
                    },
                )
            },
        )
        put("continuation", JsonPrimitive(continuation))
    }

/**
 * Extrahiert das JSON hinter `var ytInitialData = ` mit einem string- und
 * escape-bewussten Balanced-Scanner (nicht Regex — geschachtelte Objekte
 * und Strings mit `{`/`}` drin).
 */
internal fun extractYtInitialData(html: String): String? {
    val marker = html.indexOf("ytInitialData").takeIf { it >= 0 } ?: return null
    val start = html.indexOf('{', marker).takeIf { it >= 0 } ?: return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
    }
    return null
}

/**
 * Sammelt rekursiv alle Werte unter Schlüsseln [key] (Deep-Find statt
 * Pfad-Navigation — drift-tolerant gegen Struktur-Umbauten der Seite).
 */
internal fun deepFind(element: kotlinx.serialization.json.JsonElement, key: String): List<kotlinx.serialization.json.JsonElement> {
    val found = mutableListOf<kotlinx.serialization.json.JsonElement>()
    fun walk(e: kotlinx.serialization.json.JsonElement) {
        when (e) {
            is JsonObject -> {
                e[key]?.let { found.add(it) }
                e.values.forEach { walk(it) }
            }
            is JsonArray -> e.forEach { walk(it) }
            else -> Unit
        }
    }
    walk(element)
    return found
}

/**
 * Liest ytInitialData aus: das Chat-Continuation-Token (erstes
 * `getContinuationCommand.token`) und die 11-Zeichen-`videoId`. `null`,
 * wenn kein Live-Chat-Aufhänger da ist (Kanal offline).
 */
internal fun parseLiveChatBootstrap(ytInitialData: String): YoutubeLiveChatBootstrap? {
    val root = kotlinx.serialization.json.Json.parseToJsonElement(ytInitialData).jsonObject
    // Tier 0: im Live-Chat-Teilbaum (reale Watch-Seite: conversationBar.
    // liveChatRenderer.continuations[0].reloadContinuationData.token bzw.
    // liveChatRenderer.continuation.continuationCommand.token).
    val chatSubtree = deepFind(root, "liveChatRenderer").firstOrNull() as? JsonObject
    val continuation = (chatSubtree?.let(::findChatContinuation) ?: findChatContinuation(root))
        ?: return null
    val videoId = deepFind(root, "videoId")
        .filterIsInstance<JsonPrimitive>()
        .firstOrNull { it.isString && it.content.length == 11 }
        ?.content
    return YoutubeLiveChatBootstrap(videoId = videoId, continuation = continuation)
}

/**
 * Chat-Continuation in einem Teilbaum: `continuationCommand.token` zuerst,
 * dann Bootstrap-Formen (`*ContinuationData.token`, reale Watch-Seite).
 * Das globale Fallback-Tier kann im Drift-Fall theoretisch ein Nicht-Chat-
 * Token greifen — harmlos, denn ein falsches Token liefert nur einen
 * get_live_chat-Fehler und damit Disconnected + Retry (kein Falsch-Chat).
 */
private fun findChatContinuation(scope: JsonObject): String? =
    deepFind(scope, "continuationCommand")
        .filterIsInstance<JsonObject>()
        .firstNotNullOfOrNull { it["token"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
        ?: allJsonObjects(scope)
            .flatMap { it.entries }
            .filter { it.key.endsWith("ContinuationData") }
            .mapNotNull { (it.value as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull }
            .firstOrNull { !it.isNullOrBlank() }

/** Alle [JsonObject]s unterhalb [element] (inkl. [element] selbst). */
internal fun allJsonObjects(element: kotlinx.serialization.json.JsonElement): List<JsonObject> {
    val found = mutableListOf<JsonObject>()
    fun walk(e: kotlinx.serialization.json.JsonElement) {
        when (e) {
            is JsonObject -> {
                found.add(e)
                e.values.forEach { walk(it) }
            }
            is JsonArray -> e.forEach { walk(it) }
            else -> Unit
        }
    }
    walk(element)
    return found
}

/**
 * Mappt einen `liveChatTextMessageRenderer` auf eine [ChatMessage]
 * (platform = YOUTUBE). YouTube liefert keine User-Farben und keine
 * Twitch-ähnlichen Rollen-Flags — Badges werden als Tooltip-Texte
 * übernommen, Moderator/Broadcaster bleiben bewusst `false` (P1: Bot-
 * Aktionen laufen nur auf Twitch).
 */
internal fun mapYoutubeMessage(renderer: JsonObject, channel: String): ChatMessage? {
    val id = renderer["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val author = renderer["authorSimpleText"]?.jsonPrimitive?.contentOrNull
        ?: renderer["authorExternalChannelId"]?.jsonPrimitive?.contentOrNull
        ?: return null
    val runs = renderer["message"]?.jsonObject?.get("runs")?.jsonArray ?: return null
    val text = runs.joinToString("") { run ->
        (run as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
    }
    if (text.isBlank()) return null
    val timestampMs = renderer["timestampUsec"]?.jsonPrimitive?.contentOrNull
        ?.toLongOrNull()?.div(1000L) ?: 0L
    val badges = renderer["authorBadges"]?.jsonArray
        ?.mapNotNull { badge ->
            (badge as? JsonObject)
                ?.get("liveChatAuthorBadgeRenderer")?.jsonObject
                ?.get("tooltip")?.jsonPrimitive?.contentOrNull
        }
        .orEmpty()
    return ChatMessage(
        id = id,
        channel = channel,
        userId = renderer["authorExternalChannelId"]?.jsonPrimitive?.contentOrNull ?: author,
        userLogin = author,
        displayName = author,
        color = null,
        text = text,
        badges = badges,
        emotesTag = "",
        timestamp = timestampMs,
        isModerator = false,
        isSubscriber = false,
        platform = ChatPlatform.YOUTUBE,
    )
}

/**
 * Parst eine get_live_chat-Antwort: Nachrichten aus `addChatItemAction`
 * (unbekannte Aktionen/Renderer übersprungen), Folgetoken aus dem
 * `continuations`-Array (jede Variante: timed/invalidation/…), `timeoutMs`
 * aus demselben Objekt — Default [YoutubeChatReader.DEFAULT_POLL_MS].
 */
internal fun parseGetLiveChatResponse(body: String, channel: String): YoutubeChatPage {
    val json = Json { ignoreUnknownKeys = true }
    val root = json.parseToJsonElement(body).jsonObject
    val chat = root["continuationContents"]
        ?.jsonObject?.get("liveChatContinuation")?.jsonObject
    val actions = chat?.get("actions") as? JsonArray ?: JsonArray(emptyList())
    val messages = actions.mapNotNull { action ->
        (action as? JsonObject)
            ?.get("addChatItemAction")?.jsonObject
            ?.get("item")?.jsonObject
            ?.get("liveChatTextMessageRenderer")?.jsonObject
            ?.let { mapYoutubeMessage(it, channel) }
    }
    val continuationEntries = (chat?.get("continuations") as? JsonArray)
        ?.filterIsInstance<JsonObject>()
        .orEmpty()
    val continuationObjects = continuationEntries.flatMap { it.values.filterIsInstance<JsonObject>() }
    // Real: "continuation"; Drift-Fallback: "token" (Bootstrap-Schlüssel-Form).
    val nextContinuation = continuationObjects
        .firstNotNullOfOrNull { (it["continuation"] ?: it["token"])?.jsonPrimitive?.contentOrNull }
    val timeoutMs = continuationObjects
        .firstNotNullOfOrNull { it["timeoutMs"]?.jsonPrimitive?.longOrNull }
        ?: YoutubeChatReader.DEFAULT_POLL_MS
    return YoutubeChatPage(messages = messages, nextContinuation = nextContinuation, timeoutMs = timeoutMs)
}

/**
 * Platzhalter-Sender für YouTube (Skizze P4): Senden braucht Google-OAuth
 * mit `youtube.force-ssl` — bis dahin schlägt ein Send-Versuch kontrolliert
 * mit [com.vivid.feature.chat.session.ChatSendResult.Failed] fehl, statt
 * das Multibinding des Session-Managers zu erzwingen.
 */
@Singleton
class YoutubeChatSender @Inject constructor() : ChatSender {
    override suspend fun send(config: ChatSessionConfig, text: String) =
        com.vivid.feature.chat.session.ChatSendResult.Failed(
            "YouTube-Senden folgt mit P4 (Google-OAuth, youtube.force-ssl)",
        )
}
