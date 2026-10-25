package com.vivid.core.data

/** Preview-only UI preferences, independent of scenes and stream profiles. */
data class LocalOverlaySettings(
    val showZoom: Boolean = true,
    val showFps: Boolean = true,
    val showDuration: Boolean = true,
    val showScenes: Boolean = true,
    val showBitrate: Boolean = true,
)
