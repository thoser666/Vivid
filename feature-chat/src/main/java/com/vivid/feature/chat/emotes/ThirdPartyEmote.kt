package com.vivid.feature.chat.emotes

import kotlinx.serialization.Serializable

/**
 * Einheitliches Emote-Modell für Third-Party-Emotes (BTTV, FFZ, 7TV).
 */
@Serializable
data class ThirdPartyEmote(
    val id: String,
    val name: String,
    val url: String,
    val source: EmoteSource,
    val width: Int = 28,
    val height: Int = 28,
    val format: EmoteFormat = EmoteFormat.SVG,
)

/** Quelle des Emotes. */
enum class EmoteSource(val displayName: String) {
    BTTV("BetterTTV"),
    FFZ("FrankerFaceZ"),
    SEVENTV("7TV"),
}

/** Bildformat des Emotes. */
enum class EmoteFormat(val mimeType: String) {
    SVG("image/svg+xml"),
    PNG("image/png"),
    WEBP("image/webp"),
    GIF("image/gif"),
}

/**
 * Cache für Third-Party-Emotes pro Kanal.
 */
class EmoteCache {
    private val cache = mutableMapOf<String, List<ThirdPartyEmote>>()
    private val timestampCache = mutableMapOf<String, Long>()

    /** Cache-Dauer in Millisekunden (5 Minuten). */
    private val cacheDuration = 5 * 60 * 1000L

    /**
     * Gibt gecachte Emotes zurück, wenn der Cache gültig ist.
     */
    fun get(channelId: String): List<ThirdPartyEmote>? {
        val timestamp = timestampCache[channelId] ?: return null
        return if (System.currentTimeMillis() - timestamp < cacheDuration) {
            cache[channelId]
        } else {
            cache.remove(channelId)
            timestampCache.remove(channelId)
            null
        }
    }

    /**
     * Speichert Emotes im Cache.
     */
    fun put(channelId: String, emotes: List<ThirdPartyEmote>) {
        cache[channelId] = emotes
        timestampCache[channelId] = System.currentTimeMillis()
    }

    /**
     * Leert den Cache für einen Kanal.
     */
    fun invalidate(channelId: String) {
        cache.remove(channelId)
        timestampCache.remove(channelId)
    }

    /**
     * Leert den gesamten Cache.
     */
    fun clear() {
        cache.clear()
        timestampCache.clear()
    }

    /** Anzahl der gecachten Kanäle. */
    val size: Int get() = cache.size
}
