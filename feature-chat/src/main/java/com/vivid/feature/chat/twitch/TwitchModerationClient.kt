package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/** Fehler bei einer Twitch-Moderation (Ursache als Message, fürs Bot-Log). */
class TwitchModerationException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Führt Moderation-Aktionen über die **Helix-API** aus — die Umsetzung der
 * Owner-Befehle `!ban` / `!timeout` / `!delete`:
 *
 * - `!ban` / `!timeout` → `POST /helix/moderation/bans` (Scope
 *   **`moderator:manage:banned_users`**; `duration` in Sekunden macht aus dem
 *   Bann einen Timeout, fehlendes `duration` = permanenter Bann).
 * - `!delete` → `DELETE /helix/moderation/chat` je Nachrichten-ID (Scope
 *   **`moderator:manage:chat_messages`**). Es können nur Nachrichten gelöscht
 *   werden, die der Bot selbst über den EventSub-Reader gesehen hat (die
 *   Engine trackt die letzten IDs).
 *
 * Voraussetzungen wie beim Senden: Der OAuth-Token des Bot-Kontos braucht die
 * Moderation-Scopes, [TwitchEventSubConfig.clientId] ist die Client-ID der
 * Twitch-App, und der Bot (moderator_id) muss **Moderator im Kanal** sein
 * (oder der Kanal-Inhaber selbst) — sonst antwortet Twitch mit 403.
 *
 * Die Methoden liefern die fertige Chat-Antwort (Bestätigung/Fehlermeldung)
 * und werfen [TwitchModerationException] bei API-/Transport-Fehlern; die
 * Engine ([com.vivid.feature.chat.bot.ChatModeration]) kümmert sich nur um
 * Gate, Limits und Antwortweg.
 */
@Singleton
class TwitchModerationClient @Inject constructor(
    private val http: HttpClient,
    private val whisperClient: TwitchWhisperClient,
) {
    /** `!ban <user>` — permanent verbannen. */
    suspend fun ban(config: TwitchEventSubConfig, userLogin: String): String {
        val target = normalize(userLogin)
        if (target.isBlank()) return missingUserHint("!ban")
        return moderate(config, target, durationSeconds = null)
    }

    /** `!timeout <user> <minuten?>` — Timeout (default 5 Min). */
    suspend fun timeout(config: TwitchEventSubConfig, userLogin: String, durationMinutes: Int?): String {
        val target = normalize(userLogin)
        if (target.isBlank()) return missingUserHint("!timeout")
        val minutes = (durationMinutes ?: DEFAULT_TIMEOUT_MINUTES).coerceAtLeast(1)
        return moderate(config, target, durationSeconds = minutes * 60L)
    }

    /**
     * `!delete <anzahl?>` — löscht die letzten [count] Nachrichten aus
     * [recentMessageIds] (null = alle getrackten). Twitch erlaubt nur das
     * Löschen einzelner Nachrichten per ID und nur innerhalb eines
     * Lösch-Fensters — Nachrichten, die der Bot nicht gesehen hat oder die
     * zu alt sind, werden übersprungen (400) statt den Rest zu blockieren.
     */
    suspend fun deleteRecent(
        config: TwitchEventSubConfig,
        count: Int?,
        recentMessageIds: List<String>,
    ): String {
        val ids = resolveModeratorIds(config)
            ?: throw TwitchModerationException("Moderator-/Broadcaster-ID konnte nicht ermittelt werden.")
        val targets = pickTargets(count, recentMessageIds)
        if (targets.isEmpty()) {
            return "Keine Nachrichten im Puffer zum Löschen — der Bot muss die Nachrichten zuerst gesehen haben."
        }
        var deleted = 0
        for (messageId in targets) {
            val response = http.delete("$HELIX_API/moderation/chat") {
                parameter("broadcaster_id", ids.broadcasterId)
                parameter("moderator_id", ids.moderatorId)
                parameter("message_id", messageId)
                header(HttpHeaders.Authorization, bearer(config.oauthToken))
                header(CLIENT_ID_HEADER, config.clientId)
            }
            if (response.status.isSuccess()) {
                deleted++
            } else if (response.status.value == 400) {
                // Nachricht zu alt/schon gelöscht/nicht löschbar → überspringen.
            } else {
                throw TwitchModerationException(describeDeleteError(response.status.value))
            }
        }
        return if (deleted == 0) {
            "⚠️ Keine Nachricht gelöscht — alle waren zu alt oder bereits weg."
        } else {
            "🗑 $deleted Nachricht(en) gelöscht."
        }
    }

    /** Gemeinsamer Pfad für `!ban`/`!timeout` (gleicher Endpunkt). */
    private suspend fun moderate(
        config: TwitchEventSubConfig,
        userLogin: String,
        durationSeconds: Long?,
    ): String {
        val ids = resolveModeratorIds(config)
            ?: throw TwitchModerationException("Moderator-/Broadcaster-ID konnte nicht ermittelt werden.")
        val userId = whisperClient.resolveUserId(toWhisperConfig(config), userLogin)
            ?: return "❌ User $userLogin konnte nicht gefunden werden."
        val isBan = durationSeconds == null
        val reason = "Per !${if (isBan) "ban" else "timeout"} vom Streamer"
        val response = http.post("$HELIX_API/moderation/bans") {
            parameter("broadcaster_id", ids.broadcasterId)
            parameter("moderator_id", ids.moderatorId)
            header(HttpHeaders.Authorization, bearer(config.oauthToken))
            header(CLIENT_ID_HEADER, config.clientId)
            contentType(ContentType.Application.Json)
            // Getrennte DTOs: ein permanenter Bann sendet KEIN duration-Feld
            // (Twitch würde ein explizites null ablehnen); der Timeout sendet
            // die Dauer in Sekunden (Minuten × 60).
            setBody(
                if (isBan) {
                    ModerationRequest(listOf(ModerationTarget(user_id = userId, reason = reason)))
                } else {
                    TimeoutRequest(listOf(TimeoutTarget(user_id = userId, reason = reason, duration = durationSeconds)))
                },
            )
        }
        if (!response.status.isSuccess()) {
            throw TwitchModerationException(describeBanError(response.status.value))
        }
        return if (isBan) {
            "✅ @$userLogin wurde verbannt."
        } else {
            "⏱ @$userLogin wurde für ${durationSeconds / 60} Min. getimeoutet."
        }
    }

    /** broadcaster_id (Kanal) + moderator_id (Bot) per `GET /helix/users`. */
    private suspend fun resolveModeratorIds(config: TwitchEventSubConfig): UserIds? {
        val whisperConfig = toWhisperConfig(config)
        val broadcasterId = whisperClient.resolveUserId(whisperConfig, config.channel) ?: return null
        val moderatorId = whisperClient.resolveUserId(whisperConfig, config.botLogin) ?: return null
        return UserIds(broadcasterId, moderatorId)
    }

    private fun toWhisperConfig(config: TwitchEventSubConfig): TwitchWhisperConfig =
        TwitchWhisperConfig(
            botLogin = config.botLogin,
            oauthToken = config.oauthToken,
            clientId = config.clientId,
        )

    /** Wählt die zu löschenden Nachrichten (letzte [count] bzw. alle). */
    private fun pickTargets(count: Int?, recentMessageIds: List<String>): List<String> {
        val n = count?.coerceAtLeast(1) ?: recentMessageIds.size
        return recentMessageIds.takeLast(n)
    }

    private fun normalize(login: String): String =
        login.trim().lowercase().removePrefix("@")

    private fun bearer(token: String): String = "Bearer ${token.trim().removePrefix("oauth:")}"

    private fun describeBanError(code: Int): String = when (code) {
        400 -> "Twitch hat die Moderation abgelehnt (400) — der User existiert nicht oder ist schon gebannt/getimeoutet."
        401 -> "Twitch-Authentifizierung fehlgeschlagen (401) — der Bot-Token braucht den Scope moderator:manage:banned_users."
        403 -> "Twitch verweigert die Moderation (403) — der Bot muss Moderator im Kanal sein (oder der Kanal-Inhaber)."
        429 -> "Twitch Rate-Limit erreicht (429) — zu viele Moderation-Aufrufe in kurzer Zeit."
        else -> "Twitch-Moderation fehlgeschlagen (HTTP $code)."
    }

    private fun describeDeleteError(code: Int): String = when (code) {
        401 -> "Twitch-Authentifizierung fehlgeschlagen (401) — der Bot-Token braucht den Scope moderator:manage:chat_messages."
        403 -> "Twitch verweigert das Löschen (403) — der Bot muss Moderator im Kanal sein (oder der Kanal-Inhaber)."
        429 -> "Twitch Rate-Limit erreicht (429) — zu viele Moderation-Aufrufe in kurzer Zeit."
        else -> "Twitch-Nachrichtenlöschung fehlgeschlagen (HTTP $code)."
    }

    private data class UserIds(val broadcasterId: String, val moderatorId: String)

    companion object {
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"
        internal const val DEFAULT_TIMEOUT_MINUTES = 5
    }

    private fun missingUserHint(command: String): String =
        "Bitte gib einen Benutzernamen an: $command <user>"
}

@Serializable
internal data class ModerationRequest(val data: List<ModerationTarget>)

@Serializable
internal data class ModerationTarget(
    val user_id: String,
    val reason: String = "",
)

@Serializable
internal data class TimeoutRequest(val data: List<TimeoutTarget>)

@Serializable
internal data class TimeoutTarget(
    val user_id: String,
    val reason: String = "",
    // Dauer in Sekunden (Minuten × 60) — Twitch-Pflichtfeld für Timeouts.
    val duration: Long = 0,
)
