package com.vivid.feature.chat.bot

/**
 * Abstraktion für das Senden von Bot-Nachrichten (z. B. der Twitch-Bot-Client).
 *
 * - [send] schreibt öffentlich in den Kanal (PRIVMSG).
 * - [sendWhisper] sendet privat an einen Login (Twitch-Whisper via Helix-API)
 *   und wirft bei Fehlern eine Exception — die Engine entscheidet, wann der
 *   private Antwortweg genutzt wird und fällt bei Fehlern auf [send] zurück.
 */
interface ChatSender {
    suspend fun send(text: String)

    suspend fun sendWhisper(toLogin: String, text: String)
}
