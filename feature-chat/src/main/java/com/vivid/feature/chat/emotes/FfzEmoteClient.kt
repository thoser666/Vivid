package com.vivid.feature.chat.emotes

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API-Client für FrankerFaceZ (FFZ) Emotes.
 *
 * API: https://api.frankerfacez.com/v1/user/{userId}
 */
class FfzEmoteClient(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val BASE_URL = "https://api.frankerfacez.com/v1"
    }

    /**
     * Lädt die FFZ-Emotes für einen Twitch-Kanal.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @return Liste der FFZ-Emotes.
     */
    suspend fun getChannelEmotes(channelId: String): List<ThirdPartyEmote> {
        return try {
            val response = httpClient.get("$BASE_URL/user/id/$channelId")
                .body<FfzUserResponse>()

            val emotes = mutableListOf<ThirdPartyEmote>()

            response.sets?.values?.forEach { set ->
                set.emoticons?.forEach { emoticon ->
                    emotes.add(emoticon.toThirdPartyEmote())
                }
            }

            emotes
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class FfzUserResponse(
    @SerialName("sets")
    val sets: Map<String, FfzSet>? = null,
)

@Serializable
data class FfzSet(
    @SerialName("emoticons")
    val emoticons: List<FfzEmoticon>? = null,
)

@Serializable
data class FfzEmoticon(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String,
    @SerialName("width")
    val width: Int = 28,
    @SerialName("height")
    val height: Int = 28,
    @SerialName("urls")
    val urls: Map<String, String>? = null,
) {
    fun toThirdPartyEmote(): ThirdPartyEmote {
        // Bevorzuge 2x, fallback auf 1x
        val url = urls?.get("2")
            ?: urls?.get("1")
            ?: urls?.values?.firstOrNull()
            ?: ""
        return ThirdPartyEmote(
            id = "ffz_$id",
            name = name,
            url = url,
            source = EmoteSource.FFZ,
            width = width,
            height = height,
            format = EmoteFormat.PNG,
        )
    }
}
