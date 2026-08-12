package com.vivid.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Live-Test des [UpdateChecker] gegen die ECHTEN GitHub-Releases des Repos —
 * kein Mock, echte HTTP-Antwort der GitHub-API.
 *
 * Standardmäßig deaktiviert (Unit-Tests dürfen kein Netz brauchen); aktivieren:
 * ```
 * ./gradlew :core:testDebugUnitTest -PliveUpdateCheck=true
 * ```
 * Im CI läuft das als eigener Schritt (android_fastlane.yml, „Live update
 * check against GitHub releases“) mit `GH_TOKEN` (authentifiziertes
 * Rate-Limit statt 60 Requests/h unauthentifiziert).
 *
 * Bewusst ein eigener, schlanker Ktor-Client ohne Logging — damit wird kein
 * Authorization-Header (Token) in CI-Logs geschrieben.
 */
@EnabledIfSystemProperty(named = "liveUpdateCheck", matches = "true")
class LiveUpdateCheckTest {

    /** In-Memory-Cache ohne Persistenz — pro Checker ein API-Call. */
    private class InMemoryCache : UpdateCheckCache {
        var stored: UpdateCheckCache.CachedCheck? = null
        override suspend fun load(): UpdateCheckCache.CachedCheck? = stored
        override suspend fun save(
            installedVersion: String,
            result: UpdateCheckResult,
            timestampMillis: Long,
        ) {
            stored = UpdateCheckCache.CachedCheck(installedVersion, result, timestampMillis)
        }
    }

    private fun api(): GitHubReleasesApi {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return GitHubReleasesApiImpl(
            client = client,
            authToken = System.getenv("GH_TOKEN")?.takeIf { it.isNotBlank() },
        )
    }

    private fun checker(): UpdateChecker = UpdateChecker(api(), InMemoryCache())

    /**
     * Holt ALLE Releases über alle Seiten (GitHub paginiert bei per_page=100).
     * Beendet die Schleife, sobald eine Seite leer ist.
     */
    private suspend fun allReleases(): List<GitHubRelease> {
        val api = api()
        val all = mutableListOf<GitHubRelease>()
        var page = 1
        while (true) {
            val batch = api.getReleases(perPage = 100, page = page)
            if (batch.isEmpty()) break
            all += batch
            if (batch.size < 100) break
            page++
        }
        return all
    }

    /** Alle parsebaren Versionen aus den Releases (Name oder Tag), Drafts ignoriert. */
    private fun parseableVersions(releases: List<GitHubRelease>): List<AppVersion> =
        releases
            .filterNot { it.draft }
            .mapNotNull { AppVersion.parse(it.name) ?: AppVersion.parse(it.tagName) }

    @Test
    fun `reports an update for an ancient nightly and returns a parseable latest`() = runBlocking {
        val result = checker().check("0.0.1-nightly.1")

        assertTrue(result is UpdateCheckResult.UpdateAvailable, "erwartet UpdateAvailable, war: $result")
        result as UpdateCheckResult.UpdateAvailable
        assertNotNull(AppVersion.parse(result.latestVersion), "latestVersion nicht parsebar: ${result.latestVersion}")
        assertTrue(result.releaseUrl.startsWith("https://github.com/thoser666/Vivid/releases"))
    }

    @Test
    fun `checking against the reported latest is up to date`() = runBlocking {
        val checker = checker()
        val result = checker.check("0.0.1-nightly.1")
        assertTrue(result is UpdateCheckResult.UpdateAvailable, "erwartet UpdateAvailable, war: $result")

        val recheck = checker.check((result as UpdateCheckResult.UpdateAvailable).latestVersion)

        assertTrue(recheck is UpdateCheckResult.UpToDate, "Re-Check gegen latest sollte UpToDate sein, war: $recheck")
    }

    @Test
    fun `a futuristic version is up to date`() = runBlocking {
        val result = checker().check("99.99.99-nightly.1")

        assertTrue(result is UpdateCheckResult.UpToDate, "erwartet UpToDate, war: $result")
    }

    /**
     * Konsistenztest: Der [UpdateChecker] muss über ALLE echten Releases hinweg
     * das Maximum als „latest“ melden — kein einzelnes Release darf neuer sein
     * als das gemeldete latest.
     */
    @Test
    fun `no release is newer than the reported latest`() = runBlocking {
        val releases = allReleases()
        assertTrue(releases.isNotEmpty(), "Keine Releases von der GitHub-API geliefert")

        val versions = parseableVersions(releases)
        assertTrue(
            versions.isNotEmpty(),
            "Keine parsebaren Versionen in den Releases — Tag/Name-Format unerwartet",
        )

        // Maximum über ALLE Releases (unabhängig vom Kanal) = erwartetes latest.
        val expectedLatest = versions.maxOrNull()!!

        // Der Checker mit einer sehr alten Version muss genau dieses Maximum melden.
        val result = checker().check("0.0.1-nightly.1")
        assertTrue(result is UpdateCheckResult.UpdateAvailable, "erwartet UpdateAvailable, war: $result")
        val reported = AppVersion.parse((result as UpdateCheckResult.UpdateAvailable).latestVersion)
            ?: throw AssertionError("gemeldetes latest nicht parsebar: ${result.latestVersion}")

        // Kern-Assertion: kein Release ist neuer als das gemeldete latest,
        // und das gemeldete latest ist exakt das Maximum aller Releases.
        assertTrue(
            versions.all { it <= reported },
            "Release neuer als gemeldetes latest gefunden! latest=$reported, alle=$versions",
        )
        org.junit.jupiter.api.Assertions.assertEquals(
            expectedLatest,
            reported,
            "Gemeldetes latest ($reported) != Maximum aller Releases ($expectedLatest)",
        )
    }
}
