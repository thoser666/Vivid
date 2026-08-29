package com.vivid.feature.chat.emotes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API-Client für 7TV Emotes.
 *
 * API: https://7tv.io/v3/users/twitch/{userId}
 */
class SevenTvEmoteClient(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val BASE_URL = "https://7tv.io/v3"
    }

    /**
     * Lädt die 7TV-Emotes für einen Twitch-Kanal.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @return Liste der 7TV-Emotes.
     */
    suspend fun getChannelEmotes(channelId: String): List<ThirdPartyEmote> {
        return try {
            val response = httpClient.get("$BASE_URL/users/twitch/$channelId")
                .body<SevenTvUserResponse>()

            val emotes = mutableListOf<ThirdPartyEmote>()

            response.emoteSet?.emotes?.forEach { emote ->
                emotes.add(emote.toThirdPartyEmote())
            }

            emotes
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class SevenTvUserResponse(
    @SerialName("emote_set")
    val emoteSet: SevenTvEmoteSet? = null,
)

@Serializable
data class SevenTvEmoteSet(
    @SerialName("emotes")
    val emotes: List<SevenTvEmote>? = null,
)

@Serializable
data class SevenTvEmote(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("width")
    val width: Int = 28,
    @SerialName("height")
    val height: Int = 28,
    @SerialName("animated")
    val animated: Boolean = false,
    @SerialName("urls")
    val urls: List<List<String>>? = null,
) {
    fun toThirdPartyEmote(): ThirdPartyEmote {
        // 7TV liefert URLs als [[size, url], [size, url]]
        val url = urls?.lastOrNull()?.lastOrNull()
            ?: "https://cdn.7tv.app/emote/$id/2x.webp"
        val format = if (animated) EmoteFormat.GIF else EmoteFormat.WEBP
        return ThirdPartyEmote(
            id = "7tv_$id",
            name = name,
            url = url,
            source = EmoteSource.SEVENTV,
            width = width,
            height = height,
            format = format,
        )
    }
}
