package com.vivid.core.data

/** Saved independently of stream profiles; order is shared with the future zoom widget. */
data class CameraControlSettings(
    val zoomSpeed: Float = 5.0f,
    val rearZoomPresets: List<Float> = listOf(0.5f, 1f, 1.5f, 2f),
    val frontZoomPresets: List<Float> = listOf(0.5f, 1f),
    val stabilization: StabilizationMode = StabilizationMode.OFF,
    val mirrorFrontCamera: Boolean = false,
) {
    fun normalized() = copy(
        zoomSpeed = if (zoomSpeed.isFinite()) zoomSpeed.coerceIn(0.1f, 10f) else 5.0f,
        rearZoomPresets = validPresets(rearZoomPresets),
        frontZoomPresets = validPresets(frontZoomPresets),
    )

    companion object {
        fun validPresets(values: List<Float>): List<Float> =
            values.filter { it.isFinite() && it in 0.1f..100f }.distinct().take(20)
    }
}

enum class StabilizationMode {
    OFF, STANDARD, CINEMATIC;

    companion object {
        fun fromName(value: String?): StabilizationMode = entries.firstOrNull { it.name == value } ?: OFF
    }
}
