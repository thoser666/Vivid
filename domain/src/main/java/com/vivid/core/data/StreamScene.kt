package com.vivid.core.data

import kotlinx.serialization.Serializable

/**
 * Videoquelle, die eine Szene beim Anwenden aktiviert.
 *
 * Bewusst als eigenes Enum (statt `VideoSourceKind` aus feature-streaming),
 * damit das Domain-Modul keine Abhängigkeit auf das Streaming-Modul bekommt —
 * die Zuordnung passiert im Streaming-Modul (SceneController).
 */
enum class SceneVideoSource {
    CAMERA,
    EMPTY,
    SCREEN_CAPTURE,
    /** Eine gespeicherte Replay-Aufnahme (MP4) als Quelle — wird in Dauerschleife gestreamt. */
    REPLAY,
}

/** A source and an ordered composition of shared widgets. Legacy snapshot fields
 * remain readable until SceneRepository migrates them to widget definitions. */
@Serializable
data class StreamScene(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val cameraId: String? = null,
    /** null preserves legacy overlays until migrated; empty means no widgets. */
    val widgets: List<SceneWidget>? = null,
    val videoSource: SceneVideoSource = SceneVideoSource.CAMERA,
    val widgetEnabled: Boolean = false,
    val widgetShowTime: Boolean = true,
    val widgetShowLocation: Boolean = true,
    val widgetShowSpeed: Boolean = true,
    val widgetShowAltitude: Boolean = false,
    val widgetTemplate: String = "",
    val streamUrl: String = "",
    val streamKey: String = "",
    val streamUseTls: Boolean = false,
    /** Absoluter Pfad der Replay-Datei (nur für [SceneVideoSource.REPLAY] relevant). */
    val replayPath: String? = null,
)