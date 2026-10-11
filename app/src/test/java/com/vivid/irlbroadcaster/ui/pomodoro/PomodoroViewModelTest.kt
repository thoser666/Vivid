package com.vivid.irlbroadcaster.ui.pomodoro

import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests des [PomodoroViewModel]: Einstellungen werden einmalig gelesen, der
 * Sekundentakt läuft über einen virtuellen Test-Scheduler, Phasen-Enden landen
 * im [PomodoroViewModel.phaseComplete]-Flow.
 *
 * WICHTIG: Der Sekundentakt des ViewModels ist eine `while(true)`-Schleife in
 * `viewModelScope`. Da dieser Scope denselben Test-Scheduler wie `runTest`
 * benutzt, würde `runTest`s abschließendes `advanceUntilIdle()` die Endlosschleife
 * nie leerlaufen lassen — jeder Test bricht den Scope daher explizit ab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repository(
        enabled: Boolean = true,
        work: Int = 1,
        breakMinutes: Int = 1,
    ): SettingsRepository = mockk(relaxed = true) {
        every { appSettingsFlow } returns flowOf(
            AppSettings(
                pomodoroEnabled = enabled,
                pomodoroWorkDurationMinutes = work,
                pomodoroBreakDurationMinutes = breakMinutes,
            ),
        )
    }

    @Test
    fun `initial state mirrors the persisted settings`() = runTest(dispatcher) {
        val viewModel = PomodoroViewModel(repository(work = 25, breakMinutes = 5))

        val state = viewModel.uiState.value
        assertTrue(state.enabled)
        assertEquals(25 * 60, state.remainingSeconds)
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertFalse(state.running)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `tick advances the countdown once started`() = runTest(dispatcher) {
        val viewModel = PomodoroViewModel(repository())

        viewModel.toggleRunning()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(59, viewModel.uiState.value.remainingSeconds)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `a completed work phase flips to break and emits one event`() = runTest(dispatcher) {
        val viewModel = PomodoroViewModel(repository())
        val events = mutableListOf<PomodoroPhase>()
        val collector = launch { viewModel.phaseComplete.collect { events += it } }

        viewModel.toggleRunning()
        advanceTimeBy(60_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(PomodoroPhase.BREAK, state.phase)
        assertEquals(1, state.completedWorkSessions)
        assertEquals(listOf(PomodoroPhase.WORK), events)

        collector.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `disabled timer never starts`() = runTest(dispatcher) {
        val viewModel = PomodoroViewModel(repository(enabled = false))

        viewModel.toggleRunning()
        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(viewModel.uiState.value.running)
        assertEquals(60, viewModel.uiState.value.remainingSeconds)

        viewModel.viewModelScope.cancel()
    }
}
