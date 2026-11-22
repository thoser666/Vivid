package com.vivid.feature.streaming.ui.scene

import androidx.compose.runtime.saveable.Saver
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.matchesSceneIdentity

/** Re-evaluate after permission: the default camera's concrete ID may only now be available. */
internal fun reusableSceneCameraSource(
    sources: List<SceneSource>,
    candidate: SceneSource,
    cameraLocator: (SceneSource) -> String,
): SceneSource = sources.firstOrNull {
    it.matchesSceneIdentity(candidate.type, candidate.locator, cameraLocator)
} ?: candidate

/** Persist only the pending descriptor, never a camera session or permission token. */
internal val sceneCameraSourceSaver = Saver<SceneSource?, List<String>>(
    save = { source -> source?.let { listOf(it.id, it.type.name, it.locator, it.name, it.videoLoop?.toString().orEmpty()) } },
    restore = { raw -> runCatching {
        require(raw.size == 5)
        SceneSource(raw[0], SceneSourceType.valueOf(raw[1]), raw[2], raw[3],
            videoLoop = raw[4].takeIf { it.isNotEmpty() }?.toBooleanStrict())
    }.getOrNull() },
)
