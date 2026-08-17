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
    // true, wenn der Absender der Kanal-Inhaber ist (Twitch-Badge „broadcaster/1“) —
    // Grundlage für die Owner-Erkennung (nur der Streamer darf Owner-Befehle nutzen).
    val isBroadcaster: Boolean = false,
)
