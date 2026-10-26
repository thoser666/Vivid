package com.vivid.feature.streaming.compositor.gl

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * One stable RootEncoder slot. Child changes commit on the GL thread between whole frames.
 * The last child draws directly into our stable output FBO: no additional blit or pixel copy.
 */
class SceneFilterChainRender(private val retired: () -> Unit = {}) : BaseFilterRender() {
    private data class Slot(val render: BaseFilterRender, var hasFramebuffer: Boolean = false)
    private val desired = AtomicReference<List<BaseFilterRender>>(emptyList())
    private val slots = IdentityHashMap<BaseFilterRender, Slot>()
    private var context: Context? = null
    private var attachedContext: EGLContext? = null
    private var passthrough: NoFilterRender? = null
    @Volatile var isReleased = false
        private set

    /** Latest state wins; discarded factories have not allocated GL resources. */
    fun setChain(filters: List<BaseFilterRender>) {
        require(filters.indices.all { index -> filters.take(index).none { it === filters[index] } })
        desired.set(filters.toList())
    }

    internal fun requestedChain(): List<BaseFilterRender> = desired.get()

    @Synchronized
    override fun initGlFilter(context: Context) {
        this.context = context
        attachedContext = EGL14.eglGetCurrentContext()
        isReleased = false
    }

    @Synchronized
    override fun draw() {
        val app = context ?: return
        val requested = desired.get()
        val chain = requested.ifEmpty {
            listOf(passthrough ?: NoFilterRender().also { passthrough = it })
        }
        val removed = slots.keys.filter { old -> chain.none { it === old } }
        removed.forEach { old -> slots.remove(old)?.let(::releaseSlot) }
        var input = previousTexId
        chain.forEachIndexed { index, filter ->
            val slot = slots[filter] ?: Slot(filter).also {
                filter.initGl(getWidth(), getHeight(), app, getPreviewWidth(), getPreviewHeight())
                slots[filter] = it
            }
            filter.setPreviewSize(getPreviewWidth(), getPreviewHeight())
            filter.setPreviousTexId(input)
            if (index == chain.lastIndex) {
                // RootEncoder reads this output texture before draw(), so its identity must
                // remain stable even when the final creative filter changes this frame.
                val own = filter.getRenderHandler()
                filter.setRenderHandler(getRenderHandler())
                try { filter.draw() } finally { filter.setRenderHandler(own) }
            } else {
                if (!slot.hasFramebuffer) { filter.initFBOLink(); slot.hasFramebuffer = true }
                filter.draw()
                input = filter.texId
            }
        }
    }

    private fun releaseSlot(slot: Slot) {
        try { slot.render.release() }
        finally {
            if (slot.hasFramebuffer && attachedContext != null && attachedContext == EGL14.eglGetCurrentContext()) {
                val buffers = slot.render.getRenderHandler()
                GLES20.glDeleteFramebuffers(1, buffers.fboId, 0)
                GLES20.glDeleteRenderbuffers(1, buffers.rboId, 0)
                GLES20.glDeleteTextures(1, buffers.texId, 0)
            }
        }
    }

    @Synchronized
    override fun release() {
        isReleased = true
        var failure: Exception? = null
        fun cleanup(action: () -> Unit) {
            try { action() }
            catch (error: Exception) {
                val previous = failure
                if (previous == null) failure = error
                else if (previous !== error) previous.addSuppressed(error)
            }
        }
        cleanup(retired)
        slots.values.toList().forEach { slot -> cleanup { releaseSlot(slot) } }
        slots.clear()
        desired.set(emptyList())
        passthrough = null
        context = null
        attachedContext = null
        // The inherited output handler belongs to RootEncoder (SET may reuse it).
        failure?.let { throw it }
    }

    override fun drawFilter() = Unit
    override fun disableResources() = Unit
}
