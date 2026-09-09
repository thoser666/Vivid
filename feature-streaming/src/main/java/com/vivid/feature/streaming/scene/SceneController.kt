package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wendet eine Szene (Moblin: Basic Scenes) an: stellt die komplette
 * Stream-Konfiguration her, die in der Szene gespeichert ist.
 *
 * - Primäres Stream-Ziel (URL/Key/TLS) → SettingsRepository
 * - Text-/Info-Widget-Zustand (an/aus, Felder, Template) → SettingsRepository
 * - Videoquelle (Kamera / Screen-Capture / Replay) → StreamingEngine (VideoSourceRegistry)
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
        applySceneSource(scene)
        sceneRepository.setActiveScene(scene.id)
    }

    /**
     * Aktiviert die Videoquelle der Szene. Replay-Szenen setzen zuerst die
     * gespeicherte Datei (Loop-Wiedergabe); fehlt die Datei (gelöscht/ungültig),
     * schlägt der Wechsel fehl und die aktive Quelle bleibt unverändert.
     */
    private fun applySceneSource(scene: StreamScene) {
        when (scene.videoSource) {
            SceneVideoSource.REPLAY -> {
                val path = scene.replayPath
                if (path != null) {
                    streamingEngine.useReplayAsSource(File(path))
                } else {
                    streamingEngine.switchSource(VideoSourceKind.REPLAY)
                }
            }
            else -> streamingEngine.switchSource(scene.videoSource.toVideoSourceKind())
        }
    }
}

/** Domain-Enum → Engine-Enum (das Domain-Modul kennt die Engine nicht). */
private fun SceneVideoSource.toVideoSourceKind(): VideoSourceKind = when (this) {
    SceneVideoSource.CAMERA -> VideoSourceKind.CAMERA
    SceneVideoSource.SCREEN_CAPTURE -> VideoSourceKind.SCREEN_CAPTURE
    SceneVideoSource.REPLAY -> VideoSourceKind.REPLAY
}