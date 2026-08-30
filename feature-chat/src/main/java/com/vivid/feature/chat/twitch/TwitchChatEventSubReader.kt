package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.AlertDetail
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

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

    /**
     * Event-Alerts (Follow/Sub/Raid) des Kanals — vom selben EventSub-
     * WebSocket wie [messages], aber als eigener Flow. Die Overlay-Alerts
     * werden über [ChatOverlayViewModel] angezeigt; [triggerTestAlert]
     * speist synthetische Alerts in denselben Flow ein (Trigger-API).
     */
    private val _alerts = MutableSharedFlow<ChatAlert>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alerts: Flow<ChatAlert> = _alerts.asSharedFlow()

    /**
     * Gelöschte Nachrichten — vom EventSub-Topic `channel.chat.message_delete`
     * geliefert. Enthält die IDs gelöschter Nachrichten, die vom
     * [ChatOverlayViewModel] gefiltert bzw. ausgegraut werden.
     */
    private val _deletedMessageIds = MutableSharedFlow<String>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deletedMessageIds: Flow<String> = _deletedMessageIds.asSharedFlow()

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
            "notification" -> envelope.payload.event?.let { eventJson ->
                // Dispatch über subscription_type: Chat-Nachrichten und
                // Event-Alerts (Follow/Sub/Raid) kommen über denselben
                // WebSocket, tragen aber verschiedene Event-Formen.
                when (envelope.metadata.subscription_type) {
                    FOLLOW_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<FollowEvent>(eventJson)
                        _alerts.tryEmit(toFollowAlert(event))
                    }
                    SUBSCRIBE_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<SubscribeEvent>(eventJson)
                        _alerts.tryEmit(toSubscribeAlert(event))
                    }
                    GIFT_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<GiftEvent>(eventJson)
                        _alerts.tryEmit(toGiftAlert(event))
                    }
                    RESUB_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<ResubEvent>(eventJson)
                        _alerts.tryEmit(toResubAlert(event))
                    }
                    RAID_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<RaidEvent>(eventJson)
                        _alerts.tryEmit(toRaidAlert(event))
                    }
                    // channel.chat.message (auch wenn subscription_type fehlt,
                    // z. B. in Fixtures) → Chat-Nachricht.
                    CHAT_MESSAGE_DELETE_SUBSCRIPTION_TYPE -> {
                        val event = json.decodeFromJsonElement<ChatMessageDeleteEvent>(eventJson)
                        _deletedMessageIds.tryEmit(event.message_id)
                    }
                    else -> {
                        val event = json.decodeFromJsonElement<ChatMessageEvent>(eventJson)
                        val text = event.message.text.trim()
                        if (text.isNotEmpty()) {
                            _messages.tryEmit(toChatMessage(cfg, event))
                        }
                    }
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
        // /me-Nachrichten: Text beginnt mit "/me ", message_type ist "text"
        val isAction = event.message_type == "action" ||
            event.message.text.trimStart().startsWith("/me ")
        val rawText = if (isAction) {
            event.message.text.trimStart().removePrefix("/me ").trim()
        } else {
            event.message.text.trim()
        }
        // Bits: Cheer-Amount aus dem Event + Summe der Cheermote-Fragmente
        val eventBits = event.cheer?.bits_amount ?: 0
        val fragmentBits = event.message.fragments
            .filter { it.type == "cheermote" && it.cheermote != null }
            .sumOf { it.cheermote!!.bits }
        val totalBits = maxOf(eventBits, fragmentBits)
        // Reply: Eltern-Nachricht (gekürzt auf 80 Zeichen)
        val replyParent = event.reply?.let {
            val preview = it.parent_message_body
                .takeIf { body -> body.isNotBlank() }
                ?.take(80)
            ReplyInfo(
                messageId = it.parent_message_id,
                userLogin = it.parent_user_login,
                userName = it.parent_user_name,
                preview = preview,
            )
        }
        return ChatMessage(
            id = event.message_id,
            channel = event.broadcaster_user_login.ifBlank { cfg.channel }.lowercase(),
            userId = event.chatter_user_id,
            userLogin = login,
            displayName = event.chatter_user_name.ifBlank { login },
            color = event.color?.takeIf { it.isNotBlank() },
            text = rawText,
            badges = badges,
            emotesTag = emotesTag,
            timestamp = parseTimestamp(event.message_timestamp),
            isModerator = event.badges.any { it.set_id == "moderator" },
            isSubscriber = event.badges.any { it.set_id == "subscriber" },
            isBroadcaster = event.badges.any { it.set_id == "broadcaster" },
            isWhisper = false,
            inlineEmotes = InlineEmote.parseFromEmotesTag(emotesTag),
            isAction = isAction,
            replyParentMessageId = replyParent?.messageId,
            replyParentUserLogin = replyParent?.userLogin,
            replyParentMessagePreview = replyParent?.preview,
            bitsAmount = totalBits,
        )
    }

    /** Datenklasse für Reply-Informationen (lokal, nicht serialisierbar). */
    private data class ReplyInfo(
        val messageId: String,
        val userLogin: String,
        val userName: String,
        val preview: String?,
    )

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

    /**
     * Subscribt alle Topics dieser Session: `channel.chat.message` (Kern für
     * Overlay + Bot) plus die Event-Alerts `channel.follow`/`channel.subscribe`/
     * `channel.raid`.
     *
     * Nur der Chat-Subscribe bestimmt [subscribed] (Reconnect-Logik) — die
     * Alert-Subscriptions sind best-effort: fehlt ein Scope oder ist der Bot
     * z. B. kein Moderator (Follow braucht `moderator:read:followers`), fällt
     * nur der jeweilige Alert-Typ aus, Chat und Reconnect laufen weiter.
     */
    private suspend fun subscribe(cfg: TwitchEventSubConfig, sessionId: String) {
        val auth = TwitchWhisperConfig(
            botLogin = cfg.botLogin,
            oauthToken = cfg.oauthToken,
            clientId = cfg.clientId,
        )
        val botUserId = whisperClient.resolveUserId(auth, cfg.botLogin) ?: return
        val broadcasterUserId = whisperClient.resolveUserId(auth, cfg.channel) ?: return

        subscribed = postSubscription(
            cfg, sessionId,
            type = CHAT_SUBSCRIPTION_TYPE,
            version = CHAT_SUBSCRIPTION_VERSION,
            condition = ChatEventSubCondition(
                broadcaster_user_id = broadcasterUserId,
                user_id = botUserId,
            ),
        )

        // Event-Alerts: jede Subscription einzeln, Fehler → nur dieser Typ fällt aus.
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = FOLLOW_SUBSCRIPTION_TYPE,
                version = FOLLOW_SUBSCRIPTION_VERSION,
                condition = FollowEventSubCondition(
                    broadcaster_user_id = broadcasterUserId,
                    moderator_user_id = botUserId,
                ),
            )
        }
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = SUBSCRIBE_SUBSCRIPTION_TYPE,
                version = SUBSCRIBE_SUBSCRIPTION_VERSION,
                condition = SubscribeEventSubCondition(broadcaster_user_id = broadcasterUserId),
            )
        }
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = GIFT_SUBSCRIPTION_TYPE,
                version = GIFT_SUBSCRIPTION_VERSION,
                condition = SubscribeEventSubCondition(broadcaster_user_id = broadcasterUserId),
            )
        }
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = RESUB_SUBSCRIPTION_TYPE,
                version = RESUB_SUBSCRIPTION_VERSION,
                condition = SubscribeEventSubCondition(broadcaster_user_id = broadcasterUserId),
            )
        }
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = RAID_SUBSCRIPTION_TYPE,
                version = RAID_SUBSCRIPTION_VERSION,
                condition = RaidEventSubCondition(
                    to_broadcaster_user_id = broadcasterUserId,
                    from_broadcaster_user_id = "",
                ),
            )
        }
        // channel.chat.message_delete: Bot muss Moderator sein, um gelöschte
        // Nachrichten zu empfangen. Scope: moderator:read:chat_messages.
        runCatching {
            postSubscription(
                cfg, sessionId,
                type = CHAT_MESSAGE_DELETE_SUBSCRIPTION_TYPE,
                version = CHAT_MESSAGE_DELETE_SUBSCRIPTION_VERSION,
                condition = ChatMessageDeleteCondition(
                    broadcaster_user_id = broadcasterUserId,
                    user_id = botUserId,
                ),
            )
        }
    }

    /**
     * Postet eine EventSub-Subscription für diese Session; true = Erfolg.
     *
     * `inline`/`reified`: Ktors `setBody` ist reified und braucht den konkreten
     * Typ der Condition, um den generischen `EventSubSubscribeRequest<T>`-
     * Serializer aufzulösen — ein `Any`-Parameter würde die Typ-Information
     * auslöschen und den Serializer-Lookup brechen.
     */
    private suspend inline fun <reified T : Any> postSubscription(
        cfg: TwitchEventSubConfig,
        sessionId: String,
        type: String,
        version: String,
        condition: T,
    ): Boolean {
        val response = http.post("$HELIX_API/eventsub/subscriptions") {
            header(HttpHeaders.Authorization, "Bearer ${cfg.oauthToken.trim().removePrefix("oauth:")}")
            header(CLIENT_ID_HEADER, cfg.clientId)
            contentType(ContentType.Application.Json)
            setBody(
                EventSubSubscribeRequest(
                    type = type,
                    version = version,
                    condition = condition,
                    transport = EventSubTransport(method = "websocket", session_id = sessionId),
                ),
            )
        }
        return response.status.isSuccess()
    }

    /**
     * Trigger-API für Event-Alerts: erzeugt lokal (ohne Netzwerk) einen
     * synthetischen [ChatAlert] und speist ihn in denselben [alerts]-Flow ein.
     * Damit lässt sich das Overlay-Verhalten testen bzw. eine Probe auslösen
     * (z. B. vor dem Go-Live), ohne echte Follows/Subs/Raids abzuwarten.
     */
    fun triggerTestAlert(
        type: ChatAlertType,
        displayName: String = "Test",
        detail: AlertDetail = AlertDetail(),
    ) {
        _alerts.tryEmit(
            ChatAlert(
                id = "test-${type.name.lowercase()}-${System.nanoTime()}",
                type = type,
                displayName = displayName,
                timestamp = System.currentTimeMillis(),
                detail = detail,
            ),
        )
    }

    private fun toFollowAlert(event: FollowEvent): ChatAlert = ChatAlert(
        id = "follow-${event.user_id}-${System.nanoTime()}",
        type = ChatAlertType.FOLLOW,
        displayName = event.user_name.ifBlank { event.user_login }.ifBlank { "?" },
        timestamp = parseTimestamp(event.followed_at),
    )

    private fun toSubscribeAlert(event: SubscribeEvent): ChatAlert = ChatAlert(
        id = "sub-${event.user_id}-${System.nanoTime()}",
        type = ChatAlertType.SUBSCRIBE,
        displayName = event.user_name.ifBlank { event.user_login }.ifBlank { "?" },
        timestamp = System.currentTimeMillis(),
        detail = AlertDetail(
            tier = event.tier,
            gifterName = if (event.is_gift) event.gifter_user_name else "",
        ),
    )

    private fun toGiftAlert(event: GiftEvent): ChatAlert = ChatAlert(
        id = "gift-${event.user_id}-${System.nanoTime()}",
        type = ChatAlertType.GIFT_SUB,
        displayName = event.user_name.ifBlank { event.user_login }.ifBlank { "?" },
        timestamp = System.currentTimeMillis(),
        detail = AlertDetail(
            tier = event.tier,
            count = event.total,
            cumulativeTotal = event.cumulative_total ?: 0,
            isAnonymous = event.is_anonymous,
        ),
    )

    private fun toResubAlert(event: ResubEvent): ChatAlert = ChatAlert(
        id = "resub-${event.user_id}-${System.nanoTime()}",
        type = ChatAlertType.RESUB,
        displayName = event.user_name.ifBlank { event.user_login }.ifBlank { "?" },
        timestamp = System.currentTimeMillis(),
        detail = AlertDetail(
            tier = event.tier,
            months = event.cumulative_months,
            streakMonths = event.streak_months ?: 0,
        ),
    )

    private fun toRaidAlert(event: RaidEvent): ChatAlert = ChatAlert(
        id = "raid-${event.from_broadcaster_user_id}-${System.nanoTime()}",
        type = ChatAlertType.RAID,
        displayName = event.from_broadcaster_user_name.ifBlank { event.from_broadcaster_user_login }.ifBlank { "?" },
        timestamp = System.currentTimeMillis(),
        detail = AlertDetail(viewerCount = event.viewers),
    )

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
        private const val FOLLOW_SUBSCRIPTION_TYPE = "channel.follow"
        private const val FOLLOW_SUBSCRIPTION_VERSION = "2"
        private const val SUBSCRIBE_SUBSCRIPTION_TYPE = "channel.subscribe"
        private const val SUBSCRIBE_SUBSCRIPTION_VERSION = "1"
        private const val GIFT_SUBSCRIPTION_TYPE = "channel.subscription.gift"
        private const val GIFT_SUBSCRIPTION_VERSION = "1"
        private const val RESUB_SUBSCRIPTION_TYPE = "channel.subscription.message"
        private const val RESUB_SUBSCRIPTION_VERSION = "1"
        private const val RAID_SUBSCRIPTION_TYPE = "channel.raid"
        private const val RAID_SUBSCRIPTION_VERSION = "1"
        private const val CHAT_MESSAGE_DELETE_SUBSCRIPTION_TYPE = "channel.chat.message_delete"
        private const val CHAT_MESSAGE_DELETE_SUBSCRIPTION_VERSION = "1"
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
    // Die Event-Form variiert je nach subscription_type (Chat-Nachricht vs.
    // Follow/Subscribe/Raid) — deshalb als JsonElement und erst im Handler
    // anhand von metadata.subscription_type dekodiert.
    val event: JsonElement? = null,
)

@Serializable
internal data class FollowEvent(
    val user_id: String = "",
    val user_login: String = "",
    val user_name: String = "",
    val followed_at: String = "",
)

@Serializable
internal data class SubscribeEvent(
    val user_id: String = "",
    val user_login: String = "",
    val user_name: String = "",
    val tier: String = "",
    val is_gift: Boolean = false,
    val gifter_user_name: String = "",
)

@Serializable
internal data class GiftEvent(
    val user_id: String = "",
    val user_login: String = "",
    val user_name: String = "",
    val broadcaster_user_id: String = "",
    val total: Int = 0,
    val tier: String = "",
    // Twitch liefert null bei anonymen Gifts (und wenn der Wert unbekannt ist).
    val cumulative_total: Int? = null,
    val is_anonymous: Boolean = false,
)

@Serializable
internal data class ResubEvent(
    val user_id: String = "",
    val user_login: String = "",
    val user_name: String = "",
    val broadcaster_user_id: String = "",
    val tier: String = "",
    val cumulative_months: Int = 0,
    // Twitch liefert null, wenn keine Serie aktiv ist.
    val streak_months: Int? = null,
)

@Serializable
internal data class RaidEvent(
    val from_broadcaster_user_id: String = "",
    val from_broadcaster_user_login: String = "",
    val from_broadcaster_user_name: String = "",
    val viewers: Int = 0,
)

// --- Helix-Subscribe-Conditions für die Event-Alert-Topics ---

@Serializable
internal data class FollowEventSubCondition(
    val broadcaster_user_id: String,
    val moderator_user_id: String,
)

@Serializable
internal data class SubscribeEventSubCondition(val broadcaster_user_id: String)

@Serializable
internal data class RaidEventSubCondition(
    val to_broadcaster_user_id: String,
    val from_broadcaster_user_id: String,
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
    // Bits/Cheer: Enthält die Anzahl der Bits, die mit dieser Nachricht geschickt wurden.
    val cheer: ChatEventCheer? = null,
    // Reply: Enthält Informationen über die Eltern-Nachricht, auf die geantwortet wird.
    val reply: ChatEventReply? = null,
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
    val cheermote: ChatEventCheermote? = null,
)

/** Cheermote-Metadaten aus einem Fragment (Bits): */
@Serializable
internal data class ChatEventCheermote(
    val prefix: String = "",
    val bits: Int = 0,
    val tier: Int = 0,
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

/** Bits/Cheer-Informationen aus dem EventSub Event ("cheer"): */
@Serializable
internal data class ChatEventCheer(
    val bits_amount: Int = 0,
)

/** Reply-Informationen aus dem EventSub Event ("reply"): */
@Serializable
internal data class ChatEventReply(
    val parent_message_id: String = "",
    val parent_message_body: String = "",
    val parent_user_id: String = "",
    val parent_user_login: String = "",
    val parent_user_name: String = "",
)

// --- Helix-Subscribe-Condition für channel.chat.message ---

@Serializable
internal data class ChatEventSubCondition(
    val broadcaster_user_id: String,
    val user_id: String,
)

// --- channel.chat.message_delete: Event für gelöschte Nachrichten ---

@Serializable
internal data class ChatMessageDeleteEvent(
    val broadcaster_user_id: String = "",
    val broadcaster_user_login: String = "",
    val broadcaster_user_name: String = "",
    val user_id: String = "",
    val user_login: String = "",
    val user_name: String = "",
    val message_id: String = "",
    val message_timestamp: String = "",
)

// Helix-Subscribe-Condition für channel.chat.message_delete
@Serializable
internal data class ChatMessageDeleteCondition(
    val broadcaster_user_id: String,
    val user_id: String,
)
