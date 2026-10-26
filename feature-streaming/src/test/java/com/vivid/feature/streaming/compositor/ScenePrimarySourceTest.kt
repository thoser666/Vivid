package com.vivid.feature.streaming.compositor

import com.vivid.core.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScenePrimarySourceTest {
    private val image = SceneSource("image", SceneSourceType.IMAGE, "content://image")
    private val graph = SceneComposition(sources = listOf(image), layers = listOf(SceneLayer("image-layer", image.id)))

    @Test fun `adding a missing primary preserves unrelated sources and places its layer beneath overlays`() {
        val result = replaceScenePrimarySource(graph, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, "content://movie")!!
        assertEquals(image, result.graph.sources.first())
        assertEquals(graph.layers.single(), result.graph.layers.last())
        assertEquals(result.sourceId, result.graph.layers.first().sourceId)
        assertEquals(SceneSourceType.VIDEO, result.graph.sources.last().type)
    }
    @Test fun `subsequent switches retain the primary ID and every layer transform`() {
        val movie = replaceScenePrimarySource(graph, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, "movie")!!
        val changed = replaceScenePrimarySource(movie.graph, movie.sourceId, SceneSourceType.VIDEO, SceneSourceType.SCREEN_CAPTURE, "")!!
        assertEquals(movie.sourceId, changed.sourceId)
        assertEquals(movie.graph.layers, changed.graph.layers)
        assertEquals(image, changed.graph.sources.first())
    }
    @Test fun `tracked primary wins over another source with the same type`() {
        val first = SceneSource("a", SceneSourceType.VIDEO, "a")
        val second = SceneSource("b", SceneSourceType.VIDEO, "b")
        val input = graph.copy(sources = listOf(first, second, image))
        val result = replaceScenePrimarySource(input, "b", SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear")!!
        assertEquals(first, result.graph.sources.first())
        assertEquals("b", result.sourceId)
        assertEquals(SceneSourceType.CAMERA, result.graph.sources[1].type)
    }
    @Test fun `full graphs reject an addition instead of discarding an existing layer`() {
        val full = graph.copy(layers = List(SceneComposition.MAX_LAYERS) { SceneLayer("layer-$it", image.id) })
        assertNull(replaceScenePrimarySource(full, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, "movie"))
        val sources = graph.copy(sources = List(SceneComposition.MAX_SOURCES) { image.copy(id = "image-$it") }, layers = emptyList())
        assertNull(replaceScenePrimarySource(sources, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, "movie"))
    }
}
