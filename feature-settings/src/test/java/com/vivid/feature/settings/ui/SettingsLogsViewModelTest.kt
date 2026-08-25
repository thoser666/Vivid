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
        store.add(entry("historie", timestampMillis = 1L))
        buffer.add(entry("live", timestampMillis = 2L))
        // Derselbe Eintrag in Puffer UND Store — darf nur einmal erscheinen.
        buffer.add(entry("historie", timestampMillis = 1L))

        val viewModel = createViewModel(buffer, store)
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        assertEquals(setOf("historie", "live"), messages.toSet())
        assertEquals(2, messages.size)
        store.clear()
    }

    @Test
    fun `crashCount zaehlt markierte Abstuerze`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        buffer.add(entry("normal"))
        buffer.add(entry("absturz", isCrash = true))
        buffer.add(entry("fehler", level = LogLevel.ERROR))

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
        buffer.add(entry("fehler", level = LogLevel.ERROR))
        buffer.add(entry("absturz", isCrash = true))

        val viewModel = createViewModel(buffer, LogStore(File("unused")))
        advanceUntilIdle()
        viewModel.toggleErrorsOnly()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.entries.map { it.message }
        assertEquals(setOf("fehler", "absturz"), messages.toSet())
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
    fun `clearLogs leert Puffer und Store`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val buffer = LogBuffer()
        val store = LogStore(File.createTempFile("logs", "").parentFile.resolve("vm_clear"))
        store.add(entry("persistiert"))
        buffer.add(entry("live"))

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
