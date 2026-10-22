package com.vivid.core.data

/** Capture implementation; independent of the output encoder preset. */
enum class CameraMode {
    STANDARD,
    EXPERIMENTAL_60_FPS;

    companion object {
        fun fromName(name: String?): CameraMode = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
