package com.vivid.feature.widget

import androidx.lifecycle.viewModelScope
import com.vivid.core.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroWidgetManagerTest {
    private fun widget(id: String = "timer", work: Int = 1) = StreamWidget(id, "Pomodoro", WidgetType.POMODORO,
        configuration = WidgetConfiguration(pomodoroWorkDurationMinutes = work, pomodoroBreakDurationMinutes = 1))

    private class Harness(scope: TestScope, initial: List<StreamWidget>) {
        val definitions = MutableStateFlow(initial)
        val repository = mockk<SceneRepository> { every { widgetsFlow } returns definitions }
        val notifier = mockk<PomodoroNotifier>(relaxed = true)
        val manager = PomodoroWidgetManager(repository, scope.backgroundScope, notifier).apply {
            now = { scope.testScheduler.currentTime }
        }
    }

    @Test fun `started widget counts work break cycles and signals completion`() = runTest {
        val timer = widget()
        val h = Harness(this, listOf(timer))
        runCurrent()
        assertFalse(h.manager.states.value.getValue(timer.id).running)
        h.manager.start(timer)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(59, h.manager.states.value.getValue(timer.id).remainingSeconds)
        advanceTimeBy(59_000); runCurrent()
        val state = h.manager.states.value.getValue(timer.id)
        assertEquals(PomodoroPhase.BREAK, state.phase)
        assertEquals(1, state.completedWorkSessions)
        assertTrue(state.running)
        verify(exactly = 1) { h.notifier.phaseCompleted() }
    }

    @Test fun `content and scene assignment edits do not reset the running timer`() = runTest {
        val timer = widget()
        val h = Harness(this, listOf(timer))
        runCurrent(); h.manager.start(timer)
        advanceTimeBy(5_000); runCurrent()
        h.definitions.value = listOf(timer.copy(name = "Renamed"), StreamWidget("image", "Logo", WidgetType.IMAGE))
        runCurrent()
        assertEquals(55, h.manager.states.value.getValue(timer.id).remainingSeconds)
        assertTrue(h.manager.states.value.getValue(timer.id).running)
        // The same definition can be rendered by another scene without restarting.
        h.manager.start(timer.copy(name = "Renamed"))
        assertEquals(55, h.manager.states.value.getValue(timer.id).remainingSeconds)
    }

    @Test fun `disabling pauses and re-enabling preserves remaining time without auto starting`() = runTest {
        val timer = widget()
        val h = Harness(this, listOf(timer))
        runCurrent(); h.manager.start(timer)
        advanceTimeBy(5_000); runCurrent()
        h.definitions.value = listOf(timer.copy(enabled = false)); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(55, h.manager.states.value.getValue(timer.id).remainingSeconds)
        assertFalse(h.manager.states.value.getValue(timer.id).running)
        h.definitions.value = listOf(timer); runCurrent()
        assertEquals(55, h.manager.states.value.getValue(timer.id).remainingSeconds)
        assertFalse(h.manager.states.value.getValue(timer.id).running)
        h.definitions.value = emptyList(); runCurrent()
        assertTrue(h.manager.states.value.isEmpty())
    }

    @Test fun `duration edits reset only their widget and skip reset remain independent`() = runTest {
        val first = widget("a")
        val second = widget("b", work = 2)
        val h = Harness(this, listOf(first, second))
        runCurrent(); h.manager.start(first); h.manager.start(second)
        advanceTimeBy(5_000); runCurrent()
        h.definitions.value = listOf(first.copy(configuration = first.configuration.copy(pomodoroWorkDurationMinutes = 3)), second)
        runCurrent()
        assertEquals(180, h.manager.states.value.getValue("a").remainingSeconds)
        assertFalse(h.manager.states.value.getValue("a").running)
        assertEquals(115, h.manager.states.value.getValue("b").remainingSeconds)
        assertTrue(h.manager.states.value.getValue("b").running)
        h.manager.skip("b")
        assertEquals(PomodoroPhase.BREAK, h.manager.states.value.getValue("b").phase)
        assertEquals(0, h.manager.states.value.getValue("b").completedWorkSessions)
        h.manager.reset("b")
        assertEquals(120, h.manager.states.value.getValue("b").remainingSeconds)
        assertFalse(h.manager.states.value.getValue("b").running)
        verify(exactly = 0) { h.notifier.phaseCompleted() }
    }

    @Test fun `delayed coroutine catches up against monotonic elapsed time`() = runTest {
        val timer = widget()
        val h = Harness(this, listOf(timer))
        var clock = 0L
        h.manager.now = { clock }
        runCurrent(); h.manager.start(timer)
        clock = 10_000L
        advanceTimeBy(1_000); runCurrent()
        assertEquals(50, h.manager.states.value.getValue(timer.id).remainingSeconds)
    }

    @Test fun `save and start persists current draft before starting independently of legacy enabled flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val timer = widget(work = 25)
            val h = Harness(this, listOf(timer))
            val edited = widget(work = 1)
            coEvery { h.repository.saveWidgetWithSceneVisibility(edited, mapOf("scene" to true)) } answers {
                h.definitions.value = listOf(edited)
            }
            val viewModel = PomodoroWidgetViewModel(h.repository, h.manager)
            runCurrent()
            viewModel.saveAndStart(edited, mapOf("scene" to true)); runCurrent()
            coVerify { h.repository.saveWidgetWithSceneVisibility(edited, mapOf("scene" to true)) }
            val state = h.manager.states.value.getValue(edited.id)
            assertEquals(60, state.remainingSeconds)
            assertTrue(state.enabled)
            assertTrue(state.running)
            viewModel.viewModelScope.cancel()
        } finally { Dispatchers.resetMain() }
    }
}
