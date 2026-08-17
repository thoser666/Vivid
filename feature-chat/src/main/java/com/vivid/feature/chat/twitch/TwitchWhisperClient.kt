package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Konfiguration für den privaten Antwortweg (Twitch-Whisper) des Chat-Bots.
 *
 * Der Bot sendet Owner-Antworten über die **Helix-Whisper-API** — der alte
 * IRC-Weg (`/w`-Command via PRIVMSG) ist von Twitch seit Februar 2023
 * abgeschaltet. Voraussetzungen (Twitch-Doku „Whispers“):
 * - Der OAuth-Token des Bot-Kontos muss den Scope **`user:manage:whispers`**
 *   enthalten (zusätzlich zu chat:read/chat:edit).
 * - [clientId] ist die **Client-ID der Twitch-App**, die den Token ausgestellt
 *   hat (Pflicht-Header `Client-Id` für alle Helix-API-Aufrufe).
 * - Das Sender-Konto braucht eine **verifizierte Telefonnummer**.
 */
data class TwitchWhisperConfig(
    val botLogin: String,
    val oauthToken: String,
    val clientId: String,
) {
    val isConfigured: Boolean
        get() = botLogin.isNotBlank() && oauthToken.isNotBlank() && clientId.isNotBlank()
}

/** Fehler beim Senden eines Twitch-Whispers (Ursache als Message, fürs Bot-Log). */
class TwitchWhisperException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Sendet private Nachrichten (Whispers) über die Twitch-Helix-API an einen
 * Viewer — der private Antwortweg für Owner-Befehle (`!start`/`!stop`/`!diag`/
 * `!ask`). Die Engine entscheidet, wann gepﬁstert wird; dieser Client liefert
 * nur die Transportlogik (Login → User-ID auflösen, POST /helix/whispers).
 */
@Singleton
class TwitchWhisperClient @Inject constructor(
    private val http: HttpClient,
) {
    // Login (normalisiert) → User-ID — Whispers sind selten, aber die
    // Auflösung kostet einen API-Call; Caching reicht für die Stream-Dauer.
    private val userIdCache = ConcurrentHashMap<String, String>()

    /**
     * Sendet [text] als Whisper an [toLogin]. Wirft [TwitchWhisperException]
     * bei jedem Fehler (nicht konfiguriert, User-ID nicht auflösbar,
     * HTTP-Fehler wie 400 „Whispers blockiert“, 401 „Scope fehlt“).
     * HTTP 204 = angenommen (Twitch kann Whispers trotzdem still verwerfen).
     */
    suspend fun whisper(config: TwitchWhisperConfig, toLogin: String, text: String) {
        if (!config.isConfigured) {
            throw TwitchWhisperException(
                "Whisper nicht konfiguriert — Twitch-App-Client-ID fehlt (Einstellungen → Owner-Zugriff) und/oder Token ohne Scope user:manage:whispers.",
            )
        }
        val target = toLogin.trim().lowercase().removePrefix("@")
        if (target.isBlank()) throw TwitchWhisperException("Kein Whisper-Empfänger angegeben.")
        // Erste Whisper-Nachricht an einen User ist auf 500 Zeichen begrenzt.
        val message = text
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .take(MAX_WHISPER_LENGTH)
        if (message.isEmpty()) throw TwitchWhisperException("Leere Whisper-Nachricht.")

        val fromId = resolveUserId(config, config.botLogin)
            ?: throw TwitchWhisperException("Bot-User-ID konnte nicht ermittelt werden (Login ${config.botLogin}).")
        val toId = resolveUserId(config, target)
            ?: throw TwitchWhisperException("User-ID von $target konnte nicht ermittelt werden.")

        try {
            val response = http.post("$HELIX_API/whispers") {
                parameter("from_user_id", fromId)
                parameter("to_user_id", toId)
                header(HttpHeaders.Authorization, bearer(config.oauthToken))
                header(CLIENT_ID_HEADER, config.clientId)
                contentType(ContentType.Application.Json)
                setBody(WhisperSendRequest(message))
            }
            if (!response.status.isSuccess()) {
                throw TwitchWhisperException(describeError(response.status.value))
            }
        } catch (e: TwitchWhisperException) {
            throw e
        } catch (e: Exception) {
            throw TwitchWhisperException("Whisper fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Löst einen Twitch-Login über `GET /helix/users` in die User-ID auf
     * (mit Cache). null bei Netzwerk-/Auth-Fehlern oder unbekanntem Login.
     * Auch vom EventSub-Client genutzt (Bot-User-ID für die Subscription).
     */
    suspend fun resolveUserId(config: TwitchWhisperConfig, login: String): String? {
        val key = login.trim().lowercase()
        userIdCache[key]?.let { return it }
        try {
            val response = http.get("$HELIX_API/users") {
                parameter("login", key)
                header(HttpHeaders.Authorization, bearer(config.oauthToken))
                header(CLIENT_ID_HEADER, config.clientId)
            }
            if (!response.status.isSuccess()) return null
            val id = response.body<TwitchUsersResponse>().data.firstOrNull()?.id ?: return null
            userIdCache[key] = id
            return id
        } catch (e: Exception) {
            return null
        }
    }

    private fun bearer(token: String): String = "Bearer ${token.trim().removePrefix("oauth:")}"

    private fun describeError(code: Int): String = when (code) {
        400 -> "Twitch hat den Whisper abgelehnt (400) — der Empfänger blockt Whispers von Fremden oder die Anfrage ist ungültig."
        401 -> "Twitch-Authentifizierung fehlgeschlagen (401) — der Bot-Token braucht den Scope user:manage:whispers."
        403 -> "Twitch verweigert den Whisper (403) — das Sender-Konto braucht eine verifizierte Telefonnummer."
        else -> "Twitch-Whisper fehlgeschlagen (HTTP $code)."
    }

    companion object {
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"

        /** Limit der ersten Whisper-Nachricht an einen User (Twitch). */
        internal const val MAX_WHISPER_LENGTH = 500
    }
}

@Serializable
internal data class WhisperSendRequest(val message: String)

@Serializable
internal data class TwitchUsersResponse(val data: List<TwitchUser> = emptyList())

@Serializable
internal data class TwitchUser(
    val id: String,
    val login: String = "",
    val display_name: String = "",
)
