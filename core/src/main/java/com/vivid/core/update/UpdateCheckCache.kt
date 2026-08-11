package com.vivid.core.update

/**
 * Persistenter Cache für das Ergebnis des Update-Checks — überlebt App-Starts,
 * damit nicht jede Settings-Öffnung die GitHub-API (60 Requests/h unauthentifiziert)
 * belastet. Nur erfolgreiche Ergebnisse (UpToDate/UpdateAvailable) werden gespeichert,
 * Fehler nie (damit die nächste Öffnung erneut versuchen kann).
 */
interface UpdateCheckCache {

    /** Gecachtes Ergebnis samt Version, für die es gilt, und Zeitstempel (epoch millis). */
    data class CachedCheck(
        val installedVersion: String,
        val result: UpdateCheckResult,
        val timestampMillis: Long,
    )

    suspend fun load(): CachedCheck?

    suspend fun save(
        installedVersion: String,
        result: UpdateCheckResult,
        timestampMillis: Long = System.currentTimeMillis(),
    )
}
