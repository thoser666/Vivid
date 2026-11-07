package com.vivid.core.data

/** Phase des Pomodoro-Zyklus. */
enum class PomodoroPhase { WORK, BREAK }

/**
 * Unveränderlicher UI-Zustand des Pomodoro-Timers.
 *
 * Bewusst KEIN Android-Import — die Zustandslogik bleibt damit als reine
 * JVM-Klasse unit-testbar ([PomodoroControllerTest]).
 */
data class PomodoroUiState(
    val enabled: Boolean = false,
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    val remainingSeconds: Int = 25 * 60,
    val completedWorkSessions: Int = 0,
    val running: Boolean = false,
    val workDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
)

/** Sekunden als `MM:SS`, auf zwei Ziffern pro Feld (auch über 59 min). */
fun formatMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Reine Zustandsmaschine des Pomodoro-Widgets (PARITY-Zeile 143).
 *
 * Kein Alarm/keine Coroutine im Controller: Der Aufrufer treibt ihn per
 * [tick] (eine Sekunde pro Aufruf) und hängt sich über [onPhaseComplete]
 * an das Phasenende. So bleibt die gesamte Timing-Logik deterministisch
 * testbar, während die Android-Seite (Vibration) im Screen/ViewModel sitzt.
 */
class PomodoroController(
    enabled: Boolean,
    val workDurationMinutes: Int,
    val breakDurationMinutes: Int,
) {
    var enabled: Boolean = enabled
        private set

    private var phase: PomodoroPhase = PomodoroPhase.WORK
    private var remainingSeconds: Int = workDurationMinutes * 60
    private var completedWorkSessions: Int = 0
    private var running: Boolean = false

    /** Wird beim regulären Phasenende (Timer läuft auf 0) aufgerufen — nicht bei [skipPhase]. */
    var onPhaseComplete: ((PomodoroPhase) -> Unit)? = null

    fun snapshot(): PomodoroUiState = PomodoroUiState(
        enabled = enabled,
        phase = phase,
        remainingSeconds = remainingSeconds,
        completedWorkSessions = completedWorkSessions,
        running = running,
        workDurationMinutes = workDurationMinutes,
        breakDurationMinutes = breakDurationMinutes,
    )

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) pause()
    }

    /** Startet den Countdown (nur wenn freigeschaltet). */
    fun start() {
        if (enabled) running = true
    }

    /** Wechselt zwischen Laufen und Pause (nur wenn freigeschaltet). */
    fun toggleRunning() {
        if (!enabled) return
        running = !running
    }

    fun pause() {
        running = false
    }

    /** Ein Sekundenschritt. Tut nichts, wenn pausiert oder nicht freigeschaltet. */
    fun tick() {
        if (!enabled || !running) return
        if (remainingSeconds > 0) remainingSeconds -= 1
        if (remainingSeconds == 0) advancePhase()
    }

    private fun advancePhase() {
        val finished = phase
        if (phase == PomodoroPhase.WORK) {
            completedWorkSessions += 1
            phase = PomodoroPhase.BREAK
            remainingSeconds = breakDurationMinutes * 60
        } else {
            phase = PomodoroPhase.WORK
            remainingSeconds = workDurationMinutes * 60
        }
        onPhaseComplete?.invoke(finished)
    }

    /**
     * Aktuelle Phase sofort beenden (Nutzeraktion). Löst bewusst KEIN
     * [onPhaseComplete] (keine Vibration) aus und zählt keine Session hoch.
     */
    fun skipPhase() {
        if (!enabled) return
        if (phase == PomodoroPhase.WORK) {
            phase = PomodoroPhase.BREAK
            remainingSeconds = breakDurationMinutes * 60
        } else {
            phase = PomodoroPhase.WORK
            remainingSeconds = workDurationMinutes * 60
        }
    }

    /** Setzt auf die erste Arbeitsphase zurück (Zähler und Laufzustand inklusive). */
    fun reset() {
        phase = PomodoroPhase.WORK
        remainingSeconds = workDurationMinutes * 60
        completedWorkSessions = 0
        running = false
    }
}
