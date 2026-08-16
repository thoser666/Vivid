package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotCommandScope
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
 *
 * Der [ChatBotCommandScope] regelt, **wer** Befehle auslösen darf (Koexistenz
 * mit anderen Bots im selben Kanal): [ChatBotCommandScope.ALL] beantwortet
 * jeden `!`-Befehl, [ChatBotCommandScope.MENTION] nur direkt adressierte
 * Befehle (`@vividbot !help`) und [ChatBotCommandScope.PREFIX] nur Befehle
 * mit eigenem Präfix (`!v!help` bei Präfix `v`). Fremde Befehle liefern dann
 * [Result.None] statt [Result.Unknown] — sie gehören dem anderen Bot.
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
     *
     * [scope]/[prefix]/[botLogin] steuern die Koexistenz (siehe Klassen-
     * Kommentar): Standard ist [ChatBotCommandScope.ALL] (jeder Befehl).
     */
    fun handle(
        text: String,
        streamStartedAtMillis: Long?,
        scope: ChatBotCommandScope = ChatBotCommandScope.ALL,
        prefix: String = "",
        botLogin: String = "",
    ): Result {
        val tokens = text.trim().split(Regex("\\s+"))
        if (tokens.size == 1 && tokens[0].isEmpty()) return Result.None

        // PREFIX: Nur `!<prefix>!<befehl>` zählt — generische `!`-Befehle
        // gehören dem anderen Bot und werden ignoriert (None statt Unknown).
        if (scope == ChatBotCommandScope.PREFIX) {
            val p = prefix.trim().removePrefix("!").removeSuffix("!").lowercase()
            if (p.isBlank()) return Result.None
            val token = tokens.firstOrNull { it.lowercase().startsWith("!${p}!") } ?: return Result.None
            val command = token.substring(p.length + 2).lowercase()
            if (command.isBlank()) return Result.None
            return dispatch(command, streamStartedAtMillis, prefix = p)
        }

        // MENTION: Nur wenn der Bot direkt angesprochen wird (Login als Wort,
        // mit oder ohne '@' — z. B. "@vividbot !help").
        if (scope == ChatBotCommandScope.MENTION) {
            val mentioned = botLogin.isNotBlank() &&
                tokens.any { it.trim('@', ':', ',').lowercase() == botLogin.lowercase() }
            if (!mentioned) return Result.None
        }

        // ALL + MENTION: erster `!`-Token in der Nachricht.
        val token = tokens.firstOrNull { it.startsWith("!") } ?: return Result.None
        val command = token.substring(1).lowercase()
        if (command.isBlank()) return Result.None
        return dispatch(command, streamStartedAtMillis, prefix = null)
    }

    private fun dispatch(command: String, startedAt: Long?, prefix: String?): Result =
        when (command) {
            "help", "commands", "hilfe" -> Result.Reply(helpText(prefix))
            "uptime" -> Result.Reply(uptimeReply(startedAt))
            "tts" -> Result.ToggleTts
            "song", "nowplaying", "np" -> Result.MediaNowPlaying
            "next", "skip" -> Result.MediaNext
            "pause" -> Result.MediaPause
            "play" -> Result.MediaPlay
            "prev", "previous" -> Result.MediaPrevious
            "bot" -> Result.Reply(BOT_INFO_TEXT)
            else -> Result.Unknown(command)
        }

    /** Hilfe-Text: Im PREFIX-Scope mit dem eigenen Präfix (z. B. `!v!help`). */
    private fun helpText(prefix: String?): String {
        if (prefix.isNullOrBlank()) return HELP_TEXT
        val p = "!${prefix}!"
        return "Verfügbare Befehle: ${p}help · ${p}uptime · ${p}tts · ${p}song · ${p}next · ${p}pause · ${p}bot"
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
