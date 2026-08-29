package com.vivid.feature.chat.emotes

import timber.log.Timber
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentraler Service für Third-Party-Emotes (BTTV, FFZ, 7TV).
 *
 * Lädt Emotes pro Kanal, cached sie und stellt sie für das Chat-Overlay bereit.
 */
@Singleton
class ThirdPartyEmoteService @Inject constructor() {
    companion object {
        private const val TAG = "ThirdPartyEmoteService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val bttvClient = BttvEmoteClient(httpClient)
    private val ffzClient = FfzEmoteClient(httpClient)
    private val sevenTvClient = SevenTvEmoteClient(httpClient)

    private val cache = EmoteCache()

    private val _emotes = MutableStateFlow<Map<String, List<ThirdPartyEmote>>>(emptyMap())
    val emotes: StateFlow<Map<String, List<ThirdPartyEmote>>> = _emotes.asStateFlow()

    /** Aktiviert/Deaktiviert Third-Party-Emotes. */
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Welche Quellen aktiv sind. */
    private val _activeSources = MutableStateFlow(setOf(EmoteSource.BTTV, EmoteSource.FFZ, EmoteSource.SEVENTV))
    val activeSources: StateFlow<Set<EmoteSource>> = _activeSources.asStateFlow()

    /**
     * Lädt Third-Party-Emotes für einen Kanal.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @param forceUpdate Cache ignorieren und neu laden.
     */
    fun loadEmotes(channelId: String, forceUpdate: Boolean = false) {
        if (!_enabled.value) return

        // Cache prüfen
        if (!forceUpdate) {
            val cached = cache.get(channelId)
            if (cached != null) {
                _emotes.value = _emotes.value + (channelId to cached)
                return
            }
        }

        scope.launch {
            val allEmotes = mutableListOf<ThirdPartyEmote>()
            val sources = _activeSources.value

            if (EmoteSource.BTTV in sources) {
                allEmotes.addAll(bttvClient.getChannelEmotes(channelId))
            }
            if (EmoteSource.FFZ in sources) {
                allEmotes.addAll(ffzClient.getChannelEmotes(channelId))
            }
            if (EmoteSource.SEVENTV in sources) {
                allEmotes.addAll(sevenTvClient.getChannelEmotes(channelId))
            }

            // Nach Name sortieren
            val sorted = allEmotes.sortedBy { it.name.lowercase() }

            cache.put(channelId, sorted)
            _emotes.value = _emotes.value + (channelId to sorted)

            Timber.d(TAG, "Loaded ${sorted.size} third-party emotes for channel $channelId")
        }
    }

    /**
     * Sucht ein Emote nach Namen.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @param name Der Emote-Name (z.B. "BibleThump").
     * @return Das gefundene Emote oder null.
     */
    fun findEmote(channelId: String, name: String): ThirdPartyEmote? {
        return _emotes.value[channelId]?.find {
            it.name.equals(name, ignoreCase = true)
        }
    }

    /**
     * Parst einen Chat-Text und ersetzt Emote-Namen durch ThirdPartyEmote-Objekte.
     *
     * @param channelId Die Twitch-User-ID des Kanals.
     * @param text Der Chat-Text.
     * @return Liste von Text-Segmenten und Emotes.
     */
    fun parseMessage(channelId: String, text: String): List<EmoteSegment> {
        val segments = mutableListOf<EmoteSegment>()
        val channelEmotes = _emotes.value[channelId] ?: return listOf(EmoteSegment.Text(text))

        // Emote-Namen nach Länge absteigend sortieren (längste zuerst)
        val sortedEmotes = channelEmotes.sortedByDescending { it.name.length }

        var remaining = text
        while (remaining.isNotEmpty()) {
            var found = false
            for (emote in sortedEmotes) {
                if (remaining.startsWith(emote.name, ignoreCase = true)) {
                    segments.add(EmoteSegment.Emote(emote))
                    remaining = remaining.removePrefix(emote.name).removePrefix(" ")
                    found = true
                    break
                }
            }
            if (!found) {
                // Nächstes Emote oder Rest-Text finden
                val nextEmoteIndex = sortedEmotes.minOfOrNull { emote ->
                    remaining.indexOf(emote.name, ignoreCase = true).takeIf { it >= 0 } ?: Int.MAX_VALUE
                } ?: Int.MAX_VALUE

                if (nextEmoteIndex == Int.MAX_VALUE) {
                    segments.add(EmoteSegment.Text(remaining))
                    break
                } else if (nextEmoteIndex > 0) {
                    segments.add(EmoteSegment.Text(remaining.substring(0, nextEmoteIndex)))
                    remaining = remaining.removeRange(0, nextEmoteIndex)
                }
            }
        }

        return segments.ifEmpty { listOf(EmoteSegment.Text(text)) }
    }

    /** Third-Party-Emotes aktivieren/deaktivieren. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) {
            cache.clear()
            _emotes.value = emptyMap()
        }
    }

    /** Aktive Quellen setzen. */
    fun setActiveSources(sources: Set<EmoteSource>) {
        _activeSources.value = sources
    }

    /** Cache für einen Kanal leeren. */
    fun invalidateCache(channelId: String) {
        cache.invalidate(channelId)
    }
}

/**
 * Ein Segment eines Chat-Messages (Text oder Emote).
 */
sealed class EmoteSegment {
    data class Text(val text: String) : EmoteSegment()
    data class Emote(val emote: ThirdPartyEmote) : EmoteSegment()
}
