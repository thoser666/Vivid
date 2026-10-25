package com.vivid.feature.streaming.compositor.capture

import android.graphics.SurfaceTexture
import android.view.Surface
import com.vivid.core.data.SceneSource
import com.vivid.feature.streaming.compositor.SceneProducer

/** One compositor-owned Surface per independently active capture source. */
data class SceneCaptureTarget(
    val texture: SurfaceTexture,
    val surface: Surface,
    val width: Int,
    val height: Int,
)

interface SceneCaptureBackend {
    /** Borrowed controls for the currently open source; never a second capture session. */
    fun camera(sourceId: String): com.pedro.encoder.input.video.Camera2ApiManager? = null
    /** Includes the legacy camera, so opening another camera respects device concurrency. */
    fun configure(sources: List<SceneSource>)
    fun create(source: SceneSource, target: SceneCaptureTarget,
        failed: (permissionRequired: Boolean) -> Unit): SceneProducer
    fun release()
}
