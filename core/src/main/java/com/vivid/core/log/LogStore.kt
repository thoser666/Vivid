package com.vivid.core.log

import com.google.gson.Gson
import java.io.File

/**
 * Persistenter, **tagesbasierter** Log-Speicher mit konfigurierbarer Vorhaltezeit.
 *
 * Pro Kalendertag liegt eine JSON-Lines-Datei unter `logs/yyyy-MM-dd.log` im
 * App-internen Dateiverzeichnis (kein Zugriff von außen). Die Einträge sind
 * bereits durch den [LogRedactor] geschwärzt — sensible Werte werden nie
 * persistiert. Beim [load] werden nur die Tage innerhalb der Vorhaltezeit
 * (Retention) gelesen; [prune] löscht Dateien, die älter sind.
 *
 * Alle Methoden sind `@Synchronized` (Timber-Trees können von beliebigen
 * Threads aufgerufen werden; der Crash-Handler läuft im Absturz-Thread).
 */
class LogStore(
    private val directory: File,
    private val gson: Gson = Gson(),
) {

    /** Hängt einen Eintrag an die Datei seines Kalendertags an (erzeugt sie bei Bedarf). */
    @Synchronized
    fun add(entry: LogEntry) {
        runCatching {
            val file = fileFor(entry.timestampMillis)
            file.parentFile?.mkdirs()
            file.appendText(gson.toJson(entry) + "\n")
        }
    }

    /**
     * Alle Einträge der letzten [retentionDays] Tage (heute + gestern + …),
     * chronologisch aufsteigend. Korrupte Zeilen werden übersprungen statt
     * geworfen — ein defekter Log darf die App nie blockieren.
     */
    @Synchronized
    fun load(retentionDays: Int): List<LogEntry> {
        if (retentionDays < 1) return emptyList()
        val cutoff = LogDates.daysAgoKey(System.currentTimeMillis(), retentionDays - 1)
        return directory.listFiles { f -> f.isFile && DAY_FILE.matches(f.name) }
            .orEmpty()
            .filter { it.name.removeSuffix(".log") >= cutoff }
            .sortedBy { it.name }
            .flatMap { file ->
                runCatching { file.readLines() }.getOrDefault(emptyList())
                    .mapNotNull { line -> parseLine(line) }
            }
            .sortedBy { it.timestampMillis }
    }

    /**
     * Parst eine JSON-Lines-Zeile über [LogLineDto] und stellt die Kotlin-
     * Invarianten von [LogEntry] wieder her.
     *
     * Gson instanziiert per Reflection/Unsafe OHNE den Kotlin-Konstruktor — die
     * Non-null-Checks von [LogEntry] laufen nie, und fehlende/unbekannte Felder
     * landen als null im Objekt (unbekannter Enum-Name, `"level": null`, fehlender
     * Key, Teil-Schreibvorgang). Ein solcher Eintrag crashte real in
     * `LogEntry.format()` (`level.name`) bzw. im errorsOnly-Levelvergleich —
     * Sentry VIVID-3A/3B (Issues #225/#216) und erneut VIVID-5H (#433), obwohl
     * dieser Guard existierte: er prüfte ein statisch non-null typisiertes Feld,
     * also einen für den Optimierer unmöglichen Zustand.
     *
     * Deshalb läuft das Parsing über [LogLineDto], dessen Felder echt nullbar
     * sind (Level als roher Name). Die Prüfungen dort sind nicht wegoptimierbar,
     * und [LogLineDto.toEntry] baut den Eintrag über den Konstruktor.
     * Invariantenverletzende Zeilen werden übersprungen statt weitergereicht;
     * ein defekter Log darf die App nie blockieren.
     */
    private fun parseLine(line: String): LogEntry? =
        runCatching { gson.fromJson(line, LogLineDto::class.java) }
            .getOrNull()
            ?.toEntry()

    /** Löscht alle Log-Dateien, deren Tag älter als [retentionDays] Tage ist. */
    @Synchronized
    fun prune(retentionDays: Int) {
        if (retentionDays < 1) {
            clear()
            return
        }
        val cutoff = LogDates.daysAgoKey(System.currentTimeMillis(), retentionDays - 1)
        directory.listFiles { f -> f.isFile && DAY_FILE.matches(f.name) }
            .orEmpty()
            .filter { it.name.removeSuffix(".log") < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    /** Löscht alle Log-Dateien („Logs leeren“). */
    @Synchronized
    fun clear() {
        directory.listFiles { f -> f.isFile && DAY_FILE.matches(f.name) }
            .orEmpty()
            .forEach { runCatching { it.delete() } }
    }

    private fun fileFor(timestampMillis: Long): File =
        File(directory, LogDates.dayKey(timestampMillis) + ".log")

    companion object {
        private val DAY_FILE = Regex("""\d{4}-\d{2}-\d{2}\.log""")
    }
}
