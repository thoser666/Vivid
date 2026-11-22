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
    @Test fun `subsequent switches retain view IDs and source identities without mutating producers`() {
        val movie = replaceScenePrimarySource(graph, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, SCENE_MOVIE)!!
        val changed = replaceScenePrimarySource(movie.graph, movie.sourceId, SceneSourceType.VIDEO, SceneSourceType.SCREEN_CAPTURE, "")!!
        assertNotEquals(movie.sourceId, changed.sourceId)
        assertEquals(movie.graph.layers.map { if (it.followsPrimaryInput) it.copy(sourceId = changed.sourceId) else it }, changed.graph.layers)
        assertEquals(movie.graph.sources.last(), changed.graph.sources.first { it.id == movie.sourceId })
        assertEquals(image, changed.graph.sources.first())
    }
    @Test fun `unreferenced saved sources are not repurposed by a main input change`() {
        val first = SceneSource("a", SceneSourceType.VIDEO, "a")
        val second = SceneSource("b", SceneSourceType.VIDEO, "b")
        val input = graph.copy(sources = listOf(first, second, image))
        val result = replaceScenePrimarySource(input, "b", SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear")!!
        assertEquals(first, result.graph.sources.first())
        assertEquals(second, result.graph.sources[1])
        assertNotEquals("b", result.sourceId)
        assertEquals(SceneSourceType.CAMERA, result.graph.sources.last().type)
    }
    @Test fun `full graphs reject an addition instead of discarding an existing layer`() {
        val full = graph.copy(layers = List(SceneComposition.MAX_LAYERS) { SceneLayer("layer-$it", image.id) })
        assertNull(replaceScenePrimarySource(full, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, SCENE_MOVIE))
        val sources = graph.copy(sources = List(SceneComposition.MAX_SOURCES) { image.copy(id = "image-$it") }, layers = emptyList())
        assertNull(replaceScenePrimarySource(sources, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO, SCENE_MOVIE))
    }
    @Test fun `switching to an existing camera and back preserves its independent layer and one producer`() {
        val movie = SceneSource(SCENE_MOVIE, SceneSourceType.VIDEO, "content://movie")
        val camera = SceneSource("rear", SceneSourceType.CAMERA, "rear-device")
        val cameraLayer = SceneLayer("rear-view", camera.id, SceneTransform(width = .3f, height = .3f))
        val input = SceneComposition(sources = listOf(movie, camera),
            layers = listOf(SceneLayer("main", movie.id), cameraLayer))
        val switched = replaceScenePrimarySource(input, movie.id, SceneSourceType.VIDEO, SceneSourceType.CAMERA, camera.locator)!!
        assertEquals(1, switched.graph.sources.count { it.type == SceneSourceType.CAMERA && it.locator == camera.locator })
        assertEquals(camera.id, switched.sourceId)
        val restored = replaceScenePrimarySource(switched.graph, switched.sourceId, SceneSourceType.CAMERA, SceneSourceType.VIDEO, movie.locator)!!
        assertEquals(cameraLayer, restored.graph.layers.single { it.id == cameraLayer.id })
        assertEquals(movie.id, restored.sourceId)
        assertEquals(input.sources, restored.graph.sources)
    }

    @Test fun `main input duplicates keep their binding while independent views of a reused source remain independent`() {
        val movie = SceneSource(SCENE_MOVIE, SceneSourceType.VIDEO, SCENE_MOVIE)
        val camera = SceneSource("camera", SceneSourceType.CAMERA, "rear")
        val input = SceneComposition(sources = listOf(movie, camera), primaryInputConfigured = true,
            layers = listOf(SceneLayer("main", movie.id, followsPrimaryInput = true), SceneLayer("camera-view", camera.id)))
            .duplicateLayer("main", "main-copy")
        val switched = replaceScenePrimarySource(input, movie.id, SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear")!!
        val restored = replaceScenePrimarySource(switched.graph, camera.id, SceneSourceType.CAMERA, SceneSourceType.VIDEO, SCENE_MOVIE)!!
        assertEquals(input, restored.graph)
    }
    @Test fun `removing main views does not adopt an independent camera view on the next switch`() {
        val camera = SceneSource("camera", SceneSourceType.CAMERA, "rear")
        val independent = SceneLayer("independent", camera.id)
        val input = SceneComposition(sources = listOf(camera), layers = listOf(independent), primaryInputConfigured = true)
        val result = replaceScenePrimarySource(input, null, SceneSourceType.CAMERA, SceneSourceType.CAMERA, "rear")!!
        assertEquals(listOf(camera), result.graph.sources)
        assertEquals(independent, result.graph.layers.last())
        assertTrue(result.graph.layers.first().followsPrimaryInput)
    }
    @Test fun `a full source pool reuses an existing producer and only evicts an unused replaced input when necessary`() {
        val main = SceneSource("main", SceneSourceType.VIDEO, SCENE_MOVIE)
        val sources = listOf(main) + List(SceneComposition.MAX_SOURCES - 1) { image.copy(id = "image-$it") }
        val input = SceneComposition(sources = sources, primaryInputConfigured = true,
            layers = listOf(SceneLayer("main-view", main.id, followsPrimaryInput = true)))
        val changed = replaceScenePrimarySource(input, main.id, SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear")!!
        assertEquals(SceneComposition.MAX_SOURCES, changed.graph.sources.size)
        assertFalse(changed.graph.sources.any { it.id == main.id })
        val shared = input.copy(layers = input.layers + SceneLayer("independent", main.id))
        assertNull(replaceScenePrimarySource(shared, main.id, SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear"))
    }
    @Test fun `HAL camera aliases and resolved default cameras reuse the same physical producer`() {
        val camera = SceneSource("external", SceneSourceType.UVC, "hal-device")
        val input = graph.copy(sources = graph.sources + camera)
        val reused = replaceScenePrimarySource(input, null, SceneSourceType.VIDEO, SceneSourceType.CAMERA, "hal-device")!!
        assertEquals(camera.id, reused.sourceId)
        assertEquals(input.sources, reused.graph.sources)
        val default = camera.copy(id = "default", type = SceneSourceType.CAMERA, locator = "")
        val resolved = replaceScenePrimarySource(graph.copy(sources = listOf(image, default)), null,
            SceneSourceType.VIDEO, SceneSourceType.CAMERA, "rear", cameraLocator = { "rear" })!!
        assertEquals(default.id, resolved.sourceId)
        assertEquals(default, resolved.graph.sources.last())
    }
    @Test fun `lens controls reuse another camera without rebinding the main movie views`() {
        val movie = SceneSource(SCENE_MOVIE, SceneSourceType.VIDEO, SCENE_MOVIE)
        val rear = SceneSource("rear", SceneSourceType.CAMERA, "rear")
        val front = SceneSource(SCENE_FRONT, SceneSourceType.CAMERA, SCENE_FRONT)
        val input = SceneComposition(sources = listOf(movie, rear, front), primaryInputConfigured = true,
            layers = listOf(SceneLayer("main", movie.id, followsPrimaryInput = true),
                SceneLayer("rear-view", rear.id), SceneLayer("front-view", front.id)))
        val changed = rebindSceneSourceLayers(input, rear.id, SceneSourceType.CAMERA, SCENE_FRONT)!!
        assertEquals(input.sources, changed.graph.sources)
        assertEquals(input.layers.first(), changed.graph.layers.first())
        assertEquals(listOf(movie.id, front.id, front.id), changed.graph.layers.map { it.sourceId })
        assertEquals(input.layers.last(), changed.graph.layers.last())
    }

    @Test fun `the same default camera remains one source when its actual device ID becomes available`() {
        val camera = SceneSource("default", SceneSourceType.CAMERA)
        val input = SceneComposition(sources = listOf(camera), primaryInputConfigured = true,
            layers = listOf(SceneLayer("main", camera.id, followsPrimaryInput = true)))
        val result = replaceScenePrimarySource(input, camera.id, SceneSourceType.CAMERA, SceneSourceType.CAMERA, "",
            cameraLocator = { "resolved-rear" })!!
        assertEquals(input, result.graph)
        assertEquals(camera.id, result.sourceId)
    }

    @Test fun `one document can have distinct once and looping producers and reuse the requested mode`() {
        val loop = SceneSource("loop", SceneSourceType.VIDEO, "content://clip", videoLoop = true)
        val view = SceneLayer("loop-view", loop.id)
        val input = graph.copy(sources = graph.sources + loop, layers = graph.layers + view)
        val once = replaceScenePrimarySource(input, null, SceneSourceType.CAMERA, SceneSourceType.VIDEO,
            loop.locator, videoLoop = false)!!
        assertNotEquals(loop.id, once.sourceId)
        assertEquals(false, once.graph.sources.single { it.id == once.sourceId }.videoLoop)
        assertEquals(view, once.graph.layers.last())
        val reused = replaceScenePrimarySource(once.graph, once.sourceId, SceneSourceType.VIDEO, SceneSourceType.VIDEO,
            loop.locator, videoLoop = true)!!
        assertEquals(loop.id, reused.sourceId)
        assertEquals(once.graph.sources, reused.graph.sources)
        assertEquals(view, reused.graph.layers.last())
    }

}

private const val SCENE_FRONT = "front"
private const val SCENE_MOVIE = "movie"
