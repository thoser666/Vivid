package com.vivid.feature.chat.model

/**
 * Chat-Plattform einer [ChatMessage]/Session (Multi-Plattform-Chat, P0 der
 * Architektur-Skizze docs/architecture/multi-platform-chat.md).
 *
 * Der Default [TWITCH] in [ChatMessage.platform] hält alle Bestandstests
 * und Konstruktoren verhaltensneutral — heute erzeugte Nachrichten sind
 * Twitch-Nachrichten; P1 (YouTube) und P2 (Kick) setzen das Feld je Adapter.
 */
enum class ChatPlatform(val id: String) {
    TWITCH("twitch"),
    YOUTUBE("youtube"),
    KICK("kick"),
}
