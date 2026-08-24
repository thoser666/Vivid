package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.StreamScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-Scene-Switcher (Moblin-Parität): wechselt die aktive Szene
 * automatisch in einem festen Zeitintervall (zeitbasiert).
 *
 * Der Wechsel läuft nur, solange [enabled] = true ist; pro Intervall wird die
 * nächste Szene in Anlage-Reihenfolge angewendet (nach der aktiven, kreisend;
 * ohne aktive Szene beginnt der Zyklus vorne). Weniger als zwei Szenen sind
 * ein No-op. Regelbasierte Trigger (z. B. „wenn Quelle X aktiv“) sind als
 * offener Task dokumentiert — die Schleife ist der Erweiterungspunkt.
 */
@Singleton
class AutoSceneSwitcher @Inject constructor(
    private val scope: CoroutineScope,
    private val sceneRepository: SceneRepository,
    private val sceneController: SceneController,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(DEFAULT_INTERVAL_SECONDS)
    val intervalSeconds: StateFlow<Long> = _intervalSeconds.asStateFlow()

    private var job: Job? = null

    /** Schaltet den automatischen Wechsel an/aus (startet/stoppt die Schleife). */
    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (value) {
            if (job == null) {
                job = scope.launch { runLoop() }
            }
        } else {
            job?.cancel()
            job = null
        }
    }

    /** Setzt das Wechsel-Intervall (Sekunden), geclampt auf das Minimum. */
    fun setIntervalSeconds(value: Long) {
        _intervalSeconds.value = max(value, MIN_INTERVAL_SECONDS)
    }

    private suspend fun runLoop() {
        while (_enabled.value) {
            delay(_intervalSeconds.value * 1000)
            val scenes = sceneRepository.scenesFlow.first()
            if (scenes.size >= 2) {
                val nextIndex = nextSceneIndex(scenes, sceneRepository.activeSceneIdFlow.first())
                sceneController.applyScene(scenes[nextIndex])
            }
        }
    }

    companion object {
        const val MIN_INTERVAL_SECONDS = 5L
        const val DEFAULT_INTERVAL_SECONDS = 60L

        /**
         * Wählt den Index der nächsten Szene: nach der aktiven (kreisweise);
         * ist keine aktiv oder die aktive nicht mehr vorhanden, die erste.
         * Bei weniger als zwei Szenen gibt es nichts zu wechseln (Index 0).
         */
        fun nextSceneIndex(scenes: List<StreamScene>, activeSceneId: String?): Int {
            if (scenes.size < 2) return 0
            val currentIndex = scenes.indexOfFirst { it.id == activeSceneId }
            return if (currentIndex < 0) 0 else (currentIndex + 1) % scenes.size
        }
    }
}