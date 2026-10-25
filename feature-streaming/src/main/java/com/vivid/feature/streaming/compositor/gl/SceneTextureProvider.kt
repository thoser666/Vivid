package com.vivid.feature.streaming.compositor.gl

import android.content.Context
import com.vivid.feature.streaming.compositor.SceneFramePlan

/** A borrowed GPU texture valid until the next provider update. No pixel buffers cross this API. */
data class SceneTextureFrame(
    val textureId: Int,
    val external: Boolean = false,
    /** Fail-safe protection for additional live sources while global privacy is enabled. */
    val fullFramePrivacy: Boolean = false,
    /** Maps canonical top-left-origin source UVs to this texture's sampling coordinates. */
    val transform: FloatArray = identityTextureMatrix(),
) {
    init {
        require(textureId > 0)
        require(transform.size == 16 && transform.all { it.isFinite() })
    }
}

fun identityTextureMatrix() = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f,
)

/** GL attachment, frame updates and lookups run on RootEncoder's rendering thread. */
interface SceneTextureProvider {
    fun attach(context: Context, width: Int, height: Int)
    fun update(plan: SceneFramePlan, nowMillis: Long)
    fun texture(sourceId: String): SceneTextureFrame?
    /** RootEncoder can release after destroying its EGL context; release capture even then. */
    fun detach()
}
