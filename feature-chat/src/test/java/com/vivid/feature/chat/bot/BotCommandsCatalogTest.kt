package com.vivid.feature.chat.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Beweist die Single-Source-of-Truth-Eigenschaft von [BotCommandsCatalog]:
 * Jeder Katalog-Name (primär + Aliase) wird von der dispatch()-Tabelle des
 * [BotCommandProcessor] akzeptiert und endet NICHT als [BotCommandProcessor
 * .Result.Unknown]. Damit ist der Katalog per Unit-Test an den echten
 * Befehlssatz gekoppelt — dasselbe Set prüfen zusätzlich die Doku-Guards
 * (`check_bot_commands_doc.sh`) und der Wiki-Sync gegen die Handbücher.
 */
class BotCommandsCatalogTest {

    private val processor = BotCommandProcessor()

    /** Alle (Name, Anzeige-)Paare des Katalogs für den Parametrisierten Test. */
    companion object {
        @JvmStatic
        fun catalogNames(): List<String> =
            BotCommandsCatalog.all.flatMap { entry -> listOf(entry.primary) + entry.aliases }
    }

    @ParameterizedTest(name = "\"!{0}\" wird von dispatch() angenommen (kein Unknown)")
    @MethodSource("catalogNames")
    fun `catalog name resolves in dispatch - never Unknown`(name: String) {
        val result = processor.handle("!$name", streamStartedAtMillis = null)
        assertFalse(
            result is BotCommandProcessor.Result.Unknown,
            "Katalog-Name '!$name' ist dem BotCommandProcessor unbekannt — " +
                "Katalog-Eintrag oder dispatch()-Tabelle nachziehen.",
        )
    }

    @Test
    fun `catalog primaries are unique`() {
        val primaries = BotCommandsCatalog.all.map { it.primary }
        assertEquals(primaries.size, primaries.toSet().size, "Doppelte primary-Namen im Katalog.")
    }

    @Test
    fun `catalog aliases do not collide with other primaries`() {
        val primaries = BotCommandsCatalog.all.map { it.primary }.toSet()
        val collisions = BotCommandsCatalog.all
            .flatMap { it.aliases }
            .filter { it in primaries }
        assertTrue(collisions.isEmpty(), "Aliase kollidieren mit Primaries: $collisions")
    }

    @Test
    fun `help viewer section lists exactly the viewer commands`() {
        // Verankerung der historischen Viewer-Reihenfolge (stabile Bot-Antwort).
        assertEquals(
            listOf("help", "uptime", "song", "next", "pause", "bot", "vote"),
            BotCommandsCatalog.helpViewerNames,
        )
    }

    @Test
    fun `help owner section lists exactly the owner commands`() {
        assertEquals(
            listOf("tts", "testalert", "torch", "filter", "boost", "battery", "lut", "colorspace", "poll", "pollend"),
            BotCommandsCatalog.helpOwnerNames,
        )
    }

    @Test
    fun `help viewer names are not ownerOnly and vice versa`() {
        val byPrimary = BotCommandsCatalog.all.associateBy { it.primary }
        for (name in BotCommandsCatalog.helpViewerNames) {
            val entry = requireNotNull(byPrimary[name]) { "Help-Viewer-Name ohne Katalog-Eintrag: $name" }
            assertFalse(entry.ownerOnly, "'$name' steht in der Viewer-Sektion, ist aber ownerOnly.")
        }
        for (name in BotCommandsCatalog.helpOwnerNames) {
            val entry = requireNotNull(byPrimary[name]) { "Help-Owner-Name ohne Katalog-Eintrag: $name" }
            assertTrue(entry.ownerOnly, "'$name' steht in der Owner-Sektion, ist aber nicht ownerOnly.")
        }
    }

    @Test
    fun `helpTextIsStable - legacy exact string`() {
        // Der Bot-Antworttext ist öffentlich sichtbar; dieser Lock verhindert
        // unbeabsichtigte Format-Änderungen (Absichtlich: exakter Vergleich).
        assertEquals(
            "Verfügbare Befehle: !help · !uptime · !song · !next · !pause · !bot · !vote " +
                "| Owner: !tts · !testalert · !torch · !filter · !boost · !battery · " +
                "!lut · !colorspace · !poll · !pollend",
            BotCommandProcessor.HELP_TEXT,
        )
    }

    @Test
    fun `prefix help text matches legacy format`() {
        assertEquals(
            "Verfügbare Befehle: !v!help · !v!uptime · !v!song · !v!next · !v!pause · !v!bot · !v!vote " +
                "| Owner: !v!tts · !v!testalert · !v!torch · !v!filter · !v!boost · !v!battery · " +
                "!v!lut · !v!colorspace · !v!poll · !v!pollend",
            BotCommandsCatalog.helpText("v"),
        )
    }
}
