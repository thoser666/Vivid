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
    // true, wenn die Nachricht eine /me-Aktion ist ("* User tut etwas *").
    val isAction: Boolean = false,
    // Reaktions-Antwort: Wenn die Nachricht eine Antwort auf eine andere Nachricht ist,
    // enthält dieses Feld die ID der Eltern-Nachricht. Die Bot-Engine kann damit
    // Reaktions-Ketten erkennen (z. B. für gepunktete Antworten).
    val replyParentMessageId: String? = null,
    // Display-Name des Users, auf den geantwortet wird (nur bei Reply gesetzt).
    val replyParentUserLogin: String? = null,
    // Klartext der Eltern-Nachricht (nur bei Reply gesetzt, gekürzt auf 80 Zeichen).
    val replyParentMessagePreview: String? = null,
    // Anzahl der Bits, die mit dieser Nachricht geschickt wurden (Cheer). 0 = kein Cheer.
    val bitsAmount: Int = 0,
    // true, wenn die Nachricht als gelöscht markiert wurde (EventSub: channel.chat.message_delete).
    val isDeleted: Boolean = false,
)
