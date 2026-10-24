package com.vivid.feature.chat.twitch

import androidx.annotation.StringRes
import com.vivid.feature.chat.R
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

/**
 * Operationale Fehler der Helix-Kanaloperationen.
 *
 * @param message technisches Detail (Englisch, i18n-plan §4: Logs sind Englisch)
 * @param messageRes Ressourcen-ID (`@StringRes`) der lokalisierten UI-Meldung
 *   (i18n-plan §4: eigene Fehlerursachen sind Ressourcen) — vom Konsumenten
 *   erst bei der Anzeige aufzulösen; `0`, wenn keine vorliegt.
 * @param formatArgs Platzhalter-Argumente für [messageRes]
 */
class TwitchChannelException(
    override val message: String,
    cause: Throwable? = null,
    val statusCode: Int? = null,
    @StringRes val messageRes: Int = 0,
    val formatArgs: List<String> = emptyList(),
) : Exception(message, cause)

/**
 * Kapselt die für Vivid benötigten Twitch-Helix-Kanaloperationen.
 *
 * Der Viewer-Zähler wird über `GET /streams` gelesen. Titel und Kategorie
 * werden über `PATCH /channels` gesetzt; der sichtbare Kategoriename wird vor
 * dem Patch über `GET /search/categories` in eine Twitch-Game-ID aufgelöst.
 * Der OAuth-Token benötigt für den Patch den Scope `channel:manage:broadcast`.
 *
 * Bei `401` versucht der Client, das Access-Token über den gespeicherten
 * Refresh-Token ([TwitchTokenStore]) zu erneuern und führt den Request einmal
 * mit dem frischen Token aus.
 */
@Singleton
class TwitchChannelClient @Inject constructor(
    private val http: HttpClient,
    private val whisperClient: TwitchWhisperClient,
    private val tokenStore: TwitchTokenStore,
) {
    suspend fun getStreamInfo(config: TwitchChannelConfig): TwitchStreamInfo? =
        withTokenRefresh(config) { effective ->
            if (!effective.isConfigured) {
                throw TwitchChannelException(
                    "twitch channel not configured",
                    messageRes = R.string.twitch_channel_error_client_not_configured,
                )
            }
            val broadcasterId = resolveBroadcasterId(effective) ?: return@withTokenRefresh null
            val response = try {
                http.get("$HELIX_API/streams") {
                    parameter("user_id", broadcasterId)
                    auth(effective)
                }
            } catch (error: Exception) {
                throw TwitchChannelException(
                    "viewer count request failed",
                    error,
                    messageRes = R.string.twitch_channel_error_viewer_count_failed,
                )
            }
            if (!response.status.isSuccess()) {
                throw httpError(response.status.value, "viewer count")
            }
            val stream = response.body<TwitchStreamsResponse>().data.firstOrNull() ?: return@withTokenRefresh null
            TwitchStreamInfo(
                viewerCount = stream.viewer_count,
                title = stream.title,
                category = stream.game_name,
            )
        }

    suspend fun updateChannelInfo(
        config: TwitchChannelConfig,
        title: String,
        category: String,
    ) = withTokenRefresh(config) { effective ->
        if (!effective.isConfigured) {
            throw TwitchChannelException(
                "twitch channel not configured",
                messageRes = R.string.twitch_channel_error_client_not_configured,
            )
        }
        val broadcasterId = resolveBroadcasterId(effective)
            ?: throw TwitchChannelException(
                "channel lookup failed for ${effective.channel}",
                messageRes = R.string.twitch_channel_error_channel_resolve_failed,
                formatArgs = listOf(effective.channel),
            )
        val trimmedTitle = title.trim().take(MAX_TITLE_LENGTH)
        val trimmedCategory = category.trim().take(MAX_CATEGORY_LENGTH)
        if (trimmedTitle.isEmpty() && trimmedCategory.isEmpty()) {
            throw TwitchChannelException(
                "title and category both empty",
                messageRes = R.string.twitch_channel_error_empty_fields,
            )
        }
        val gameId = if (trimmedCategory.isEmpty()) {
            null
        } else {
            resolveCategoryId(effective, trimmedCategory)
                ?: throw TwitchChannelException(
                    "category not found: $trimmedCategory",
                    messageRes = R.string.twitch_channel_error_category_not_found,
                    formatArgs = listOf(trimmedCategory),
                )
        }
        val response = try {
            http.patch("$HELIX_API/channels") {
                parameter("broadcaster_id", broadcasterId)
                auth(effective)
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
            throw TwitchChannelException(
                "channel update failed",
                error,
                messageRes = R.string.twitch_channel_error_save_failed,
            )
        }
        if (!response.status.isSuccess()) {
            throw httpError(response.status.value, "channel info")
        }
    }

    /**
     * Führt [block] aus; bei `401` wird über den Refresh-Token ein neues
     * Access-Token geholt, in den [TwitchTokenStore] geschrieben und [block]
     * einmal mit dem frischen Token wiederholt. Ohne Refresh-Möglichkeit
     * wird der ursprüngliche Fehler weitergereicht.
     */
    private suspend fun <T> withTokenRefresh(
        config: TwitchChannelConfig,
        block: suspend (TwitchChannelConfig) -> T,
    ): T {
        try {
            return block(config)
        } catch (error: TwitchChannelException) {
            if (error.statusCode != HttpStatusCode.Unauthorized.value) throw error
            val refreshed = refreshAccessToken(config)
                ?: throw error
            try {
                return block(config.copy(oauthToken = refreshed))
            } catch (retryError: TwitchChannelException) {
                throw error
            }
        }
    }

    private suspend fun refreshAccessToken(config: TwitchChannelConfig): String? {
        val session = tokenStore.loadSession() ?: return null
        if (session.refreshToken.isBlank()) return null
        return try {
            val response = TwitchOAuth.refreshAccessToken(
                http,
                TwitchOAuthRefreshRequest(
                    clientId = config.clientId.trim(),
                    refreshToken = session.refreshToken,
                ),
            )
            val fresh = TwitchTokenSession.from(
                response,
                nowMillis = System.currentTimeMillis(),
            )
            tokenStore.saveSession(fresh)
            response.accessToken
        } catch (error: TwitchOAuthException) {
            null
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
            throw TwitchChannelException(
                "category search failed",
                error,
                messageRes = R.string.twitch_channel_error_category_search_failed,
            )
        }
        if (!response.status.isSuccess()) {
            throw httpError(response.status.value, "category search")
        }
        val categories = response.body<TwitchCategoriesResponse>().data
        return categories.firstOrNull { it.name.equals(category, ignoreCase = true) }?.id
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(config: TwitchChannelConfig) {
        header(HttpHeaders.Authorization, "Bearer ${config.oauthToken.trim().removePrefix("oauth:")}")
        header(CLIENT_ID_HEADER, config.clientId.trim())
    }

    /**
     * Maps a failed Helix response onto the localized UI resource
     * (i18n-plan §4: own cause = resource, resolved at display time).
     * [operation] stays an English technical token for logs and stack traces.
     */
    private fun httpError(code: Int, operation: String): TwitchChannelException {
        val messageRes = when (code) {
            401 -> R.string.twitch_channel_error_http_401
            403 -> R.string.twitch_channel_error_http_403
            429 -> R.string.twitch_channel_error_http_429
            else -> R.string.twitch_channel_error_http
        }
        // Nur der generische Fehlertext trägt den HTTP-Code als Platzhalter.
        val formatArgs = if (code == 401 || code == 403 || code == 429) {
            emptyList()
        } else {
            listOf(code.toString())
        }
        return TwitchChannelException(
            "twitch http $code ($operation)",
            statusCode = code,
            messageRes = messageRes,
            formatArgs = formatArgs,
        )
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
