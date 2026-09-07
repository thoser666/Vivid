package com.vivid.feature.chat.bot

/**
 * Single Source of Truth für alle Chat-Bot-Befehle.
 *
 * Diese Tabelle ist die eine Stelle, an der jeder Befehl mit seinem
 * kanonischen Namen ([BotCommandEntry.primary]), seinen Aliasen,
 * seiner Owner-Grenze und seiner Anzeige-Form ([BotCommandEntry.display])
 * gepflegt wird. Sie speist:
 *
 *  - [BotCommandProcessor.HELP_TEXT] und den `!help`-Antworttext
 *    (`helpText()`) — der Bot erklärt damit exakt die Befehle, die er auch
 *    annimmt,
 *  - die In-App-Hilfe (`HelpScreen` im app-Modul rendert die Katalogzeilen),
 *  - die Doku-Guards: `BotCommandsCatalogTest` beweist per
 *    `BotCommandProcessor.handle()`, dass **jeder** Katalog-Name (primär +
 *    Aliase) von der dispatch()-Tabelle akzeptiert wird und nicht als
 *    `Unknown` endet; `scripts/check_bot_commands_doc.sh` prüft dasselbe
 *    Set gegen die Handbücher (DE/EN/FR), `scripts/sync_wiki.sh` spiegelt
 *    es ins GitHub-Wiki.
 *
 * **Neuen Befehl hinzufügen:** hier einen Eintrag ergänzen, die
 * dispatch()-Zeile in [BotCommandProcessor] anlegen und die
 * Beschreibungs-Strings (`help_cmd_*`) in app (values, values-en,
 * values-fr) füllen. Der Katalog-Test schlägt an, solange eine der
 * drei Ebenen fehlt — Befehle können nicht mehr an der Hilfe oder Doku
 * vorbeishippen.
 */
object BotCommandsCatalog {

    /**
     * Ein Befehl im Katalog.
     *
     * @param primary   kanonischer Name (Schlüssel in der dispatch()-Tabelle)
     * @param aliases   weitere akzeptierte Namen (müssen ebenfalls in
     *                  dispatch() gelistet sein — siehe Katalog-Test)
     * @param ownerOnly `true`, wenn nur der Streamer den Befehl auslösen darf
     *                  (das Gate liegt in der Engine; hier dokumentarisch)
     * @param display   Anzeige-Form für UI/Hilfe inkl. Aliasen und
     *                  Argument-Hinweis (z. B. `"!timeout <user> <min?>"`)
     */
    data class Entry(
        val primary: String,
        val aliases: List<String> = emptyList(),
        val ownerOnly: Boolean,
        val display: String,
    )

    /** Alle Befehle — erst Viewer, dann Owner (Reihenfolge = Anzeige-Reihenfolge). */
    val all: List<Entry> = listOf(
        // ── Viewer (jeder im Chat) ──────────────────────────────────────────
        Entry("help", aliases = listOf("commands", "hilfe"), ownerOnly = false, display = "!help / !commands"),
        Entry("uptime", ownerOnly = false, display = "!uptime"),
        Entry("song", aliases = listOf("nowplaying", "np"), ownerOnly = false, display = "!song / !nowplaying"),
        Entry("next", aliases = listOf("skip"), ownerOnly = false, display = "!next / !skip"),
        Entry("pause", ownerOnly = false, display = "!pause"),
        Entry("play", ownerOnly = false, display = "!play"),
        Entry("prev", aliases = listOf("previous"), ownerOnly = false, display = "!prev / !previous"),
        Entry("bot", ownerOnly = false, display = "!bot"),
        Entry("vote", ownerOnly = false, display = "!vote <Nummer|Option>"),
        // ── Owner (nur der Streamer) ────────────────────────────────────────
        Entry("tts", ownerOnly = true, display = "!tts"),
        Entry("start", aliases = listOf("go-live", "go_live", "livestart"), ownerOnly = true, display = "!start / !go-live"),
        Entry("stop", aliases = listOf("end", "shutdown"), ownerOnly = true, display = "!stop / !end"),
        Entry("diag", aliases = listOf("diagnose", "status"), ownerOnly = true, display = "!diag / !status"),
        Entry("ask", ownerOnly = true, display = "!ask <frage>"),
        Entry("testalert", aliases = listOf("test-alert", "alert"), ownerOnly = true, display = "!testalert <type>"),
        Entry("torch", aliases = listOf("lantern", "flashlight"), ownerOnly = true, display = "!torch"),
        Entry("fix", ownerOnly = true, display = "!fix"),
        Entry("filter", aliases = listOf("fx"), ownerOnly = true, display = "!filter [name]"),
        Entry("boost", aliases = listOf("lowlight", "low-light"), ownerOnly = true, display = "!boost"),
        Entry("battery", aliases = listOf("akku"), ownerOnly = true, display = "!battery"),
        Entry("lut", ownerOnly = true, display = "!lut [warm|cool|none]"),
        Entry("colorspace", aliases = listOf("color-space", "cs"), ownerOnly = true, display = "!colorspace [srgb|p3|log]"),
        Entry("ban", ownerOnly = true, display = "!ban <user>"),
        Entry("timeout", ownerOnly = true, display = "!timeout <user> <min?>"),
        Entry("delete", ownerOnly = true, display = "!delete <count?>"),
        Entry("poll", ownerOnly = true, display = "!poll Frage | Option A | Option B"),
        Entry("pollend", aliases = listOf("endpoll", "end-poll"), ownerOnly = true, display = "!pollend"),
    )

    /** Kanonische Namen in der Viewer-Sektion des Help-Texts (historische Reihenfolge). */
    val helpViewerNames: List<String> = listOf("help", "uptime", "song", "next", "pause", "bot", "vote")

    /** Kanonische Namen in der Owner-Sektion des Help-Texts (historische Reihenfolge). */
    val helpOwnerNames: List<String> =
        listOf("tts", "testalert", "torch", "filter", "boost", "battery", "lut", "colorspace", "poll", "pollend")

    /**
     * Baut den `!help`-Antworttext. [prefix] ist im PREFIX-Scope das
     * eigene Bot-Präfix (z. B. `"v"` → `!v!help`); null/blank ergibt den
     * normalen Text.
     */
    fun helpText(prefix: String? = null): String {
        val p = if (prefix.isNullOrBlank()) "!" else "!${prefix}!"
        val viewer = helpViewerNames.joinToString(" · ") { "$p$it" }
        val owner = helpOwnerNames.joinToString(" · ") { "$p$it" }
        return "Verfügbare Befehle: $viewer | Owner: $owner"
    }
}
