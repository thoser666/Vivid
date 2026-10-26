package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneTransitionType

/** The legacy pipeline input can be reused as one texture without another capture session. */
data class SceneRenderState(
    val composition: SceneComposition,
    val inputSourceId: String? = null,
) {
    init {
        require(inputSourceId == null || composition.sources.any { it.id == inputSourceId })
    }
}

data class SceneFramePlan(
    val current: SceneRenderState,
    val outgoing: SceneRenderState? = null,
    val progress: Float = 1f,
) {
    val compositions: List<SceneComposition>
        get() = listOfNotNull(outgoing?.composition, current.composition)
}

/** Monotonic, bounded transitions; the renderer blends complete scenes, not individual layers. */
class SceneTransitionClock {
    private var current: SceneRenderState? = null
    private var outgoing: SceneRenderState? = null
    private var startedAtMillis = 0L
    private var durationMillis = 0L
    private var lastTimeMillis = 0L

    fun replace(state: SceneRenderState, nowMillis: Long, animate: Boolean = true) {
        require(nowMillis >= 0)
        val previous = current
        if (state == previous) return
        val transition = state.composition.transition
        outgoing = previous?.takeIf {
            animate && transition.type == SceneTransitionType.FADE && transition.durationMillis > 0 &&
                compatible(it, state)
        }
        current = state
        startedAtMillis = maxOf(nowMillis, lastTimeMillis)
        durationMillis = transition.durationMillis
    }

    fun frame(nowMillis: Long): SceneFramePlan? {
        require(nowMillis >= 0)
        val target = current ?: return null
        lastTimeMillis = maxOf(lastTimeMillis, nowMillis)
        val progress = if (outgoing == null) 1f else
            ((lastTimeMillis - startedAtMillis).toDouble() / durationMillis).toFloat().coerceIn(0f, 1f)
        if (progress >= 1f) outgoing = null
        return SceneFramePlan(target, outgoing, progress)
    }

    fun clear() {
        current = null
        outgoing = null
        lastTimeMillis = 0L
    }

    private fun compatible(first: SceneRenderState, second: SceneRenderState): Boolean {
        // The old legacy input cannot be held after switching that pipeline to another capture.
        if (first.inputSourceId != second.inputSourceId) return false
        val sources = (first.composition.sources + second.composition.sources)
            .groupBy { it.id }
        return sources.size <= SceneComposition.MAX_SOURCES && sources.values.all { sameId ->
            sameId.distinctBy { Triple(it.type, it.locator, if (it.type == com.vivid.core.data.SceneSourceType.VIDEO) it.videoLoop ?: true else null) }.size == 1
        }
    }
}
