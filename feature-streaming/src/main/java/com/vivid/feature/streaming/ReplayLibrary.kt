package com.vivid.feature.streaming

import java.io.File

/**
 * Verwaltet die gespeicherten Replay-Dateien der Replay-Bibliothek:
 * Auflisten, gezielt Löschen und Alles-Löschen. Das Löschen ist auf Dateien
 * im Replay-Verzeichnis beschränkt — Pfade außerhalb werden abgelehnt.
 *
 * Die Klasse enthält keine Android-Abhängigkeiten und ist vollständig
 * per Unit-Test prüfbar.
 */
class ReplayLibrary(private val storage: ReplayStorage) {

    /** Alle gespeicherten Replays, neueste zuerst. */
    fun items(): List<File> = storage.list()

    /**
     * Löscht ein einzelnes Replay. Aus Sicherheitsgründen werden nur Dateien
     * innerhalb des Replay-Verzeichnisses akzeptiert.
     *
     * @return true, wenn die Datei gelöscht wurde (oder fehlte).
     */
    fun delete(file: File): Boolean {
        if (!isInsideStorage(file)) return false
        return if (file.exists()) file.delete() else true
    }

    /**
     * Löscht alle Replays und gibt die Anzahl der gelöschten Dateien zurück.
     * Dateien, die sich nicht löschen ließen, werden ignoriert (nicht gezählt).
     */
    fun deleteAll(): Int {
        var deleted = 0
        items().forEach { file ->
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun isInsideStorage(file: File): Boolean {
        val dir = storage.directory.canonicalFile
        val target = file.canonicalFile
        return target.parentFile == dir
    }
}
