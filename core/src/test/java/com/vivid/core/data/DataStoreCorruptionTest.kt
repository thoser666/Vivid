package com.vivid.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vivid.core.di.settingsCorruptionHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Beschädigte `settings.preferences_pb` (#432, Sentry VIVID-5F/5G/5K).
 *
 * DataStore meldet die unlesbare Datei als [CorruptionException] aus dem
 * Bibliothekscode (`FileStorage.kt#runFileDiagnosticsIfNotCorruption`). Ohne
 * Handler ist der Default der ReThrow-Handler: die Ausnahme erreicht jeden
 * Leser, und weder `SettingsRepository` noch `SceneRepository` fangen sie an
 * `dataStore.data` ab — der Prozess stirbt. Deshalb registriert
 * `DataStoreModule` [settingsCorruptionHandler].
 *
 * Was hier geprüft wird: der Handler liefert für einen Korruptionsbericht
 * Defaults statt zu werfen, und der Fehler ist ohne Handler real (Negativ-
 * kontrolle) — dieser Test wäre ohne den Fix rot. Der *Datei*-Ersatz selbst
 * läuft auf dem Gerät: DataStore 1.3.0 kann unter Windows nicht über eine
 * vorhandene Zieldatei umbenennen (dieselbe Einschränkung wie in
 * [InMemoryPreferencesDataStore] beschrieben), der Ersatz-Pfad scheitert dort
 * also unabhängig vom Handler. Beleg für den Erfolgsfall ist deshalb der
 * Emulator-/Gerätelauf (`adb`, in `docs/sentry-issues.md` §5.22 protokolliert).
 */
class DataStoreCorruptionTest {

    @TempDir
    lateinit var tempDir: Path

    /** Kein gültiger Preferences-Proto-Satz — DataStore liest 64×0xFF nicht als Einträge. */
    private fun corruptedFile(): File = File(tempDir.toFile(), "settings.preferences_pb").apply {
        parentFile?.mkdirs()
        writeBytes(ByteArray(64) { 0xFF.toByte() })
    }

    @Test
    fun `corruption report is answered with empty defaults instead of a rethrow`() = runTest {
        val report = CorruptionException("Corruption in file settings.preferences_pb", null)

        val replacement = settingsCorruptionHandler.handleCorruption(report)

        assertTrue(
            replacement.asMap().isEmpty(),
            "ein Korruptionsbericht muss auf Defaults abgebildet werden, nicht weitergeworfen",
        )
        assertEquals(0, replacement.asMap().size, "der Ersatz darf keine Altwerte vortäuschen")
    }

    @Test
    fun `without a handler the same corrupted file surfaces as CorruptionException`() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { corruptedFile() })

        // Negativkontrolle: die Datei ist wirklich unlesbar und der Fehler
        // erreicht ohne Handler den Leser — der Test oben belegt damit die
        // Wirkung des Handlers und nicht eine folgenlose Sonderbehandlung.
        assertThrows(CorruptionException::class.java) { runBlocking { store.data.first() } }
    }
}
