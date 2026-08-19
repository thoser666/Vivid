package com.vivid.feature.chat.model

data class ChatMessage(
    val id: String,
    val channel: String,
    val userId: String,
    val userLogin: String,
    val displayName: String,
    val color: String?,
    val text: String,
    val badges: List<String>,
    val emotesTag: String,
    val timestamp: Long,
    val isModerator: Boolean,
    val isSubscriber: Boolean,
    // true, wenn der Absender der Kanal-Inhaber ist (Twitch-Badge „broadcaster/1") —
    // Grundlage für die Owner-Erkennung (nur der Streamer darf Owner-Befehle nutzen).
    val isBroadcaster: Boolean = false,
    // true, wenn die Nachricht privat per Twitch-Whisper (EventSub) statt im
    // Kanal eingegangen ist — nur Owner-Befehle werden beantwortet, und die
    // Antwort geht als Whisper zurück (nie öffentlich).
    val isWhisper: Boolean = false,
    // Strukturierte Inline-Emotes (Twitch CDN) — geparst aus den EventSub-Fragments.
    // Wird vom Chat-Overlay gerendert; die Bot-Engine nutzt weiterhin [emotesTag].
    val inlineEmotes: List<InlineEmote> = emptyList(),
)
