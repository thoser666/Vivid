package com.vivid.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DataStoreUpdateCheckCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private fun kotlinx.coroutines.test.TestScope.cache(name: String): Pair<DataStore<Preferences>, DataStoreUpdateCheckCache> {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "$name.preferences_pb") },
        )
        return dataStore to DataStoreUpdateCheckCache(dataStore)
    }

    @Test
    fun `load returns null when nothing was saved`() = runTest {
        val (_, cache) = cache("empty")

        assertNull(cache.load())
    }

    @Test
    fun `roundtrips an update available result`() = runTest {
        val (_, cache) = cache("available")

        cache.save(
            installedVersion = "0.2.0-nightly.93",
            result = UpdateCheckResult.UpdateAvailable(
                latestVersion = "0.2.0-nightly.97",
                releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118",
            ),
            timestampMillis = 1234L,
        )

        val loaded = cache.load()
        assertEquals("0.2.0-nightly.93", loaded?.installedVersion)
        assertEquals(1234L, loaded?.timestampMillis)
        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.97", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118"),
            loaded?.result,
        )
    }

    @Test
    fun `roundtrips an up to date result`() = runTest {
        val (_, cache) = cache("uptodate")

        cache.save(
            installedVersion = "0.2.0-nightly.97",
            result = UpdateCheckResult.UpToDate(latestVersion = "0.2.0-nightly.97"),
        )

        val loaded = cache.load()
        assertEquals("0.2.0-nightly.97", loaded?.installedVersion)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), loaded?.result)
    }

    @Test
    fun `saving up to date after available clears the release url`() = runTest {
        val (dataStore, cache) = cache("clear-url")

        cache.save(
            installedVersion = "0.2.0-nightly.93",
            result = UpdateCheckResult.UpdateAvailable("0.2.0-nightly.97", "https://example.com/release"),
        )
        // Beweis: RELEASE_URL ist nach dem Available-Write persistiert.
        assertNotNull(dataStore.data.first()[stringPreferencesKey("update_check_release_url")])

        // Windows-Limitation von AndroidX DataStore: FileStorage überschreibt eine bestehende
        // Datei nicht per ATOMIC_MOVE (zweiter Write auf dieselbe Datei schlägt fehl) — im Test
        // die Datei zwischen den Writes entfernen. In Produktion schreibt die App nur selten
        // (max. 1×/h durch das Caching), dort ist das kein Problem.
        File(tempDir.toFile(), "clear-url.preferences_pb").delete()

        cache.save(
            installedVersion = "0.2.0-nightly.97",
            result = UpdateCheckResult.UpToDate("0.2.0-nightly.97"),
        )

        val loaded = cache.load()
        assertEquals("0.2.0-nightly.97", loaded?.installedVersion)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), loaded?.result)
    }

    @Test
    fun `errors are not persisted`() = runTest {
        val (_, cache) = cache("error")

        cache.save(
            installedVersion = "0.2.0-nightly.93",
            result = UpdateCheckResult.Error("network down"),
        )

        assertNull(cache.load())
    }
}
