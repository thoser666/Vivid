package com.vivid.core.data

/**
 * Position des Chat-Overlays auf dem Screen.
 * Vier Ecken: oben-links, oben-rechts, unten-links, unten-rechts.
 */
enum class ChatOverlayPosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END;

    companion object {
        fun fromName(name: String?): ChatOverlayPosition =
            entries.firstOrNull { it.name == name } ?: TOP_END
    }
}
