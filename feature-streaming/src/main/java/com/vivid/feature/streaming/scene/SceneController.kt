package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wendet eine Szene (Moblin: Basic Scenes) an: stellt die komplette
 * Stream-Konfiguration her, die in der Szene gespeichert ist.
 *
 * - Primäres Stream-Ziel (URL/Key/TLS) → SettingsRepository
 * - Text-/Info-Widget-Zustand (an/aus, Felder, Template) → SettingsRepository
 * - Videoquelle (Kamera / Screen-Capture) → StreamingEngine (VideoSourceRegistry)
 * - Die Szene wird als aktiv markiert → SceneRepository
 *
 * Das sekundäre Stream-Ziel bleibt bewusst global (kein Szenen-Feld) —
 * Multi-Streaming ist eine Basis-Einstellung, keine Szenen-Eigenschaft.
 */
@Singleton
class SceneController @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
    private val streamingEngine: StreamingEngine,
) {
    suspend fun applyScene(scene: StreamScene) {
        settingsRepository.updateStreamSettings(
            url = scene.streamUrl,
            key = scene.streamKey,
            useTls = scene.streamUseTls,
        )
        settingsRepository.updateWidgetSettings(
            enabled = scene.widgetEnabled,
            showTime = scene.widgetShowTime,
            showLocation = scene.widgetShowLocation,
            showSpeed = scene.widgetShowSpeed,
            showAltitude = scene.widgetShowAltitude,
            template = scene.widgetTemplate,
        )
        streamingEngine.switchSource(scene.videoSource.toVideoSourceKind())
        sceneRepository.setActiveScene(scene.id)
    }
}

/** Domain-Enum → Engine-Enum (das Domain-Modul kennt die Engine nicht). */
private fun SceneVideoSource.toVideoSourceKind(): VideoSourceKind = when (this) {
    SceneVideoSource.CAMERA -> VideoSourceKind.CAMERA
    SceneVideoSource.SCREEN_CAPTURE -> VideoSourceKind.SCREEN_CAPTURE
}