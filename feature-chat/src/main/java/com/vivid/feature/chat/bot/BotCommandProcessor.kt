package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotCommandScope
import com.vivid.feature.chat.model.ChatAlertType
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

        /** `!ban <user>` — Viewer verbannen (Owner-only). */
        data class Ban(val userLogin: String) : Result

        /** `!timeout <user> <duration?>` — Viewer timeouten (Owner-only).
         *  duration ist optional in Minuten (z. B. `!timeout mod 10` für 10 Min).
         *  Wird keine Dauer angegeben, gilt der Standard-Cooldown (z. B. 5 Min). */
        data class Timeout(val userLogin: String, val durationMinutes: Int?) : Result

        /** `!delete <count?>` — Nachrichten löschen (Owner-only).
         *  count ist optional (z. B. `!delete 5` für die letzten 5 Nachrichten). */
        data class Delete(val count: Int?) : Result

        /** `!start` / `!go-live` — Stream starten (nur Owner). */
        data object OwnerStart : Result

        /** `!stop` / `!end` — Stream stoppen (nur Owner). */
        data object OwnerStop : Result

        /** `!diag` / `!status` — Diagnose-Lauf (nur Owner). */
        data object OwnerDiagnose : Result

        /** `!ask <frage>` — Frage an die Owner-KI (nur Owner). */
        data class OwnerAsk(val text: String) : Result

        /** `!testalert <follow|sub|raid>` — Test-Alert für das Overlay (nur Owner).
         *  type ist null bei fehlendem/ungültigem Typ → Engine antwortet mit Nutzungs-Hinweis. */
        data class TestAlert(val type: ChatAlertType?) : Result

        /** `!torch` — Taschenlampe umschalten (nur Owner). */
        data object OwnerTorch : Result

        /** `!fix` — auto-fixbare Probleme beheben (nur Owner). */
        data object OwnerFix : Result

        /** `!filter [name]` — Video-Effekt umschalten/anzeigen (nur Owner).
         *  Ohne Argument → nächster Filter; mit Name → spezifischer Filter. */
        data class Filter(val filterName: String?) : Result

        /** `!boost` — Low-Light-Boost umschalten (nur Owner). */
        data object OwnerBoost : Result

        /** `!battery` — Akkustand anzeigen (nur Owner). */
        data object OwnerBattery : Result

        /** `!lut [warm|cool|none]` — 3D-LUT-Preset wechseln (nur Owner).
         *  Ohne Argument → nächster Preset; mit Name → spezifischer Preset. */
        data class Lut(val presetName: String?) : Result

        /** `!colorspace [srgb|p3|log]` — Color-Space wechseln (nur Owner). */
        data class ColorSpace(val spaceName: String?) : Result

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
            val rest = tokens.drop(tokens.indexOf(token) + 1).joinToString(" ")
            return dispatch(command, streamStartedAtMillis, prefix = p, rest = rest)
        }

        // MENTION: Nur wenn der Bot direkt angesprochen wird (Login als Wort,
        // mit oder ohne '@' — z. B. "@vividbot !help").
        if (scope == ChatBotCommandScope.MENTION) {
            val mentioned = botLogin.isNotBlank() &&
                tokens.any { it.trim('@', ':', ',').lowercase() == botLogin.lowercase() }
            if (!mentioned) return Result.None
        }

        // ALL + MENTION: erster `!`-Token in der Nachricht. Der Rest sind die
        // Tokens NACH dem Befehlstoken (der kann mitten in der Nachricht
        // stehen, z. B. `@vividbot !ban troll1` → Rest = `troll1`).
        val token = tokens.firstOrNull { it.startsWith("!") } ?: return Result.None
        val command = token.substring(1).lowercase()
        if (command.isBlank()) return Result.None
        val rest = tokens.drop(tokens.indexOf(token) + 1).joinToString(" ")
        return dispatch(command, streamStartedAtMillis, prefix = null, rest = rest)
    }

    private fun dispatch(command: String, startedAt: Long?, prefix: String?, rest: String = ""): Result =
        when (command) {
            "help", "commands", "hilfe" -> Result.Reply(helpText(prefix))
            "uptime" -> Result.Reply(uptimeReply(startedAt))
            "tts" -> Result.ToggleTts
            "song", "nowplaying", "np" -> Result.MediaNowPlaying
            "next", "skip" -> Result.MediaNext
            "pause" -> Result.MediaPause
            "play" -> Result.MediaPlay
            "prev", "previous" -> Result.MediaPrevious
            // Owner-Befehle (nur der Streamer; das Gate liegt in der Engine).
            "start", "go-live", "go_live", "livestart" -> Result.OwnerStart
            "stop", "end", "shutdown" -> Result.OwnerStop
            "diag", "diagnose", "status" -> Result.OwnerDiagnose
            "ask" -> Result.OwnerAsk(rest.trim())
            "testalert", "test-alert", "alert" -> Result.TestAlert(parseAlertType(rest))
            // Owner-Befehl: Taschenlampe umschalten.
            "torch", "lantern", "flashlight" -> Result.OwnerTorch
            // Owner-Befehl: auto-fixbare Probleme beheben.
            "fix" -> Result.OwnerFix
            // Owner-Befehl: Video-Effekt umschalten/anzeigen.
            "filter", "fx" -> Result.Filter(firstToken(rest).ifBlank { null })
            // Owner-Befehl: Low-Light-Boost umschalten.
            "boost", "lowlight", "low-light" -> Result.OwnerBoost
            // Owner-Befehl: Akkustand anzeigen.
            "battery", "akku" -> Result.OwnerBattery
            // Owner-Befehl: 3D-LUT-Preset wechseln.
            "lut" -> Result.Lut(firstToken(rest).ifBlank { null })
            // Owner-Befehl: Color-Space wechseln.
            "colorspace", "color-space", "cs" -> Result.ColorSpace(firstToken(rest).ifBlank { null })
            "ban" -> Result.Ban(firstToken(rest).removePrefix("@"))
            "timeout" -> Result.Timeout(firstToken(rest).removePrefix("@"), parseTimeoutDuration(rest))
            "delete" -> Result.Delete(firstToken(rest).toIntOrNull())
            "bot" -> Result.Reply(BOT_INFO_TEXT)
            else -> Result.Unknown(command)
        }

    /** Hilfe-Text: Im PREFIX-Scope mit dem eigenen Präfix (z. B. `!v!help`). */
    private fun helpText(prefix: String?): String {
        if (prefix.isNullOrBlank()) return HELP_TEXT
        val p = "!${prefix}!"
        return "Verfügbare Befehle: ${p}help · ${p}uptime · ${p}tts · ${p}song · ${p}next · ${p}pause · ${p}bot · ${p}testalert · ${p}torch · ${p}filter · ${p}boost · ${p}battery · ${p}lut · ${p}colorspace"
    }

    /**
     * Erstes Token des Rest-Strings als Alert-Typ für `!testalert` — erlaubt
     * `follow`, `sub`/`subscribe`, `gift`/`giftsub`, `resub`, `raid`
     * (case-insensitive); null bei fehlendem/ungültigem Typ (Engine antwortet
     * mit Nutzungs-Hinweis).
     */
    private fun parseAlertType(rest: String): ChatAlertType? = when (firstToken(rest).lowercase()) {
        "follow", "follower" -> ChatAlertType.FOLLOW
        "sub", "subscribe", "subscriber" -> ChatAlertType.SUBSCRIBE
        "gift", "giftsub", "gift-sub", "gift_sub" -> ChatAlertType.GIFT_SUB
        "resub", "resubscribe" -> ChatAlertType.RESUB
        "raid" -> ChatAlertType.RAID
        else -> null
    }

    /** Erstes Token eines Rest-Strings (alles bis zum ersten Whitespace). */
    private fun firstToken(rest: String): String =
        rest.trim().takeWhile { it != ' ' && it != '\t' }.takeIf { it.isNotBlank() } ?: ""

    /**
     * Optionales zweites Token als Minuten-Zahl für `!timeout` — erlaubt
     * Suffixe (`10`, `10min`, `10Minute`, `10m`), case-insensitive.
     */
    private fun parseTimeoutDuration(rest: String): Int? {
        val token = rest.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.getOrNull(1)
            ?: return null
        val number = token.lowercase()
            .removeSuffix("minute")
            .removeSuffix("min")
            .removeSuffix("m")
        return number.toIntOrNull()?.takeIf { it > 0 }
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
        const val HELP_TEXT = "Verfügbare Befehle: !help · !uptime · !song · !next · !pause · !bot | Owner: !tts · !testalert · !torch · !filter · !boost · !battery · !lut · !colorspace"
        const val BOT_INFO_TEXT = "Ich bin der Chat-Bot von Vivid 🤖 — alle Befehle: !help"
    }
}
