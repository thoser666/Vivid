package com.vivid.core.log

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Testet den persistenten, tagesbasierten Log-Speicher: tägliche Rotation in
 * eigene Dateien, Laden nur innerhalb der Vorhaltezeit, Prune alter Dateien,
 * Leeren und Korruptions-Toleranz.
 */
class LogStoreTest {

    @TempDir
    lateinit var tempDir: File

    private val dayMs = 24L * 60 * 60 * 1000

    private fun entry(timestampMillis: Long, message: String = "msg") = LogEntry(
        timestampMillis = timestampMillis,
        level = LogLevel.INFO,
        tag = "Test",
        message = message,
    )

    private fun store() = LogStore(File(tempDir, "logs"))

    @Test
    fun `add erzeugt die taegliche Datei und load liefert den Eintrag`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "hallo"))

        val loaded = s.load(retentionDays = 7)
        assertEquals(listOf("hallo"), loaded.map { it.message })
        assertTrue(File(tempDir, "logs/${LogDates.dayKey(now)}.log").exists())
    }

    @Test
    fun `rotation trennt Tage in eigene Dateien`() {
        val s = store()
        val now = System.currentTimeMillis()
        val yesterday = now - dayMs
        s.add(entry(now, "heute"))
        s.add(entry(yesterday, "gestern"))

        assertEquals(
            listOf("gestern", "heute"),
            s.load(retentionDays = 7).map { it.message },
        )
        assertTrue(File(tempDir, "logs/${LogDates.dayKey(now)}.log").exists())
        assertTrue(File(tempDir, "logs/${LogDates.dayKey(yesterday)}.log").exists())
    }

    @Test
    fun `load respektiert die Vorhaltezeit`() {
        val s = store()
        val now = System.currentTimeMillis()
        val old = now - 8L * dayMs
        s.add(entry(now, "heute"))
        s.add(entry(old, "alt"))

        // Vorhaltezeit 7: der 8 Tage alte Eintrag fällt raus.
        assertEquals(listOf("heute"), s.load(retentionDays = 7).map { it.message })
        // Vorhaltezeit 30: beide enthalten.
        assertEquals(listOf("alt", "heute"), s.load(retentionDays = 30).map { it.message })
    }

    @Test
    fun `prune loescht Dateien aelter als die Vorhaltezeit`() {
        val s = store()
        val now = System.currentTimeMillis()
        val old = now - 8L * dayMs
        s.add(entry(now, "heute"))
        s.add(entry(old, "alt"))

        s.prune(retentionDays = 7)

        assertTrue(File(tempDir, "logs/${LogDates.dayKey(now)}.log").exists())
        assertFalse(File(tempDir, "logs/${LogDates.dayKey(old)}.log").exists())
    }

    @Test
    fun `clear loescht alle Log-Dateien`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "a"))
        s.add(entry(now - dayMs, "b"))

        s.clear()

        assertTrue(s.load(retentionDays = 30).isEmpty())
        assertEquals(0, File(tempDir, "logs").listFiles().orEmpty().size)
    }

    @Test
    fun `korrupte Zeile wird uebersprungen statt geworfen`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "ok"))
        // Korrupte Zeile direkt in die heutige Datei schreiben.
        File(tempDir, "logs/${LogDates.dayKey(now)}.log").appendText("das ist kein json\n")

        assertEquals(listOf("ok"), s.load(retentionDays = 7).map { it.message })
    }

    @Test
    fun `prune mit Vorhaltezeit unter 1 leert alles`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "a"))

        s.prune(retentionDays = 0)

        assertTrue(s.load(retentionDays = 30).isEmpty())
    }

    // --- VIVID-3A/3B (#225/#216): Gson-Reflexion kann LogEntry-Instanzen mit
    // null-Feldern erzeugen (Unsafe-Instanziierung ohne Kotlin-Konstruktor).
    // Der Store muss solche Zeilen überspringen, damit format()/Levelvergleiche
    // nie an einem null-Level crashen.

    @Test
    fun `zeile mit unbekanntem enum-namen wird uebersprungen statt als null-level geladen`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "ok"))
        File(tempDir, "logs/${LogDates.dayKey(now)}.log").appendText(
            "{\"timestampMillis\":$now,\"level\":\"TOTALLY_UNKNOWN\",\"tag\":\"T\",\"message\":\"gift\",\"isCrash\":false}\n",
        )

        assertEquals(listOf("ok"), s.load(retentionDays = 7).map { it.message })
    }

    @Test
    fun `zeile mit explizitem null level wird uebersprungen`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "ok"))
        File(tempDir, "logs/${LogDates.dayKey(now)}.log").appendText(
            "{\"timestampMillis\":$now,\"level\":null,\"tag\":\"T\",\"message\":\"gift\",\"isCrash\":false}\n",
        )

        assertEquals(listOf("ok"), s.load(retentionDays = 7).map { it.message })
    }

    @Test
    fun `zeile ohne level-key und ohne tag-message wird uebersprungen`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "ok"))
        val f = File(tempDir, "logs/${LogDates.dayKey(now)}.log")
        f.appendText("{\"timestampMillis\":$now,\"tag\":\"T\",\"message\":\"ohne level\",\"isCrash\":false}\n")
        f.appendText("{\"timestampMillis\":$now,\"level\":\"INFO\"}\n")

        assertEquals(listOf("ok"), s.load(retentionDays = 7).map { it.message })
    }

    @Test
    fun `regression vivid-3a-3b format auf geladenen eintraegen wirft nie`() {
        val s = store()
        val now = System.currentTimeMillis()
        s.add(entry(now, "ok"))
        val f = File(tempDir, "logs/${LogDates.dayKey(now)}.log")
        f.appendText("{\"timestampMillis\":$now,\"level\":\"UNKNOWN_NAME\",\"tag\":\"T\",\"message\":\"a\",\"isCrash\":false}\n")
        f.appendText("{\"timestampMillis\":$now,\"level\":null,\"tag\":\"T\",\"message\":\"b\",\"isCrash\":false}\n")
        f.appendText("{\"timestampMillis\":$now,\"tag\":\"T\",\"message\":\"c\"}\n")
        f.appendText("null\n")
        f.appendText("das ist kein json\n")

        val loaded = s.load(retentionDays = 7)
        assertEquals(listOf("ok"), loaded.map { it.message })
        // Der eigentliche Crash (VIVID-3B, Top-Frame LogEntry.kt:format):
        // level.name auf einem null-Level-Eintrag → NPE. Mit Invarianten-
        // Wiederherstellung im Store darf das nie mehr passieren.
        assertDoesNotThrow { loaded.forEach { it.format() } }
    }
}
