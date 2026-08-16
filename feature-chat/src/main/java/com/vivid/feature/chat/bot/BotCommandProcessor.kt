package com.vivid.feature.chat.bot

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministischer Chat-Befehl-Prozessor — der „Bot wie Moblin"-Teil des
 * Chat-Bots. Erkennt `!`-Befehle in einer Nachricht und liefert feste,
 * vorhersehbare Antworten, **ohne** ein LLM anzufragen.
 *
 * Befehle sind case-insensitive und können mitten in der Nachricht stehen
 * (z. B. `@vividbot !help`). Unbekannte Befehle werden als [Result.Unknown]
 * gemeldet — im COMMAND-Modus antwortet die Engine mit einem Hinweis, im
 * AUTONOMOUS-Modus darf die KI darüber entscheiden.
 */
@Singleton
class BotCommandProcessor @Inject constructor() {

    /** Uhrenfunktion (für Tests ersetzbar). */
    internal var now: () -> Long = System::currentTimeMillis

    sealed interface Result {
        /** Bekannter Befehl mit deterministischer Antwort. */
        data class Reply(val text: String) : Result

        /** `!tts` — schaltet das Chat-Vorlesen (Text-to-Speech) um. */
        data object ToggleTts : Result

        /** `!song` / `!nowplaying` — aktueller Titel. */
        data object MediaNowPlaying : Result

        /** `!next` / `!skip` — nächster Titel. */
        data object MediaNext : Result

        /** `!pause` — Wiedergabe pausieren. */
        data object MediaPause : Result

        /** `!play` — Wiedergabe fortsetzen. */
        data object MediaPlay : Result

        /** `!prev` / `!previous` — vorheriger Titel. */
        data object MediaPrevious : Result

        /** Mit `!` beginnendes Token, aber kein bekannter Befehl. */
        data class Unknown(val command: String) : Result

        /** Kein Befehl in der Nachricht. */
        data object None : Result
    }

    /**
     * Verarbeitet eine Chat-Nachricht. [streamStartedAtMillis] ist der
     * Zeitstempel des Stream-Starts (0/null = kein aktiver Stream).
     */
    fun handle(text: String, streamStartedAtMillis: Long?): Result {
        val token = text
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull { it.startsWith("!") }
            ?: return Result.None
        val command = token.substring(1).lowercase()
        if (command.isBlank()) return Result.None
        return when (command) {
            "help", "commands", "hilfe" -> Result.Reply(HELP_TEXT)
            "uptime" -> Result.Reply(uptimeReply(streamStartedAtMillis))
            "tts" -> Result.ToggleTts
            "song", "nowplaying", "np" -> Result.MediaNowPlaying
            "next", "skip" -> Result.MediaNext
            "pause" -> Result.MediaPause
            "play" -> Result.MediaPlay
            "prev", "previous" -> Result.MediaPrevious
            "bot" -> Result.Reply(BOT_INFO_TEXT)
            else -> Result.Unknown(command)
        }
    }

    private fun uptimeReply(startedAt: Long?): String {
        if (startedAt == null || startedAt <= 0L) {
            return "Gerade läuft kein Stream."
        }
        val seconds = ((now() - startedAt) / 1000).coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "Der Stream läuft seit ${hours}h ${minutes}m ${secs}s."
    }

    companion object {
        const val HELP_TEXT = "Verfügbare Befehle: !help · !uptime · !tts · !song · !next · !pause · !bot"
        const val BOT_INFO_TEXT = "Ich bin der Chat-Bot von Vivid 🤖 — alle Befehle: !help"
    }
}
