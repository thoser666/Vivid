package com.vivid.feature.chat.emotes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API-Client für BetterTTV (BTTV) Emotes.
 *
 * API: https://api.betterttv.net/3/cached/users/twitch/{userId}
 */
class BttvEmoteClient(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val BASE_URL = "https://api.betterttv.net/3"
    }

    /**
     * Lädt die BTTV-Emotes für einen Twitch-Kanal.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @return Liste der BTTV-Emotes.
     */
    suspend fun getChannelEmotes(channelId: String): List<ThirdPartyEmote> {
        return try {
            val response = httpClient.get("$BASE_URL/cached/users/twitch/$channelId")
                .body<BttvUserResponse>()

            val emotes = mutableListOf<ThirdPartyEmote>()

            // Channel-Emotes
            response.channelEmotes?.forEach { emote ->
                emotes.add(emote.toThirdPartyEmote())
            }

            // Shared-Emotes
            response.sharedEmotes?.forEach { emote ->
                emotes.add(emote.toThirdPartyEmote())
            }

            emotes
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class BttvUserResponse(
    @SerialName("channelEmotes")
    val channelEmotes: List<BttvEmote>? = null,
    @SerialName("sharedEmotes")
    val sharedEmotes: List<BttvEmote>? = null,
)

@Serializable
data class BttvEmote(
    @SerialName("id")
    val id: String,
    @SerialName("code")
    val code: String,
    @SerialName("imageType")
    val imageType: String = "png",
    @SerialName("width")
    val width: Int = 28,
    @SerialName("height")
    val height: Int = 28,
) {
    fun toThirdPartyEmote(): ThirdPartyEmote {
        val format = when (imageType.lowercase()) {
            "gif" -> EmoteFormat.GIF
            "webp" -> EmoteFormat.WEBP
            "svg" -> EmoteFormat.SVG
            else -> EmoteFormat.PNG
        }
        return ThirdPartyEmote(
            id = "bttv_$id",
            name = code,
            url = "https://cdn.betterttv.net/emote/$id/2x.$imageType",
            source = EmoteSource.BTTV,
            width = width,
            height = height,
            format = format,
        )
    }
}
