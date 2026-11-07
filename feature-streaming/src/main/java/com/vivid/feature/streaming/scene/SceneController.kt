package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a source and activates its widget composition. Managed scenes leave the
 * selected stream destination unchanged; unmigrated snapshots retain legacy behavior.
 */
@Singleton
class SceneController @Inject constructor(
    private val sceneRepository: SceneRepository,
    private val settingsRepository: SettingsRepository,
    private val streamingEngine: StreamingEngine,
) {
    private val activation = Mutex()

    suspend fun applyScene(scene: StreamScene) = activation.withLock {
        if (!scene.enabled) return@withLock
        val wasEmpty = streamingEngine.isSceneEmpty()
        // Close the source gate before any camera/source/effect transition.
        streamingEngine.setSceneEmpty(true)
        if (!applySceneSource(scene)) {
            streamingEngine.setSceneEmpty(wasEmpty)
            return@withLock
        }
        if (scene.widgets == null) {
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
        }
        streamingEngine.setSceneComposition(scene.composition)
        sceneRepository.setActiveScene(scene.id)
        streamingEngine.setSceneEmpty(scene.composition == null && scene.videoSource == SceneVideoSource.EMPTY)
    }

    /**
     * Aktiviert die Videoquelle der Szene. Replay-Szenen setzen zuerst die
     * gespeicherte Datei (Loop-Wiedergabe); fehlt die Datei (gelöscht/ungültig),
     * schlägt der Wechsel fehl und die aktive Quelle bleibt unverändert.
     */
    private fun applySceneSource(scene: StreamScene): Boolean {
        val switched = when (scene.videoSource) {
            SceneVideoSource.EMPTY -> streamingEngine.switchSource(VideoSourceKind.CAMERA)
            SceneVideoSource.REPLAY -> scene.replayPath?.let { streamingEngine.useReplayAsSource(File(it)) }
                ?: streamingEngine.switchSource(VideoSourceKind.REPLAY)
            SceneVideoSource.CAMERA -> streamingEngine.switchSource(VideoSourceKind.CAMERA)
            SceneVideoSource.SCREEN_CAPTURE -> streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)
        }
        if (!switched) return false
        val cameraId = scene.cameraId
        if (scene.videoSource == SceneVideoSource.CAMERA && cameraId != null) {
            return streamingEngine.selectLens(cameraId)
        }
        return true
    }
}
