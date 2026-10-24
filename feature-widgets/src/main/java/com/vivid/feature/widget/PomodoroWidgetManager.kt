package com.vivid.feature.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.PomodoroController
import com.vivid.core.data.PomodoroUiState
import com.vivid.core.data.SceneRepository
import com.vivid.core.data.StreamWidget
import com.vivid.core.data.WidgetType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

class PomodoroNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun phaseCompleted() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(400L)
            }
        }
    }
}

/** One timer per library widget, independent of scene visibility and editor navigation. */
@Singleton
class PomodoroWidgetManager @Inject constructor(
    repository: SceneRepository,
    scope: CoroutineScope,
    private val notifier: PomodoroNotifier,
) {
    private class Runtime(val controller: PomodoroController, var lastTickMs: Long) {
        var phaseCompleted = false
    }
    private val timers = mutableMapOf<String, Runtime>()
    private val _states = MutableStateFlow<Map<String, PomodoroUiState>>(emptyMap())
    val states = _states.asStateFlow()
    internal var now: () -> Long = { SystemClock.elapsedRealtime() }

    init {
        scope.launch {
            repository.widgetsFlow.collect { definitions ->
                synchronized(this@PomodoroWidgetManager) {
                    val widgets = definitions.filter { it.type == WidgetType.POMODORO }
                    timers.keys.retainAll(widgets.map { it.id }.toSet())
                    widgets.forEach { configure(it) }
                    publish()
                }
            }
        }
        scope.launch {
            while (isActive) {
                _states.first { states -> states.values.any { it.enabled && it.running } }
                delay(1_000L)
                synchronized(this@PomodoroWidgetManager) {
                    val timestamp = now()
                    timers.values.forEach { advance(it, timestamp) }
                    publish()
                }
            }
        }
    }

    private fun configure(widget: StreamWidget): Runtime {
        val config = widget.configuration
        val work = config.pomodoroWorkDurationMinutes.coerceIn(1, 120)
        val rest = config.pomodoroBreakDurationMinutes.coerceIn(1, 60)
        val previous = timers[widget.id]
        if (previous != null && previous.controller.workDurationMinutes == work && previous.controller.breakDurationMinutes == rest) {
            if (previous.controller.enabled != widget.enabled) {
                advance(previous, now())
                previous.controller.setEnabled(widget.enabled)
                previous.lastTickMs = now()
            }
            return previous
        }
        return Runtime(PomodoroController(widget.enabled, work, rest), now()).also { runtime ->
            runtime.controller.onPhaseComplete = { runtime.phaseCompleted = true }
            timers[widget.id] = runtime
        }
    }

    private fun advance(timer: Runtime, timestamp: Long) {
        val state = timer.controller.snapshot()
        if (!state.enabled || !state.running) {
            timer.lastTickMs = timestamp
            return
        }
        val seconds = ((timestamp - timer.lastTickMs).coerceAtLeast(0L) / 1_000L).toInt()
        repeat(seconds) { timer.controller.tick() }
        timer.lastTickMs += seconds * 1_000L
        // Catching up after a delayed coroutine should only signal once.
        if (timer.phaseCompleted) notifier.phaseCompleted()
        timer.phaseCompleted = false
    }

    private fun publish() { _states.value = timers.mapValues { it.value.controller.snapshot() } }

    @Synchronized
    fun toggle(widget: StreamWidget) {
        val timer = configure(widget)
        advance(timer, now())
        timer.controller.toggleRunning()
        timer.lastTickMs = now()
        publish()
    }

    @Synchronized
    fun start(widget: StreamWidget) {
        val timer = configure(widget)
        advance(timer, now())
        if (!timer.controller.snapshot().running) timer.lastTickMs = now()
        timer.controller.start()
        publish()
    }

    @Synchronized
    fun pause(id: String) {
        timers[id]?.let { advance(it, now()); it.controller.pause() }
        publish()
    }

    @Synchronized
    fun skip(id: String) {
        timers[id]?.let { advance(it, now()); it.controller.skipPhase(); it.lastTickMs = now() }
        publish()
    }

    @Synchronized
    fun reset(id: String) {
        timers[id]?.let { it.controller.reset(); it.lastTickMs = now() }
        publish()
    }
}

@HiltViewModel
class PomodoroWidgetViewModel @Inject constructor(
    private val repository: SceneRepository,
    private val manager: PomodoroWidgetManager,
) : ViewModel() {
    val states = manager.states
    fun saveAndStart(widget: StreamWidget, scenes: Map<String, Boolean>) {
        viewModelScope.launch {
            repository.saveWidgetWithSceneVisibility(widget, scenes)
            manager.start(widget)
        }
    }
    fun toggle(widget: StreamWidget) = manager.toggle(widget)
    fun pause(id: String) = manager.pause(id)
    fun skip(id: String) = manager.skip(id)
    fun reset(id: String) = manager.reset(id)
}
