package com.vivid.core.update

import javax.inject.Inject

/** Ergebnis eines Update-Checks gegen die GitHub-Releases. */
sealed interface UpdateCheckResult {
    /** Installierte Version ist die neueste (oder neuer). */
    data class UpToDate(val latestVersion: String) : UpdateCheckResult

    /** Es gibt eine neuere Version im selben oder höheren Kanal. */
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : UpdateCheckResult

    /** Kein Update-Check möglich (Netzwerkfehler, keine Releases, unbekannte Version). */
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Prüft, ob für die installierte [installedVersionName] ein Update existiert.
 *
 * Regeln (Spiegel von RELEASE.md „Cross-Track-Verhalten“):
 * - Kandidaten sind Releases mit Kanal-Rank >= installiertem Kanal-Rank
 *   (nightly → nightly/alpha/beta/rc/stable; alpha → alpha/beta/rc/stable; stable → stable).
 *   Damit wird ein Downgrade (alpha → nightly) nie als Update gemeldet.
 * - Unter den Kandidaten gewinnt die höchste (major, minor, patch, Kanal, Build-Nummer).
 *
 * Caching: Erfolgreiche Ergebnisse werden in [UpdateCheckCache] gespeichert und innerhalb
 * von [DEFAULT_CACHE_TTL_MILLIS] wiederverwendet — die GitHub-API erlaubt unauthentifiziert
 * nur 60 Requests/h, und der Settings-Screen prüft bei jeder Öffnung. [forceRefresh]
 * umgeht den Cache (manueller „Nach Updates suchen“-Knopf im About-Screen).
 */
class UpdateChecker @Inject constructor(
    private val api: GitHubReleasesApi,
    private val cache: UpdateCheckCache,
) {

    companion object {
        /** Ablaufzeit des gecachten Update-Checks (1 Stunde). */
        const val DEFAULT_CACHE_TTL_MILLIS = 60 * 60 * 1000L
    }

    suspend fun check(installedVersionName: String, forceRefresh: Boolean = false): UpdateCheckResult {
        if (!forceRefresh) {
            val cached = cache.load()
            if (cached != null &&
                cached.installedVersion == installedVersionName &&
                System.currentTimeMillis() - cached.timestampMillis < DEFAULT_CACHE_TTL_MILLIS
            ) {
                return cached.result
            }
        }

        val result = performCheck(installedVersionName)
        if (result is UpdateCheckResult.UpToDate || result is UpdateCheckResult.UpdateAvailable) {
            cache.save(installedVersionName, result)
        }
        return result
    }

    private suspend fun performCheck(installedVersionName: String): UpdateCheckResult {
        val installed = AppVersion.parse(installedVersionName)
            ?: return UpdateCheckResult.Error("Unbekannte Version: $installedVersionName")

        val releases = try {
            api.getReleases()
        } catch (e: Exception) {
            return UpdateCheckResult.Error("Update-Check fehlgeschlagen: ${e.message ?: "Netzwerkfehler"}")
        }

        val candidates = releases
            .asSequence()
            .filterNot { it.draft }
            .mapNotNull { release -> AppVersion.parse(release.name) ?: AppVersion.parse(release.tagName) }
            .filter { it.channel.rank >= installed.channel.rank }
            .toList()

        val latest = candidates.maxOrNull()
            ?: return UpdateCheckResult.Error("Keine Releases gefunden")

        return if (latest > installed) {
            val url = releases.firstOrNull { release ->
                (AppVersion.parse(release.name) ?: AppVersion.parse(release.tagName)) == latest
            }?.htmlUrl ?: "https://github.com/thoser666/Vivid/releases"
            UpdateCheckResult.UpdateAvailable(latest.toString(), url)
        } else {
            UpdateCheckResult.UpToDate(latest.toString())
        }
    }
}
