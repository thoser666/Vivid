package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.model.ChatBadge
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/**
 * Liefert die Twitch-Chat-Badges eines Kanals als Bild-URLs — die Grundlage
 * für die Badge-Anzeige im Chat-Overlay.
 *
 * Twitch-Badge-Bilder gibt es nicht über eine feste URL; man muss die
 * **Helix-Chat-Badges-Endpunkte** abfragen und bekommt pro `set_id` und
 * Versions-`id` die CDN-URLs:
 *
 * - `GET /helix/chat/badges/global` — globale Badges (Moderator, VIP, …).
 * - `GET /helix/chat/badges?broadcaster_id=<id>` — Kanal-Badges (Broadcaster,
 *   Subscriber-Monate, Sub-Gift, Bits, …).
 *
 * Der Lookup-Key ist `"set_id/version_id"` — exakt das IRC-Format, in dem
 * [com.vivid.feature.chat.model.ChatMessage.badges] die Badges einer
 * Nachricht trägt (z. B. `subscriber/6` = 6-Monate-Sub-Badge).
 *
 * Fehler sind hier unkritisch: Ohne Badge-Daten zeigt das Overlay einfach nur
 * den Usernamen ohne Bilder — der Client liefert bei jedem Fehler eine leere
 * Map statt zu werfen.
 */
@Singleton
class TwitchBadgeClient @Inject constructor(
    private val http: HttpClient,
    private val whisperClient: TwitchWhisperClient,
) {
    /**
     * Lädt die globalen + Kanal-Badges für [config] und liefert sie als
     * Map `"set_id/version_id" → [ChatBadge]` (2x-Bilder). Kanal-Badges
     * gewinnen bei Überschneidungen (z. B. Kanal-eigene Sub-Badge-Sets).
     */
    suspend fun load(config: TwitchEventSubConfig): Map<String, ChatBadge> {
        val badges = mutableMapOf<String, ChatBadge>()
        fetchBadges(config, "$HELIX_API/chat/badges/global")?.let { badges.putAll(it) }
        val broadcasterId = whisperClient.resolveUserId(
            TwitchWhisperConfig(
                botLogin = config.botLogin,
                oauthToken = config.oauthToken,
                clientId = config.clientId,
            ),
            config.channel,
        )
        if (broadcasterId != null) {
            fetchBadges(config, "$HELIX_API/chat/badges", broadcasterId)?.let { badges.putAll(it) }
        }
        return badges
    }

    private suspend fun fetchBadges(
        config: TwitchEventSubConfig,
        url: String,
        broadcasterId: String? = null,
    ): Map<String, ChatBadge>? = try {
        val response = http.get(url) {
            broadcasterId?.let { parameter("broadcaster_id", it) }
            header(HttpHeaders.Authorization, "Bearer ${config.oauthToken.trim().removePrefix("oauth:")}")
            header(CLIENT_ID_HEADER, config.clientId)
        }
        if (!response.status.isSuccess()) {
            null
        } else {
            response.body<TwitchBadgesResponse>().data.flatMap { set ->
                set.versions.map { version ->
                    val badge = ChatBadge(
                        setId = set.set_id,
                        versionId = version.id,
                        title = version.title.ifBlank { set.set_id },
                        imageUrl = version.image_url_2x.ifBlank { version.image_url_1x },
                    )
                    badge.key to badge
                }
            }.toMap()
        }
    } catch (e: Exception) {
        // Netzwerk-/Parse-Fehler: Overlay läuft ohne Badges weiter.
        null
    }

    companion object {
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"
    }
}

@Serializable
internal data class TwitchBadgesResponse(val data: List<TwitchBadgeSet> = emptyList())

@Serializable
internal data class TwitchBadgeSet(
    val set_id: String = "",
    val versions: List<TwitchBadgeVersion> = emptyList(),
)

@Serializable
internal data class TwitchBadgeVersion(
    val id: String = "",
    val image_url_1x: String = "",
    val image_url_2x: String = "",
    val image_url_4x: String = "",
    val title: String = "",
    val description: String = "",
)
