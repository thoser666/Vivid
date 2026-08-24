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
    SCREEN_CAPTURE,
}

/**
 * Eine umschaltbare „Szene“ (Moblin: Basic Scenes).
 *
 * Speichert die komplette Stream-Konfiguration, die beim Anwenden der Szene
 * hergestellt wird: Videoquelle, Text-/Info-Widget-Zustand und das primäre
 * Stream-Ziel (URL/Key/TLS). Das sekundäre Ziel wird bewusst nicht pro Szene
 * gespeichert — Multi-Streaming bleibt eine globale Einstellung.
 *
 * Serialisierbar (kotlinx.serialization), damit [com.vivid.core.data.SceneRepository]
 * die Szenen als JSON in der Preferences-DataStore ablegen kann.
 */
@Serializable
data class StreamScene(
    val id: String,
    val name: String,
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
)