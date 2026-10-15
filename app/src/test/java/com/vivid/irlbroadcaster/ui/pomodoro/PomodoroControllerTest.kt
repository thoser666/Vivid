package com.vivid.irlbroadcaster.ui.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JVM-Tests der Pomodoro-Zustandsmaschine (kein Android, keine Coroutine).
 * Treibt den Controller sekundenweise über [PomodoroController.tick].
 */
class PomodoroControllerTest {

    private fun controller(
        enabled: Boolean = true,
        work: Int = 1,
        breakMinutes: Int = 1,
    ) = PomodoroController(enabled = enabled, workDurationMinutes = work, breakDurationMinutes = breakMinutes)

    @Test
    fun `initial snapshot is a paused work phase`() {
        val state = controller(work = 25, breakMinutes = 5).snapshot()

        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(25 * 60, state.remainingSeconds)
        assertEquals(0, state.completedWorkSessions)
        assertFalse(state.running)
        assertTrue(state.enabled)
    }

    @Test
    fun `tick only decrements while running`() {
        val c = controller()
        c.tick()
        assertEquals(60, c.snapshot().remainingSeconds) // pausiert → unverändert

        c.start()
        assertTrue(c.snapshot().running)
        c.tick()
        assertEquals(59, c.snapshot().remainingSeconds)
    }

    @Test
    fun `tick does nothing when disabled`() {
        val c = controller(enabled = false)
        c.start()
        c.tick()

        assertFalse(c.snapshot().running)
        assertEquals(60, c.snapshot().remainingSeconds)
    }

    @Test
    fun `work phase completing flips to break counts the session and notifies`() {
        val completed = mutableListOf<PomodoroPhase>()
        val c = controller()
        c.onPhaseComplete = { completed += it }
        c.start()

        repeat(60) { c.tick() }

        val state = c.snapshot()
        assertEquals(PomodoroPhase.BREAK, state.phase)
        assertEquals(60, state.remainingSeconds) // Pause = 1 min
        assertEquals(1, state.completedWorkSessions)
        assertTrue(state.running) // Loop läuft weiter
        assertEquals(listOf(PomodoroPhase.WORK), completed)
    }

    @Test
    fun `break phase completing loops back to work without counting a session`() {
        val completed = mutableListOf<PomodoroPhase>()
        val c = controller()
        c.onPhaseComplete = { completed += it }
        c.start()

        repeat(120) { c.tick() } // Arbeit (60) + Pause (60)

        val state = c.snapshot()
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(60, state.remainingSeconds)
        assertEquals(1, state.completedWorkSessions)
        assertEquals(listOf(PomodoroPhase.WORK, PomodoroPhase.BREAK), completed)
    }

    @Test
    fun `skipPhase switches instantly without notifying or counting`() {
        val completed = mutableListOf<PomodoroPhase>()
        val c = controller()
        c.onPhaseComplete = { completed += it }

        c.skipPhase()
        assertEquals(PomodoroPhase.BREAK, c.snapshot().phase)

        c.skipPhase()
        assertEquals(PomodoroPhase.WORK, c.snapshot().phase)
        assertEquals(0, c.snapshot().completedWorkSessions)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `toggleRunning flips the running flag only when enabled`() {
        val c = controller()
        c.toggleRunning()
        assertTrue(c.snapshot().running)
        c.toggleRunning()
        assertFalse(c.snapshot().running)

        val disabled = controller(enabled = false)
        disabled.toggleRunning()
        assertFalse(disabled.snapshot().running)
    }

    @Test
    fun `reset returns to the first work phase and clears the counter`() {
        val c = controller(work = 2, breakMinutes = 1)
        c.start()
        repeat(120) { c.tick() } // eine komplette Arbeitsphase
        assertEquals(1, c.snapshot().completedWorkSessions)

        c.reset()

        val state = c.snapshot()
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(2 * 60, state.remainingSeconds)
        assertEquals(0, state.completedWorkSessions)
        assertFalse(state.running)
    }

    @Test
    fun `formatMmSs pads minutes and seconds`() {
        assertEquals("00:00", formatMmSs(0))
        assertEquals("00:05", formatMmSs(5))
        assertEquals("01:05", formatMmSs(65))
        assertEquals("25:00", formatMmSs(25 * 60))
        assertEquals("00:00", formatMmSs(-3))
    }
}
