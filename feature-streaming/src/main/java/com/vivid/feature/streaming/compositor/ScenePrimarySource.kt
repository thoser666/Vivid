package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneLayer
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.capture.SceneUsbLocator
import java.util.UUID

internal data class ScenePrimarySource(val graph: SceneComposition, val sourceId: String)

/** Bind main-input views to reusable producers; never change an unrelated source definition. */
internal fun replaceScenePrimarySource(
    graph: SceneComposition,
    sourceId: String?,
    previousType: SceneSourceType,
    nextType: SceneSourceType,
    locator: String,
    cameraLocator: (SceneSource) -> String = { it.locator },
    videoLoop: Boolean? = null,
): ScenePrimarySource? {
    var bindings = graph.layers.filter { it.followsPrimaryInput }.map { it.id }.toSet()
    if (bindings.isEmpty() && !graph.primaryInputConfigured) {
        // Old schema-2 graphs had no view-role metadata. Adopt only their previous input.
        val old = graph.sources.firstOrNull { it.id == sourceId }
            ?: graph.sources.firstOrNull { it.type == previousType }
        bindings = graph.layers.filter { it.sourceId == old?.id }.map { it.id }.toSet()
    }
    return bindSceneLayers(graph, bindings, nextType, locator, true, cameraLocator, videoLoop)
}

/** Lens controls act on their camera views even when the main input is a movie. */
internal fun rebindSceneSourceLayers(
    graph: SceneComposition,
    sourceId: String,
    nextType: SceneSourceType,
    locator: String,
    cameraLocator: (SceneSource) -> String = { it.locator },
): ScenePrimarySource? {
    val bindings = graph.layers.filter { it.sourceId == sourceId }.map { it.id }.toSet()
    if (bindings.isEmpty()) return null
    return bindSceneLayers(graph, bindings, nextType, locator, false, cameraLocator)
}

internal fun SceneSource.matchesSceneIdentity(
    type: SceneSourceType,
    locator: String,
    cameraLocator: (SceneSource) -> String,
    videoLoop: Boolean? = null,
): Boolean {
    val source = this
    if (source.type == SceneSourceType.VIDEO && type == SceneSourceType.VIDEO) {
        return source.locator == locator && (videoLoop == null || (source.videoLoop ?: true) == videoLoop)
    }
    if (source.type == type && source.locator == locator) return true
    val cameras = setOf(SceneSourceType.CAMERA, SceneSourceType.UVC)
    if (source.type !in cameras || type !in cameras ||
        SceneUsbLocator.isRawLocator(source.locator) || SceneUsbLocator.isRawLocator(locator)) {
        return source.type == type && source.locator == locator
    }
    val resolved = cameraLocator(source)
    return resolved == locator && (source.type == type || locator.isNotBlank())
}

private fun bindSceneLayers(
    graph: SceneComposition,
    bindings: Set<String>,
    nextType: SceneSourceType,
    locator: String,
    primary: Boolean,
    cameraLocator: (SceneSource) -> String,
    videoLoop: Boolean? = null,
): ScenePrimarySource? {
    if (bindings.isEmpty() && graph.layers.size == SceneComposition.MAX_LAYERS) return null
    val previousIds = graph.layers.filter { it.id in bindings }.map { it.sourceId }.toSet()
    val target = graph.sources.firstOrNull { it.id in previousIds && it.matchesSceneIdentity(nextType, locator, cameraLocator, videoLoop) }
        ?: graph.sources.firstOrNull { it.matchesSceneIdentity(nextType, locator, cameraLocator, videoLoop) }
        ?: SceneSource(UUID.randomUUID().toString(), nextType, locator, videoLoop = videoLoop)
    val layers = if (bindings.isEmpty()) listOf(SceneLayer(UUID.randomUUID().toString(), target.id,
        followsPrimaryInput = primary)) + graph.layers else graph.layers.map {
        if (it.id in bindings) it.copy(sourceId = target.id, followsPrimaryInput = primary || it.followsPrimaryInput) else it
    }
    var sources = graph.sources
    if (sources.none { it.id == target.id }) {
        if (sources.size == SceneComposition.MAX_SOURCES) {
            val replaced = graph.layers.filter { it.id in bindings }.map { it.sourceId }.toSet()
            val used = layers.map { it.sourceId }.toSet()
            // Only retire an input that has no independent view. Never evict another saved source.
            sources = sources.filterNot { it.id in replaced && it.id !in used }
        }
        if (sources.size == SceneComposition.MAX_SOURCES) return null
        sources = sources + target
    }
    return ScenePrimarySource(graph.copy(sources = sources, layers = layers,
        primaryInputConfigured = graph.primaryInputConfigured || primary), target.id)
}
