package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/** Ergebnis eines Send-Chat-Message-Aufrufs (Twitch liefert immer HTTP 200, s. u.). */
data class SendChatResult(
    val messageId: String?,
    val isSent: Boolean,
    val dropReason: String?,
)

/** Fehler beim Senden einer Chat-Nachricht (Ursache als Message, fürs Bot-Log). */
class TwitchSendChatException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Sendet öffentliche Chat-Nachrichten über die **Helix-API**
 * (`POST /helix/chat/messages`) — der IRC-Nachfolger für `PRIVMSG`.
 *
 * Voraussetzungen (Twitch-Doku „Send Chat Message“):
 * - Der OAuth-Token des Bot-Kontos braucht den Scope **`user:write:chat`**
 *   (ersetzt das alte `chat:edit`).
 * - [TwitchEventSubConfig.clientId] ist die Client-ID der Twitch-App, die den
 *   Token ausgestellt hat (Pflicht-Header `Client-Id` für Helix-Aufrufe).
 * - `broadcaster_id` (Kanal) und `sender_id` (Bot) werden wie gehabt per
 *   `GET /helix/users` aufgelöst (mit Cache).
 *
 * Anders als IRC liefert die API **keine NOTICE, sondern einen Drop-Grund**:
 * Auch bei HTTP 200 kann die Nachricht verworfen werden (`is_sent = false`
 * + `drop_reason`, z. B. Slow-Mode, fehlende verifizierte E-Mail, Bann).
 * Der Aufrufer entscheidet, wie er den Drop behandelt (loggen, still lassen).
 */
@Singleton
class TwitchSendChatClient @Inject constructor(
    private val http: HttpClient,
    private val whisperClient: TwitchWhisperClient,
) {
    /**
     * Sendet [text] öffentlich in den Kanal von [config]. Wirft
     * [TwitchSendChatException] bei nicht konfiguriertem Bot, nicht
     * auflösbaren User-IDs oder HTTP-Fehlern (401 = Scope fehlt, 400 =
     * Konto/Anfrage ungültig, 429 = Rate-Limit).
     */
    suspend fun send(config: TwitchEventSubConfig, text: String): SendChatResult {
        val message = text
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)
        if (message.isEmpty()) {
            throw TwitchSendChatException("Leere Chat-Nachricht.")
        }
        val auth = TwitchWhisperConfig(
            botLogin = config.botLogin,
            oauthToken = config.oauthToken,
            clientId = config.clientId,
        )
        val senderId = whisperClient.resolveUserId(auth, config.botLogin)
            ?: throw TwitchSendChatException("Bot-User-ID konnte nicht ermittelt werden (Login ${config.botLogin}).")
        val broadcasterId = whisperClient.resolveUserId(auth, config.channel)
            ?: throw TwitchSendChatException("User-ID des Kanals ${config.channel} konnte nicht ermittelt werden.")

        val response = http.post("$HELIX_API/chat/messages") {
            header(HttpHeaders.Authorization, bearer(config.oauthToken))
            header(CLIENT_ID_HEADER, config.clientId)
            contentType(ContentType.Application.Json)
            setBody(
                SendChatRequest(
                    broadcaster_id = broadcasterId,
                    sender_id = senderId,
                    message = message,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw TwitchSendChatException(describeError(response.status.value))
        }
        val data = response.body<SendChatResponse>().data.firstOrNull()
            ?: throw TwitchSendChatException("Twitch hat eine leere Antwort geliefert.")
        return SendChatResult(
            messageId = data.message_id.takeIf { it.isNotBlank() },
            isSent = data.is_sent,
            dropReason = data.drop_reason?.message?.takeIf { it.isNotBlank() },
        )
    }

    private fun bearer(token: String): String = "Bearer ${token.trim().removePrefix("oauth:")}"

    private fun describeError(code: Int): String = when (code) {
        400 -> "Twitch hat die Chat-Nachricht abgelehnt (400) — Konto gebannt/geperrt oder Anfrage ungültig."
        401 -> "Twitch-Authentifizierung fehlgeschlagen (401) — der Bot-Token braucht den Scope user:write:chat."
        403 -> "Twitch verweigert das Senden (403) — der Bot darf in diesem Kanal nicht schreiben."
        429 -> "Twitch Rate-Limit erreicht (429) — zu viele Nachrichten in kurzer Zeit."
        else -> "Twitch-Send-Chat fehlgeschlagen (HTTP $code)."
    }

    companion object {
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"

        /** Twitch-Nachrichtenlimit (identisch zur bisherigen IRC-Begrenzung). */
        internal const val MAX_MESSAGE_LENGTH = 500
    }
}

@Serializable
internal data class SendChatRequest(
    val broadcaster_id: String,
    val sender_id: String,
    val message: String,
)

@Serializable
internal data class SendChatResponse(val data: List<SendChatMessage> = emptyList())

@Serializable
internal data class SendChatMessage(
    val message_id: String = "",
    val is_sent: Boolean = false,
    val drop_reason: SendChatDropReason? = null,
)

@Serializable
internal data class SendChatDropReason(
    val code: String = "",
    val message: String = "",
)
