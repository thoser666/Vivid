package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/** Twitch-Zugangsdaten für Helix-Kanaloperationen. */
data class TwitchChannelConfig(
    val channel: String,
    val oauthToken: String,
    val clientId: String,
) {
    val isConfigured: Boolean
        get() = channel.isNotBlank() && oauthToken.isNotBlank() && clientId.isNotBlank()
}

data class TwitchStreamInfo(
    val viewerCount: Int,
    val title: String,
    val category: String,
)

class TwitchChannelException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Kapselt die für Vivid benötigten Twitch-Helix-Kanaloperationen.
 *
 * Der Viewer-Zähler wird über `GET /streams` gelesen. Titel und Kategorie
 * werden über `PATCH /channels` gesetzt; der sichtbare Kategoriename wird vor
 * dem Patch über `GET /search/categories` in eine Twitch-Game-ID aufgelöst.
 * Der OAuth-Token benötigt für den Patch den Scope `channel:manage:broadcast`.
 */
@Singleton
class TwitchChannelClient @Inject constructor(
    private val http: HttpClient,
    private val whisperClient: TwitchWhisperClient,
) {
    suspend fun getStreamInfo(config: TwitchChannelConfig): TwitchStreamInfo? {
        if (!config.isConfigured) {
            throw TwitchChannelException(
                "Twitch-Kanal ist nicht konfiguriert. Benötigt werden Kanal, OAuth-Token und Client-ID.",
            )
        }
        val broadcasterId = resolveBroadcasterId(config) ?: return null
        val response = try {
            http.get("$HELIX_API/streams") {
                parameter("user_id", broadcasterId)
                auth(config)
            }
        } catch (error: Exception) {
            throw TwitchChannelException("Twitch-Viewerzahl konnte nicht geladen werden.", error)
        }
        if (!response.status.isSuccess()) {
            throw TwitchChannelException(describeError(response.status.value, "Viewerzahl"))
        }
        val stream = response.body<TwitchStreamsResponse>().data.firstOrNull() ?: return null
        return TwitchStreamInfo(
            viewerCount = stream.viewer_count,
            title = stream.title,
            category = stream.game_name,
        )
    }

    suspend fun updateChannelInfo(
        config: TwitchChannelConfig,
        title: String,
        category: String,
    ) {
        if (!config.isConfigured) {
            throw TwitchChannelException(
                "Twitch-Kanal ist nicht konfiguriert. Benötigt werden Kanal, OAuth-Token und Client-ID.",
            )
        }
        val broadcasterId = resolveBroadcasterId(config)
            ?: throw TwitchChannelException("Twitch-Kanal ${config.channel} konnte nicht aufgelöst werden.")
        val trimmedTitle = title.trim().take(MAX_TITLE_LENGTH)
        val trimmedCategory = category.trim().take(MAX_CATEGORY_LENGTH)
        if (trimmedTitle.isEmpty() && trimmedCategory.isEmpty()) {
            throw TwitchChannelException("Titel und Kategorie dürfen nicht beide leer sein.")
        }
        val gameId = if (trimmedCategory.isEmpty()) {
            null
        } else {
            resolveCategoryId(config, trimmedCategory)
                ?: throw TwitchChannelException("Twitch-Kategorie \"$trimmedCategory\" wurde nicht gefunden.")
        }
        val response = try {
            http.patch("$HELIX_API/channels") {
                parameter("broadcaster_id", broadcasterId)
                auth(config)
                contentType(ContentType.Application.Json)
                setBody(
                    TwitchChannelUpdateRequest(
                        game_id = gameId,
                        title = trimmedTitle.ifEmpty { null },
                    ),
                )
            }
        } catch (error: TwitchChannelException) {
            throw error
        } catch (error: Exception) {
            throw TwitchChannelException("Twitch-Kanalinformationen konnten nicht gesetzt werden.", error)
        }
        if (!response.status.isSuccess()) {
            throw TwitchChannelException(describeError(response.status.value, "Kanalinformationen"))
        }
    }

    private suspend fun resolveBroadcasterId(config: TwitchChannelConfig): String? =
        whisperClient.resolveUserId(
            TwitchWhisperConfig(
                botLogin = config.channel,
                oauthToken = config.oauthToken,
                clientId = config.clientId,
            ),
            config.channel,
        )

    private suspend fun resolveCategoryId(config: TwitchChannelConfig, category: String): String? {
        val response = try {
            http.get("$HELIX_API/search/categories") {
                parameter("query", category)
                parameter("first", 10)
                auth(config)
            }
        } catch (error: Exception) {
            throw TwitchChannelException("Twitch-Kategorie konnte nicht gesucht werden.", error)
        }
        if (!response.status.isSuccess()) {
            throw TwitchChannelException(describeError(response.status.value, "Kategoriesuche"))
        }
        val categories = response.body<TwitchCategoriesResponse>().data
        return categories.firstOrNull { it.name.equals(category, ignoreCase = true) }?.id
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(config: TwitchChannelConfig) {
        header(HttpHeaders.Authorization, "Bearer ${config.oauthToken.trim().removePrefix("oauth:")}")
        header(CLIENT_ID_HEADER, config.clientId.trim())
    }

    private fun describeError(code: Int, operation: String): String = when (code) {
        401 -> "Twitch-Authentifizierung für $operation fehlgeschlagen (401). OAuth-Token oder Client-ID prüfen."
        403 -> "Twitch verweigert $operation (403). Für Änderungen wird channel:manage:broadcast benötigt."
        429 -> "Twitch-Rate-Limit für $operation erreicht (429)."
        else -> "Twitch-$operation fehlgeschlagen (HTTP $code)."
    }

    companion object {
        private const val HELIX_API = "https://api.twitch.tv/helix"
        private const val CLIENT_ID_HEADER = "Client-Id"
        private const val MAX_TITLE_LENGTH = 140
        private const val MAX_CATEGORY_LENGTH = 100
    }
}

@Serializable
private data class TwitchStreamsResponse(val data: List<TwitchStream> = emptyList())

@Serializable
private data class TwitchStream(
    val viewer_count: Int = 0,
    val title: String = "",
    val game_name: String = "",
)

@Serializable
private data class TwitchCategoriesResponse(val data: List<TwitchCategory> = emptyList())

@Serializable
private data class TwitchCategory(
    val id: String,
    val name: String,
)

@Serializable
private data class TwitchChannelUpdateRequest(
    val game_id: String? = null,
    val title: String? = null,
)
