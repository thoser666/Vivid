package com.vivid.core.update

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class UpdateCheckerTest {

    /** In-Memory-Cache für deterministische Caching-Tests. */
    private class FakeUpdateCheckCache : UpdateCheckCache {
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

    private fun noCache() = FakeUpdateCheckCache()

    private fun apiReturning(vararg releases: GitHubRelease): GitHubReleasesApi = mockk {
        coEvery { getReleases(any()) } returns releases.toList()
    }

    private fun release(
        name: String,
        tag: String = "tag",
        url: String = "https://github.com/thoser666/Vivid/releases/tag/$tag",
        draft: Boolean = false,
    ) = GitHubRelease(tagName = tag, name = name, htmlUrl = url, draft = draft)

    // --- Update verfügbar ---

    @Test
    fun `reports update when a newer nightly exists`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
                release("Vivid nightly (0.2.0-nightly.93)", tag = "nightly-20260811-0428"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.93")

        assertEquals(
            UpdateCheckResult.UpdateAvailable(
                latestVersion = "0.2.0-nightly.95",
                releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510",
            ),
            result,
        )
    }

    @Test
    fun `reports alpha as update when installed is a nightly`() = runTest {
        // RELEASE.md: nightly → alpha ist ein gültiger Update-Pfad (höherer Kanal-Rank).
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid v0.2.0-alpha", tag = "v0.2.0-alpha"),
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.93")

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("0.2.0-alpha", (result as UpdateCheckResult.UpdateAvailable).latestVersion)
    }

    @Test
    fun `picks the highest base version across channels`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.3.0-nightly.1)", tag = "nightly-20260812-0001"),
                release("Vivid v0.2.0-alpha", tag = "v0.2.0-alpha"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.93")

        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.3.0-nightly.1", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0001"),
            result,
        )
    }

    // --- Kein Update ---

    @Test
    fun `reports up to date when installed is the newest nightly`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.95")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.95"), result)
    }

    @Test
    fun `reports up to date when installed is newer than the latest release`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.93)", tag = "nightly-20260811-0428"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.95")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.93"), result)
    }

    @Test
    fun `never suggests a nightly downgrade from alpha`() = runTest {
        // RELEASE.md: alpha → nightly ist ein Downgrade und wird nie als Update gemeldet.
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
                release("Vivid v0.2.0-alpha", tag = "v0.2.0-alpha"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-alpha")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-alpha"), result)
    }

    @Test
    fun `stable release does not consider lower channels`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
                release("Vivid v0.2.0", tag = "v0.2.0"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0"), result)
    }

    // --- Sonderfälle ---

    @Test
    fun `ignores draft releases`() = runTest {
        val checker = UpdateChecker(
            apiReturning(
                release("Vivid nightly (0.2.0-nightly.99)", tag = "draft-tag", draft = true),
                release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"),
            ),
            noCache(),
        )

        val result = checker.check("0.2.0-nightly.93")

        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.95", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510"),
            result,
        )
    }

    @Test
    fun `returns error when the api fails`() = runTest {
        val api = mockk<GitHubReleasesApi> { coEvery { getReleases(any()) } throws IOException("network down") }
        val checker = UpdateChecker(api, noCache())

        val result = checker.check("0.2.0-nightly.93")

        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun `returns error when no releases exist`() = runTest {
        val checker = UpdateChecker(apiReturning(), noCache())

        val result = checker.check("0.2.0-nightly.93")

        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun `returns error for an unknown installed version`() = runTest {
        val checker = UpdateChecker(apiReturning(release("Vivid v0.2.0", tag = "v0.2.0")), noCache())

        val result = checker.check("garbage-version")

        assertTrue(result is UpdateCheckResult.Error)
    }

    // --- Caching ---

    @Test
    fun `uses the cached result when it is fresh and matches the installed version`() = runTest {
        val api = mockk<GitHubReleasesApi>()
        val cache = FakeUpdateCheckCache().apply {
            stored = UpdateCheckCache.CachedCheck(
                installedVersion = "0.2.0-nightly.93",
                result = UpdateCheckResult.UpToDate("0.2.0-nightly.93"),
                timestampMillis = System.currentTimeMillis(),
            )
        }
        val checker = UpdateChecker(api, cache)

        val result = checker.check("0.2.0-nightly.93")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.93"), result)
        coVerify(exactly = 0) { api.getReleases(any()) }
    }

    @Test
    fun `ignores the cache when the installed version differs`() = runTest {
        val api = apiReturning(release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"))
        val cache = FakeUpdateCheckCache().apply {
            stored = UpdateCheckCache.CachedCheck(
                installedVersion = "0.2.0-nightly.93",
                result = UpdateCheckResult.UpToDate("0.2.0-nightly.93"),
                timestampMillis = System.currentTimeMillis(),
            )
        }
        val checker = UpdateChecker(api, cache)

        val result = checker.check("0.2.0-nightly.95")

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.95"), result)
    }

    @Test
    fun `refreshes when the cached result is stale`() = runTest {
        val api = apiReturning(release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"))
        val cache = FakeUpdateCheckCache().apply {
            stored = UpdateCheckCache.CachedCheck(
                installedVersion = "0.2.0-nightly.93",
                result = UpdateCheckResult.UpToDate("0.2.0-nightly.93"),
                timestampMillis = System.currentTimeMillis() - UpdateChecker.DEFAULT_CACHE_TTL_MILLIS - 1,
            )
        }
        val checker = UpdateChecker(api, cache)

        val result = checker.check("0.2.0-nightly.93")

        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.95", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510"),
            result,
        )
    }

    @Test
    fun `saves successful results to the cache`() = runTest {
        val api = apiReturning(release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"))
        val cache = FakeUpdateCheckCache()
        val checker = UpdateChecker(api, cache)

        checker.check("0.2.0-nightly.93")

        assertEquals("0.2.0-nightly.93", cache.stored?.installedVersion)
        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.95", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510"),
            cache.stored?.result,
        )
    }

    @Test
    fun `does not cache errors`() = runTest {
        val api = mockk<GitHubReleasesApi> { coEvery { getReleases(any()) } throws IOException("network down") }
        val cache = FakeUpdateCheckCache()
        val checker = UpdateChecker(api, cache)

        val result = checker.check("0.2.0-nightly.93")

        assertTrue(result is UpdateCheckResult.Error)
        assertNull(cache.stored)
    }

    @Test
    fun `forceRefresh bypasses a fresh cache`() = runTest {
        val api = apiReturning(release("Vivid nightly (0.2.0-nightly.95)", tag = "nightly-20260811-0510"))
        val cache = FakeUpdateCheckCache().apply {
            stored = UpdateCheckCache.CachedCheck(
                installedVersion = "0.2.0-nightly.93",
                result = UpdateCheckResult.UpToDate("0.2.0-nightly.93"),
                timestampMillis = System.currentTimeMillis(),
            )
        }
        val checker = UpdateChecker(api, cache)

        val result = checker.check("0.2.0-nightly.93", forceRefresh = true)

        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.95", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0510"),
            result,
        )
    }
}
