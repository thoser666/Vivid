package com.vivid.feature.chat.bot

/**
 * Owner-Moderation des Chats (`!ban` / `!timeout` / `!delete`) — entkoppelt,
 * damit die Engine (feature-chat) nicht an den Twitch-Client hängt: Die
 * konkrete Implementierung ([NoOpChatModeration] in Tests/ohne Bindung,
 * `TwitchModerationClient` in der App) liefert die fertige Chat-Antwort als
 * Text zurück — die Engine entscheidet nur noch über das Owner-Gate, die
 * Limits und den Antwortweg (öffentlich/Whisper).
 *
 * Jede Methode liefert die **vollständige Antwort** für den Chat (Bestätigung
 * oder Fehlermeldung) und wirft nur bei Transport-/API-Fehlern eine Exception
 * (die Engine fängt sie und antwortet mit einem Fehlerhinweis).
 */
interface ChatModeration {
    /** `!ban <user>` — verbannen (permanent). */
    suspend fun ban(userLogin: String): String

    /** `!timeout <user> <minuten?>` — Timeout (default 5 Minuten). */
    suspend fun timeout(userLogin: String, durationMinutes: Int?): String

    /** `!delete <anzahl?>` — die letzten [count] Nachrichten löschen (null = alle getrackten). */
    suspend fun deleteRecent(count: Int?, recentMessageIds: List<String>): String
}

/** Fallback, wenn keine Implementierung an die Engine übergeben wurde. */
object NoOpChatModeration : ChatModeration {
    override suspend fun ban(userLogin: String): String = NOT_AVAILABLE_TEXT
    override suspend fun timeout(userLogin: String, durationMinutes: Int?): String = NOT_AVAILABLE_TEXT
    override suspend fun deleteRecent(count: Int?, recentMessageIds: List<String>): String = NOT_AVAILABLE_TEXT

    private const val NOT_AVAILABLE_TEXT = "⚠️ Moderation ist nicht verfügbar (keine Implementierung gebunden)."
}
