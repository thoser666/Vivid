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
)
