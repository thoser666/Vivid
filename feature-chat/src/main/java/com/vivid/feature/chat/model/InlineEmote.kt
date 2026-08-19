package com.vivid.feature.chat.model

/**
 * Ein inline gerendertes Twitch-Emote innerhalb einer Chat-Nachricht.
 *
 * @param id Die Twitch-Emote-ID (z. B. "30259" für Kappa).
 * @param start Startindex (inklusiv) im [ChatMessage.text]-String.
 * @param end Endindex (inklusiv) im [ChatMessage.text]-String.
 * @param scale Bildskalierung (1.0, 2.0 oder 3.0) für die CDN-URL.
 */
data class InlineEmote(
    val id: String,
    val start: Int,
    val end: Int,
    val scale: Double = 2.0,
) {
    /** CDN-URL des Emote-Bildes (Twitch-Standardformat). */
    val url: String
        get() = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/${scale.toInt()}.0"

    companion object {
        private const val DEFAULT_SCALE = 2.0

        /**
         * Parst das IRC `emotes=`-Tag-Format in eine sortierte Liste von [InlineEmote]s.
         *
         * Format: `"id:start-end,start2-end2/id2:start3-end3"`.
         * Die Ranges sind 0-basiert, inklsuiv auf beiden Seiten.
         */
        fun parseFromEmotesTag(emotesTag: String, scale: Double = DEFAULT_SCALE): List<InlineEmote> {
            if (emotesTag.isBlank()) return emptyList()
            return emotesTag.split("/").flatMap { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@flatMap emptyList()
                val id = parts[0].trim()
                if (id.isBlank()) return@flatMap emptyList()
                parts[1].split(",").mapNotNull { range ->
                    val bounds = range.split("-")
                    if (bounds.size != 2) return@mapNotNull null
                    val start = bounds[0].toIntOrNull() ?: return@mapNotNull null
                    val end = bounds[1].toIntOrNull() ?: return@mapNotNull null
                    if (end < start) return@mapNotNull null
                    InlineEmote(id = id, start = start, end = end, scale = scale)
                }
            }.sortedBy { it.start }
        }
    }
}
