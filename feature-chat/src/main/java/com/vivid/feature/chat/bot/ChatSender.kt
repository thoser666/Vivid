package com.vivid.feature.chat.bot

/** Abstraktion für das Senden einer Chat-Nachricht (z. B. der Twitch-Bot-Client). */
fun interface ChatSender {
    suspend fun send(text: String)
}
