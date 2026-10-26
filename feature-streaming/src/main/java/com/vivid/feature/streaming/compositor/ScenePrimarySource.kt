package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneLayer
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import java.util.UUID

internal data class ScenePrimarySource(val graph: SceneComposition, val sourceId: String)

/** A global source switch must never overwrite an unrelated image/widget layer. */
internal fun replaceScenePrimarySource(
    graph: SceneComposition,
    sourceId: String?,
    previousType: SceneSourceType,
    nextType: SceneSourceType,
    locator: String,
): ScenePrimarySource? {
    val old = graph.sources.firstOrNull { it.id == sourceId }
        ?: graph.sources.firstOrNull { it.type == previousType }
    if (old != null) return ScenePrimarySource(graph.copy(sources = graph.sources.map {
        if (it.id == old.id) it.copy(type = nextType, locator = locator) else it
    }), old.id)
    if (graph.sources.size == SceneComposition.MAX_SOURCES || graph.layers.size == SceneComposition.MAX_LAYERS) return null
    val source = SceneSource(UUID.randomUUID().toString(), nextType, locator)
    val layer = SceneLayer(UUID.randomUUID().toString(), source.id)
    return ScenePrimarySource(graph.copy(sources = graph.sources + source, layers = listOf(layer) + graph.layers), source.id)
}
