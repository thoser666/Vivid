package com.vivid.feature.settings.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Wiederverwendete Test-Nachrichten als Konstanten (DeepSource KT-5000: keine
// mehrfach wiederholten String-Literale innerhalb einer Datei).
private const val MSG_HISTORY = "historie"
private const val MSG_LIVE = "live"
private const val MSG_CRASH = "absturz"
private const val MSG_ERROR = "fehler"

/**
 * Testet das Log-ViewModel: Kombination aus Live-Puffer und persistierter
 * Tages-Historie (Deduplizierung), Crash-Zähler, Fehler-Filter, Vorhaltezeit
 * und Leeren.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLogsViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(
        message: String,
        level: LogLevel = LogLevel.INFO,
        timestampMillis: Long = 0L,
        isCrash: Boolean = false,
    ) = LogEntry(
        timestampMillis = timestampMillis,
        level = level,
        tag = "Test",
        message = message,
        isCrash = isCrash,
    )

    private fun createViewModel(
        buffer: LogBuffer,
        store: LogStore,
        settings: AppSettings = AppSettings(),
    ): SettingsLogsViewModel {
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(settings)
            coEvery { updateLogsRetentionDays(any()) } returns Unit
        }
        return SettingsLogsViewModel(buffer, store, repository)
    }

    @Test
    fun `uiState zeigt Puffer und Store-Historie dedupliziert`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        val store = LogStore(File.createTempFile("logs", "").parentFile.resolve("vm_logs"))
        store.add(entry(MSG_HISTORY, timestampMillis = 1L))
        buffer.add(entry(MSG_LIVE, timestampMillis = 2L))
        // Derselbe Eintrag in Puffer UND Store — darf nur einmal erscheinen.
        buffer.add(entry(MSG_HISTORY, timestampMillis = 1L))

        val viewModel = createViewModel(buffer, store)
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        assertEquals(setOf(MSG_HISTORY, MSG_LIVE), messages.toSet())
        assertEquals(2, messages.size)
        store.clear()
    }

    @Test
    fun `crashCount zaehlt markierte Abstuerze`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("normal"))
        buffer.add(entry(MSG_CRASH, isCrash = true))
        buffer.add(entry(MSG_ERROR, level = LogLevel.ERROR))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.crashCount)
    }

    @Test
    fun `toggleErrorsOnly filtert auf Fehler und Crashes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("info"))
        buffer.add(entry("warn", level = LogLevel.WARN))
        buffer.add(entry(MSG_ERROR, level = LogLevel.ERROR))
        buffer.add(entry(MSG_CRASH, isCrash = true))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.toggleErrorsOnly()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        assertEquals(setOf(MSG_ERROR, MSG_CRASH), messages.toSet())
        assertTrue(viewModel.uiState.value.errorsOnly)
    }

    @Test
    fun `setRetentionDays persistiert und aktualisiert`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        val store = LogStore(File.createTempFile("logs", "").parentFile.resolve("vm_ret"))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings(logsRetentionDays = 7))
            coEvery { updateLogsRetentionDays(any()) } returns Unit
        }
        val viewModel = SettingsLogsViewModel(buffer, store, repository)
        advanceUntilIdle()

        viewModel.setRetentionDays(14)
        advanceUntilIdle()

        coVerify { repository.updateLogsRetentionDays(14) }
        assertEquals(14, viewModel.uiState.value.retentionDays)
        store.clear()
    }

    @Test
    fun `setRetentionDays klemmt auf 1 bis 30`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        val store = LogStore(File("unused"))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateLogsRetentionDays(any()) } returns Unit
        }
        val viewModel = SettingsLogsViewModel(buffer, store, repository)
        advanceUntilIdle()

        viewModel.setRetentionDays(0)
        advanceUntilIdle()
        coVerify { repository.updateLogsRetentionDays(1) }
        assertEquals(1, viewModel.uiState.value.retentionDays)

        viewModel.setRetentionDays(99)
        advanceUntilIdle()
        coVerify { repository.updateLogsRetentionDays(30) }
        assertEquals(30, viewModel.uiState.value.retentionDays)
        store.clear()
    }

    @Test
    fun `setSearchQuery filtert Nachrichten case-insensitiv per Teilstring`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("RTMP-Verbindung hergestellt"))
        buffer.add(entry("WebSocket verbunden"))
        buffer.add(entry("rtmp retry geplant"))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.setSearchQuery("  rtmp ")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        assertEquals(setOf("RTMP-Verbindung hergestellt", "rtmp retry geplant"), messages.toSet())
        assertEquals("  rtmp ", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `leere oder Blank-Suche zeigt alle Eintraege`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("eins"))
        buffer.add(entry("zwei"))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.setSearchQuery("")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.entries.size)

        viewModel.setSearchQuery("   ")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `Suche ohne Treffer liefert leere Liste`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("irgendwas"))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.setSearchQuery("existiert-nicht")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun `Suche kombiniert mit Fehlerfilter`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("fehler a", level = LogLevel.ERROR))
        buffer.add(entry("fehler b", level = LogLevel.ERROR))
        buffer.add(entry("info a"))
        buffer.add(entry("warn b", level = LogLevel.WARN))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.toggleErrorsOnly()
        viewModel.setSearchQuery("b")
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        // „warn b“ ist kein Fehler → nur „fehler b“ bleibt.
        assertEquals(listOf("fehler b"), messages)
    }

    @Test
    fun `clearLogs leert Puffer und Store`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        val store = LogStore(File.createTempFile("logs", "").parentFile.resolve("vm_clear"))
        store.add(entry("persistiert"))
        buffer.add(entry(MSG_LIVE))

        val viewModel = createViewModel(buffer, store)
        advanceUntilIdle()
        viewModel.clearLogs()
        advanceUntilIdle()

        assertTrue(buffer.snapshot().isEmpty())
        assertTrue(store.load(retentionDays = 30).isEmpty())
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        store.clear()
    }
}
