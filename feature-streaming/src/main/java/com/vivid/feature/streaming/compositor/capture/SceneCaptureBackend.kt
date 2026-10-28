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
    val frameRate: Int = 30,
) {
    init { require(frameRate in 1..120) }
}

interface SceneCaptureBackend {
    /** Output demand changes may update camera AE ranges without replacing their surfaces. */
    fun setTargetFrameRate(frameRate: Int) = Unit
    /** A usable texture proves that this camera's capture session is configured. */
    fun frameReady(sourceId: String) = Unit
    /** Available after the video has supplied its first usable GL frame. */
    fun videoPlayback(sourceId: String): SceneVideoPlayback? = null
    /** Event-driven files can hold one valid frame until an explicit playback error. */
    fun retainsLastFrame(sourceId: String): Boolean = false
    /** Remaining clockwise sampling correction; null waits for matching Camera2 metadata. */
    fun samplingRotationDegrees(sourceId: String, timestamp: Long): Int? = 0
    /** Borrowed controls for the currently open source; never a second capture session. */
    fun camera(sourceId: String): com.pedro.encoder.input.video.Camera2ApiManager? = null
    /** Includes the legacy camera, so opening another camera respects device concurrency. */
    fun configure(sources: List<SceneSource>)
    fun create(source: SceneSource, target: SceneCaptureTarget,
        failed: (permissionRequired: Boolean) -> Unit): SceneProducer
    fun release()
}
