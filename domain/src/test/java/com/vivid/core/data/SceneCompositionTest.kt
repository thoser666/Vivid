package com.vivid.core.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SceneCompositionTest {
    private val camera = SceneSource("camera-1", SceneSourceType.CAMERA, "back")
    private val image = SceneSource("image-1", SceneSourceType.IMAGE, "content://logo")

    @Test fun `old presets migrate deterministically and retain settings`() {
        val raw = """{"id":"old","name":"Old","streamUrl":"rtmp://example/live","replayPath":"clip.mp4","videoSource":"REPLAY"}"""
        val scene = Json.decodeFromString<StreamScene>(raw)
        val graph = scene.effectiveComposition()
        assertEquals(graph, scene.effectiveComposition())
        assertEquals(SceneSourceType.VIDEO, graph.sources.single().type)
        assertEquals("clip.mp4", graph.sources.single().locator)
        assertEquals(SceneTransform(), graph.layers.single().transform)
        assertEquals(scene, Json.decodeFromString<StreamScene>(Json.encodeToString(scene)))
        assertEquals("rtmp://example/live", scene.streamUrl)
    }

    @Test fun `all transforms and source identities survive persistence`() {
        val graph = SceneLayoutPreset.PICTURE_IN_PICTURE.arrange(camera, image)
        val layer = graph.layers.last().copy(
            transform = SceneTransform(-0.1f, 0.2f, 0.3f, 0.4f, 45f, SceneCrop(0.1f, 0.2f, 0.9f, 0.8f)),
            opacity = 0.7f, visible = false,
        )
        val scene = StreamScene("scene", "Layered", composition = graph.copy(
            layers = listOf(graph.layers.first(), layer),
            transition = SceneTransition(SceneTransitionType.FADE, 500),
        ))
        assertEquals(scene, Json.decodeFromString<StreamScene>(Json.encodeToString(scene)))
        assertEquals(scene.composition, scene.effectiveComposition())
    }

    @Test fun `duplicate and reorder share producer and preserve other layers`() {
        val graph = SceneLayoutPreset.PICTURE_IN_PICTURE.arrange(camera, image)
        val duplicate = graph.duplicateLayer(graph.layers.first().id, "copy")
        assertEquals(2, duplicate.sources.size)
        assertEquals(listOf(camera.id, camera.id, image.id), duplicate.layers.map { it.sourceId })
        assertEquals(listOf(graph.layers.last().id, graph.layers.first().id, "copy"),
            duplicate.moveLayer(graph.layers.last().id, 0).layers.map { it.id })
        assertEquals(2, graph.layers.size)
    }

    @Test fun `invalid graphs cannot reach producer allocation`() {
        invalid { SceneComposition(sources = listOf(camera, camera)) }
        invalid { SceneComposition(layers = listOf(SceneLayer("missing", "unknown"))) }
        invalid { SceneComposition(version = 999) }
        invalid { SceneComposition(canvasWidth = 0) }
        invalid { SceneTransform(width = Float.NaN) }
        invalid { SceneTransform(x = Float.MAX_VALUE) }
        invalid { SceneTransform(width = Float.MAX_VALUE) }
        invalid { SceneTransform(rotationDegrees = Float.POSITIVE_INFINITY) }
        invalid { SceneCrop(left = 0.8f, right = 0.2f) }
        invalid { SceneLayer("x", camera.id, opacity = 1.1f) }
        invalid { SceneComposition(sources = listOf(camera), layers =
            List(SceneComposition.MAX_LAYERS + 1) { SceneLayer("$it", camera.id) }) }
    }

    @Test fun `presets have explicit ordering and source requirements`() {
        assertEquals(1, SceneLayoutPreset.FULLSCREEN.arrange(camera).layers.size)
        val split = SceneLayoutPreset.SIDE_BY_SIDE.arrange(camera, image)
        assertEquals(0.5f, split.layers.first().transform.width)
        assertEquals(0.5f, split.layers.last().transform.x)
        invalid { SceneLayoutPreset.PICTURE_IN_PICTURE.arrange(camera) }
        invalid { SceneLayoutPreset.SIDE_BY_SIDE.arrange(camera, camera) }
    }

    private fun invalid(block: () -> Unit) {
        try { block(); fail("Invalid state accepted") } catch (_: IllegalArgumentException) { }
    }
}
