package com.vivid.feature.streaming

import android.content.Context
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.RenderHandler
import com.pedro.encoder.input.gl.render.RenderMode
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender

/**
 * RootEncoder 2.7.5 removes an add/remove slot without deleting its framebuffer.
 * Release owned slots on the GL thread; repeated scene/effect changes otherwise
 * retain full-resolution textures until the entire GL context is destroyed.
 * These slots use add/remove, never setFilter (which transfers its framebuffer).
 */
internal class OwnedFilterRender(
    private val delegate: BaseFilterRender,
    private val releaseFramebuffer: (RenderHandler) -> Unit = ::releaseFilterFramebuffer,
) : BaseFilterRender() {
    private var released = false
    override fun initGl(width: Int, height: Int, context: Context, previewWidth: Int, previewHeight: Int) {
        released = false
        delegate.initGl(width, height, context, previewWidth, previewHeight)
    }
    override fun initFBOLink() = delegate.initFBOLink()
    override fun draw() = delegate.draw()
    override fun setPreviousTexId(id: Int) = delegate.setPreviousTexId(id)
    override fun getPreviousTexId(): Int = delegate.previousTexId
    override fun getTexId(): Int = delegate.texId
    override fun getRenderHandler(): RenderHandler = delegate.renderHandler
    override fun setRenderHandler(handler: RenderHandler) = delegate.setRenderHandler(handler)
    override fun setPreviewSize(width: Int, height: Int) = delegate.setPreviewSize(width, height)
    override fun setWidth(width: Int) = delegate.setWidth(width)
    override fun getRenderMode(): RenderMode = delegate.renderMode
    override fun setRenderMode(mode: RenderMode) = delegate.setRenderMode(mode)
    override fun initGlFilter(context: Context) = error("Owned filter delegates initGl")
    override fun drawFilter() = error("Owned filter delegates draw")
    override fun disableResources() = Unit
    override fun release() {
        if (released) return
        released = true
        try { delegate.release() } finally { releaseFramebuffer(delegate.renderHandler) }
    }
}

internal fun releaseFilterFramebuffer(handler: RenderHandler) {
    GLES20.glDeleteFramebuffers(handler.fboId.size, handler.fboId, 0)
    GLES20.glDeleteRenderbuffers(handler.rboId.size, handler.rboId, 0)
    GLES20.glDeleteTextures(handler.texId.size, handler.texId, 0)
    handler.fboId.fill(0)
    handler.rboId.fill(0)
    handler.texId.fill(0)
}
