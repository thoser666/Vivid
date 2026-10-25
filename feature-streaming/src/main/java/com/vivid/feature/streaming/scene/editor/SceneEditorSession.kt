package com.vivid.feature.streaming.scene.editor

import com.vivid.core.data.*
import com.vivid.feature.streaming.compositor.SceneGeometry
import java.util.ArrayDeque
import java.util.UUID

/** A complete draft is saved atomically; gestures are one undo transaction. */
data class SceneEditorState(
    val scene: StreamScene,
    val selectedLayerId: String?,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val verticalGuide: Float? = null,
    val horizontalGuide: Float? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val composition: SceneComposition get() = requireNotNull(scene.composition)
    val selectedLayer: SceneLayer? get() = composition.layers.find { it.id == selectedLayerId }
}

class SceneEditorSession(
    scene: StreamScene,
    composition: SceneComposition = scene.effectiveComposition(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var state = SceneEditorState(scene.copy(composition = composition), composition.layers.lastOrNull()?.id)
        private set
    private data class History(val scene: StreamScene, val selected: String?)
    private val undo = ArrayDeque<History>()
    private val redo = ArrayDeque<History>()
    private var gestureStart: History? = null
    private fun history() = History(state.scene, state.selectedLayerId)

    fun select(layerId: String?) {
        require(layerId == null || state.composition.layers.any { it.id == layerId })
        state = state.copy(selectedLayerId = layerId, verticalGuide = null, horizontalGuide = null)
    }

    fun selectAt(x: Float, y: Float): String? {
        val graph = state.composition
        val layer = graph.layers.asReversed().firstOrNull {
            it.visible && it.opacity > 0f && SceneGeometry.contains(it.transform,
                SceneGeometry.Point(x, y), graph.canvasWidth, graph.canvasHeight)
        }
        select(layer?.id)
        return layer?.id
    }

    /** Reusing a source ID creates another view of that source. */
    fun addSource(source: SceneSource): Boolean {
        val graph = state.composition
        if (graph.layers.size >= SceneComposition.MAX_LAYERS) return false
        val existing = graph.sources.find { it.id == source.id }
        if (existing != null && (existing.type != source.type || existing.locator != source.locator)) return false
        if (existing == null && graph.sources.size >= SceneComposition.MAX_SOURCES) return false
        val layer = SceneLayer(newId(), source.id, if (graph.layers.isEmpty()) SceneTransform()
            else SceneTransform(0.1f, 0.1f, 0.4f, 0.4f))
        change(graph.copy(sources = if (existing == null) graph.sources + source else graph.sources,
            layers = graph.layers + layer), layer.id)
        return true
    }

    fun duplicateSelected(): Boolean {
        val layer = state.selectedLayer ?: return false
        if (state.composition.layers.size >= SceneComposition.MAX_LAYERS) return false
        val id = newId()
        change(state.composition.duplicateLayer(layer.id, id), id)
        return true
    }

    fun deleteSelected() {
        val layer = state.selectedLayer ?: return
        val graph = state.composition
        val layers = graph.layers.filter { it.id != layer.id }
        val used = layers.map { it.sourceId }.toSet()
        change(graph.copy(layers = layers, sources = graph.sources.filter { it.id in used }), layers.lastOrNull()?.id)
    }

    fun moveSelected(direction: Int) {
        val layer = state.selectedLayer ?: return
        val index = state.composition.layers.indexOf(layer)
        val destination = (index + direction).coerceIn(0, state.composition.layers.lastIndex)
        if (destination != index) change(state.composition.moveLayer(layer.id, destination))
    }

    fun setVisible(visible: Boolean) = editLayer { it.copy(visible = visible) }
    fun setOpacity(opacity: Float) = editLayer { it.copy(opacity = opacity.coerceIn(0f, 1f)) }
    fun setCrop(crop: SceneCrop) = editLayer { it.copy(transform = it.transform.copy(crop = crop)) }
    fun setTransform(transform: SceneTransform) = editLayer { it.copy(transform = transform) }

    fun rename(name: String) {
        if (name.isBlank() || state.scene.name == name) return
        rememberBeforeChange()
        state = state.copy(scene = state.scene.copy(name = name), saveFailed = false)
        updateHistory()
    }

    fun editSource(source: SceneSource) {
        if (state.composition.sources.none { it.id == source.id }) return
        change(state.composition.copy(sources = state.composition.sources.map {
            if (it.id == source.id) source else it
        }))
    }

    /** Layout operations preserve layer/source identities and all hidden/extra layers. */
    fun layout(preset: SceneLayoutPreset) {
        val graph = state.composition
        if (graph.layers.isEmpty()) return
        val arranged = graph.layers.mapIndexed { index, layer ->
            val transform = when (preset) {
                SceneLayoutPreset.FULLSCREEN -> if (layer.id == state.selectedLayerId) SceneTransform() else layer.transform
                SceneLayoutPreset.PICTURE_IN_PICTURE -> when (index) {
                    0 -> SceneTransform()
                    1 -> SceneTransform(0.67f, 0.67f, 0.3f, 0.3f)
                    else -> layer.transform
                }
                SceneLayoutPreset.SIDE_BY_SIDE -> when (index) {
                    0 -> SceneTransform(width = 0.5f)
                    1 -> SceneTransform(x = 0.5f, width = 0.5f)
                    else -> layer.transform
                }
            }
            layer.copy(transform = transform)
        }
        change(graph.copy(layers = arranged))
    }

    fun setTransition(type: SceneTransitionType, durationMillis: Long = state.composition.transition.durationMillis) {
        change(state.composition.copy(transition = SceneTransition(type,
            durationMillis.coerceIn(0, SceneTransition.MAX_DURATION_MILLIS))))
    }

    fun beginGesture() { if (gestureStart == null) gestureStart = history() }

    fun transformGesture(transform: SceneTransform, canvasWidth: Int, canvasHeight: Int) {
        val snap = SceneGeometry.snap(transform, canvasWidth, canvasHeight)
        setTransform(snap.transform)
        state = state.copy(verticalGuide = snap.verticalGuide, horizontalGuide = snap.horizontalGuide)
    }

    fun endGesture() {
        gestureStart?.let { before -> if (before.scene != state.scene) { push(undo, before); redo.clear() } }
        gestureStart = null
        state = state.copy(verticalGuide = null, horizontalGuide = null)
        updateHistory()
    }

    fun undo() {
        endGesture()
        if (undo.isEmpty()) return
        push(redo, history())
        restore(undo.removeLast())
    }

    fun redo() {
        endGesture()
        if (redo.isEmpty()) return
        push(undo, history())
        restore(redo.removeLast())
    }

    fun setSaving(saving: Boolean, failed: Boolean = false) { state = state.copy(saving = saving, saveFailed = failed) }

    private fun restore(history: History) {
        val scene = history.scene
        val selected = history.selected?.takeIf { id -> scene.composition!!.layers.any { it.id == id } }
        state = state.copy(scene = scene, selectedLayerId = selected ?: scene.composition!!.layers.lastOrNull()?.id,
            verticalGuide = null, horizontalGuide = null, saveFailed = false)
        updateHistory()
    }

    private fun editLayer(edit: (SceneLayer) -> SceneLayer) {
        val selected = state.selectedLayer ?: return
        change(state.composition.copy(layers = state.composition.layers.map {
            if (it.id == selected.id) edit(it) else it
        }))
    }

    private fun change(graph: SceneComposition, selection: String? = state.selectedLayerId) {
        if (graph == state.composition) return
        rememberBeforeChange()
        state = state.copy(scene = state.scene.copy(composition = graph), selectedLayerId = selection,
            saveFailed = false)
        updateHistory()
    }

    private fun rememberBeforeChange() {
        if (gestureStart == null) { push(undo, history()); redo.clear() }
    }
    private fun updateHistory() { state = state.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty()) }
    private fun push(stack: ArrayDeque<History>, scene: History) {
        if (stack.size >= MAX_HISTORY) stack.removeFirst()
        stack.addLast(scene)
    }
    companion object { const val MAX_HISTORY = 32 }
}
