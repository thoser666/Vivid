package com.vivid.core.data

import kotlinx.serialization.Serializable

/** Persisted descriptions contain no camera sessions, GL objects or permission tokens. */
@Serializable
enum class SceneSourceType { CAMERA, SCREEN_CAPTURE, UVC, IMAGE, VIDEO, WIDGET }

@Serializable
data class SceneSource(
    val id: String,
    val type: SceneSourceType,
    /** Camera ID, persisted document URI, USB device identity or widget identity. */
    val locator: String = "",
    val name: String = "",
    /** Null preserves old graph defaults; explicit file playback modes are part of producer identity. */
    val videoLoop: Boolean? = null,
) {
    init {
        require(id.isNotBlank()) { "Source ID must not be blank" }
    }
}

/** Coordinates are normalized against the uncropped input, with a top-left origin. */
@Serializable
data class SceneCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(left < right && top < bottom) { "Crop must have positive area" }
    }
}

/** Position is the unrotated top-left corner; rotation is clockwise about its center. */
@Serializable
data class SceneTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotationDegrees: Float = 0f,
    val crop: SceneCrop = SceneCrop(),
) {
    init {
        require(listOf(x, y, width, height, rotationDegrees).all { it.isFinite() })
        require(x in -MAX_EXTENT..MAX_EXTENT && y in -MAX_EXTENT..MAX_EXTENT)
        require(width > 0f && height > 0f && width <= MAX_EXTENT && height <= MAX_EXTENT) {
            "Layer dimensions must be positive and within the canvas extent budget"
        }
    }

    companion object { const val MAX_EXTENT = 16f }
}

@Serializable
data class SceneLayer(
    val id: String,
    val sourceId: String,
    val transform: SceneTransform = SceneTransform(),
    val visible: Boolean = true,
    val opacity: Float = 1f,
    /** This view follows the main-input selector; independent views keep their source. */
    val followsPrimaryInput: Boolean = false,
) {
    init {
        require(id.isNotBlank() && sourceId.isNotBlank())
        require(opacity.isFinite() && opacity in 0f..1f)
    }
}

@Serializable
enum class SceneTransitionType { CUT, FADE }

@Serializable
data class SceneTransition(
    val type: SceneTransitionType = SceneTransitionType.CUT,
    val durationMillis: Long = 300L,
) {
    init { require(durationMillis in 0L..MAX_DURATION_MILLIS) }

    companion object { const val MAX_DURATION_MILLIS = 2_000L }
}

/** Layers are drawn back-to-front. Multiple layers can share exactly one producer. */
@Serializable
data class SceneComposition(
    val version: Int = CURRENT_VERSION,
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val sources: List<SceneSource> = emptyList(),
    val layers: List<SceneLayer> = emptyList(),
    val transition: SceneTransition = SceneTransition(),
    /** Distinguishes old graphs from a graph whose main-input views were deliberately removed. */
    val primaryInputConfigured: Boolean = false,
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported composition version: $version" }
        require(canvasWidth in 1..MAX_CANVAS_SIZE && canvasHeight in 1..MAX_CANVAS_SIZE)
        require(sources.size <= MAX_SOURCES && layers.size <= MAX_LAYERS)
        require(sources.map { it.id }.distinct().size == sources.size) { "Duplicate source IDs" }
        require(layers.map { it.id }.distinct().size == layers.size) { "Duplicate layer IDs" }
        val sourceIds = sources.map { it.id }.toSet()
        require(layers.all { it.sourceId in sourceIds }) { "Layer references missing source" }
        require(layers.filter { it.followsPrimaryInput }.map { it.sourceId }.distinct().size <= 1) {
            "Main-input views must share one source"
        }
    }

    /** Duplication copies a view, never opens another camera or decoder. */
    fun duplicateLayer(layerId: String, newLayerId: String): SceneComposition {
        require(layers.none { it.id == newLayerId }) { "Duplicate layer ID" }
        val index = layers.indexOfFirst { it.id == layerId }
        require(index >= 0) { "Unknown layer ID" }
        val duplicate = layers[index].copy(id = newLayerId)
        return copy(layers = layers.toMutableList().apply { add(index + 1, duplicate) })
    }

    fun moveLayer(layerId: String, destination: Int): SceneComposition {
        require(destination in layers.indices)
        val index = layers.indexOfFirst { it.id == layerId }
        require(index >= 0)
        return copy(layers = layers.toMutableList().apply { add(destination, removeAt(index)) })
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val MAX_CANVAS_SIZE = 8192
        const val MAX_SOURCES = 8
        const val MAX_LAYERS = 16
    }
}

/** Deterministic migration leaves the original preset and stream settings intact. */
fun StreamScene.effectiveComposition(): SceneComposition {
    composition?.let { return it }
    val sourceId = "legacy-source:$id"
    val type = when (videoSource) {
        SceneVideoSource.CAMERA -> SceneSourceType.CAMERA
        SceneVideoSource.SCREEN_CAPTURE -> SceneSourceType.SCREEN_CAPTURE
        SceneVideoSource.REPLAY -> SceneSourceType.VIDEO
    }
    return SceneComposition(
        sources = listOf(SceneSource(sourceId, type, replayPath.orEmpty(), name,
            videoLoop = if (type == SceneSourceType.VIDEO) true else null)),
        layers = listOf(SceneLayer("legacy-layer:$id", sourceId, followsPrimaryInput = true)),
        primaryInputConfigured = true,
    )
}

/** Layouts work with both still and live sources, without owning their lifecycle. */
enum class SceneLayoutPreset {
    FULLSCREEN, PICTURE_IN_PICTURE, SIDE_BY_SIDE;

    fun arrange(primary: SceneSource, secondary: SceneSource? = null): SceneComposition {
        require(this == FULLSCREEN || secondary != null) { "Layout needs a second source" }
        require(secondary == null || secondary.id != primary.id) { "Use layer duplication to reuse a source" }
        val first = SceneLayer("primary:${primary.id}", primary.id, followsPrimaryInput = true)
        return when (this) {
            FULLSCREEN -> SceneComposition(sources = listOf(primary), layers = listOf(first), primaryInputConfigured = true)
            PICTURE_IN_PICTURE -> SceneComposition(
                primaryInputConfigured = true,
                sources = listOf(primary, requireNotNull(secondary)),
                layers = listOf(first, SceneLayer(
                    "secondary:${secondary.id}", secondary.id,
                    SceneTransform(x = 0.67f, y = 0.67f, width = 0.3f, height = 0.3f),
                )),
            )
            SIDE_BY_SIDE -> SceneComposition(
                primaryInputConfigured = true,
                sources = listOf(primary, requireNotNull(secondary)),
                layers = listOf(
                    first.copy(transform = SceneTransform(width = 0.5f)),
                    SceneLayer("secondary:${secondary.id}", secondary.id,
                        SceneTransform(x = 0.5f, width = 0.5f)),
                ),
            )
        }
    }
}
