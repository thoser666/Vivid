package com.vivid.feature.settings.ui

import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testet das Log-ViewModel: Es leitet den Puffer-Flow an die UI durch und
 * delegiert das Leeren an den [LogBuffer].
 */
class SettingsLogsViewModelTest {

    private fun entry(message: String) = LogEntry(
        timestampMillis = 0L,
        level = LogLevel.WARN,
        tag = "Test",
        message = message,
    )

    @Test
    fun `logs spiegelt den LogBuffer-Flow`() {
        val buffer = LogBuffer()
        buffer.add(entry("a"))
        buffer.add(entry("b"))

        val viewModel = SettingsLogsViewModel(buffer)
        val logs = viewModel.logs.value

        assertEquals(listOf("a", "b"), logs.map { it.message })
    }

    @Test
    fun `clearLogs delegiert an den LogBuffer`() = runTest {
        val buffer = mockk<LogBuffer>(relaxed = true)
        val viewModel = SettingsLogsViewModel(buffer)

        viewModel.clearLogs()

        verify { buffer.clear() }
    }
}