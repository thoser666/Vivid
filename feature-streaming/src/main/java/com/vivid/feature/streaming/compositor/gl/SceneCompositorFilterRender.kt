package com.vivid.feature.streaming.compositor.gl

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.SystemClock
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneLayer
import com.vivid.feature.streaming.compositor.SceneGeometry
import com.vivid.feature.streaming.compositor.SceneRenderState
import com.vivid.feature.streaming.compositor.SceneTransitionClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * GLES2 compositor inside RootEncoder's FBO chain. Preview and encoder consume
 * the same final texture. Producers supply textures, never additional encoders.
 * Fades blend whole offscreen scenes, preserving alpha and layer ordering.
 */
class SceneCompositorFilterRender(
    private val textures: SceneTextureProvider,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
    private val inputAvailable: () -> Boolean = { true },
) : BaseFilterRender() {
    private data class Submission(val state: SceneRenderState?, val animate: Boolean)
    private data class Program(val id: Int, val position: Int, val uv: Int,
        val transform: Int, val sampler: Int, val opacity: Int, val offline: Int, val privacy: Int)
    private data class DrawLayer(val layer: SceneLayer, val vertices: FloatBuffer)
    private data class Target(val framebuffer: Int, val texture: Int)

    private val desired = AtomicReference(Submission(null, false))
    private var applied: Submission? = null
    private val timeline = SceneTransitionClock()
    private val drawLists = mutableMapOf<SceneRenderState, List<DrawLayer>>()
    private var attachedContext: EGLContext? = null
    private var textureProgram: Program? = null
    private var externalProgram: Program? = null
    private var mixProgram = 0
    private var mixPosition = -1
    private var mixUv = -1
    private var mixFirst = -1
    private var mixSecond = -1
    private var mixProgress = -1
    private var outgoingTarget: Target? = null
    private var incomingTarget: Target? = null
    private val fullscreen = buffer(floatArrayOf(
        -1f, -1f, 0f, 0f, 0f, 1f, -1f, 0f, 1f, 0f,
        -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 1f, 1f,
    ))
    private val identity = identityTextureMatrix()
    private val inputTransform = identityTextureMatrix().apply { this[5] = -1f; this[13] = 1f }

    /** Latest desired state wins; editor gestures never queue GL work or capture frames. */
    fun setScene(state: SceneRenderState?, animate: Boolean = true) {
        desired.set(Submission(state, animate))
    }

    @Synchronized
    override fun initGlFilter(context: Context) {
        attachedContext = EGL14.eglGetCurrentContext()
        textureProgram = createProgram(external = false)
        if (GLES20.glGetString(GLES20.GL_EXTENSIONS)?.contains("GL_OES_EGL_image_external") == true) {
            externalProgram = createProgram(external = true)
        }
        mixProgram = GlUtil.createProgram(MIX_VERTEX, MIX_FRAGMENT)
        check(mixProgram != 0) { "Unable to compile scene transition program" }
        mixPosition = GLES20.glGetAttribLocation(mixProgram, "aPosition")
        mixUv = GLES20.glGetAttribLocation(mixProgram, "aTextureCoord")
        mixFirst = GLES20.glGetUniformLocation(mixProgram, "uFirst")
        mixSecond = GLES20.glGetUniformLocation(mixProgram, "uSecond")
        mixProgress = GLES20.glGetUniformLocation(mixProgram, "uProgress")
        applied = null
        timeline.clear()
        textures.attach(context.applicationContext, getWidth(), getHeight())
    }

    /** BaseFilterRender.draw assumes one quad; this pass draws the ordered scene instead. */
    @Synchronized
    override fun draw() {
        val program = textureProgram ?: return
        val now = clockMillis()
        val submission = desired.get()
        if (submission != applied) {
            if (submission.state == null) timeline.clear()
            else timeline.replace(submission.state, now, submission.animate)
            applied = submission
        }
        val plan = timeline.frame(now)
        val output = getRenderHandler().fboId[0]
        try {
            if (plan == null) {
                drawLists.clear()
                // An empty composition provider update releases all extra capture resources.
                textures.update(com.vivid.feature.streaming.compositor.SceneFramePlan(
                    SceneRenderState(SceneComposition())), now)
                bindTarget(output)
                GLES20.glDisable(GLES20.GL_BLEND)
                drawTexture(program, fullscreen, SceneTextureFrame(previousTexId), 1f)
            } else {
                textures.update(plan, now)
                val active = listOfNotNull(plan.outgoing, plan.current).toSet()
                drawLists.keys.retainAll(active)
                if (plan.outgoing == null) {
                    drawScene(plan.current, output)
                } else {
                    val old = outgoingTarget ?: createTarget().also { outgoingTarget = it }
                    val next = incomingTarget ?: createTarget().also { incomingTarget = it }
                    drawScene(plan.outgoing, old.framebuffer)
                    drawScene(plan.current, next.framebuffer)
                    blendScenes(output, old.texture, next.texture, plan.progress)
                }
            }
        } finally {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
        GlUtil.checkGlError("scene compositor")
    }

    private fun drawScene(state: SceneRenderState, framebuffer: Int) {
        bindTarget(framebuffer)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        fitViewport(state.composition)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val layers = drawLists.getOrPut(state) {
            state.composition.layers.map { layer ->
                DrawLayer(layer, buffer(SceneGeometry.vertices(layer.transform,
                    state.composition.canvasWidth, state.composition.canvasHeight)))
            }
        }
        for (draw in layers) {
            if (!draw.layer.visible || draw.layer.opacity <= 0f) continue
            val frame = if (draw.layer.sourceId == state.inputSourceId) {
                if (inputAvailable()) SceneTextureFrame(previousTexId, transform = inputTransform) else null
            } else textures.texture(draw.layer.sourceId)
            val program = if (frame?.external == true) externalProgram else textureProgram
            // Missing/unsupported/disconnected inputs render an opaque neutral placeholder.
            drawTexture(program ?: requireNotNull(textureProgram), draw.vertices,
                frame?.takeIf { !it.external || externalProgram != null }, draw.layer.opacity)
        }
    }

    private fun drawTexture(program: Program, vertices: FloatBuffer, frame: SceneTextureFrame?, opacity: Float) {
        GLES20.glUseProgram(program.id)
        attributes(vertices, program.position, program.uv)
        GLES20.glUniformMatrix4fv(program.transform, 1, false, frame?.transform ?: identity, 0)
        GLES20.glUniform1f(program.opacity, opacity)
        GLES20.glUniform1f(program.privacy, if (frame?.fullFramePrivacy == true) 1f else 0f)
        GLES20.glUniform1f(program.offline, if (frame == null) 1f else 0f)
        GLES20.glUniform1i(program.sampler, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (frame?.external == true) GLES11Ext.GL_TEXTURE_EXTERNAL_OES
            else GLES20.GL_TEXTURE_2D, frame?.textureId ?: 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(program.position)
        GLES20.glDisableVertexAttribArray(program.uv)
    }

    private fun blendScenes(framebuffer: Int, first: Int, second: Int, progress: Float) {
        bindTarget(framebuffer)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(mixProgram)
        attributes(fullscreen, mixPosition, mixUv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, first)
        GLES20.glUniform1i(mixFirst, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, second)
        GLES20.glUniform1i(mixSecond, 1)
        GLES20.glUniform1f(mixProgress, progress)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(mixPosition)
        GLES20.glDisableVertexAttribArray(mixUv)
    }

    private fun bindTarget(framebuffer: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, getWidth(), getHeight())
    }

    private fun fitViewport(scene: SceneComposition) {
        val scale = minOf(getWidth().toFloat() / scene.canvasWidth, getHeight().toFloat() / scene.canvasHeight)
        val w = (scene.canvasWidth * scale).roundToInt().coerceAtLeast(1)
        val h = (scene.canvasHeight * scale).roundToInt().coerceAtLeast(1)
        GLES20.glViewport((getWidth() - w) / 2, (getHeight() - h) / 2, w, h)
    }

    private fun createTarget(): Target {
        val ids = IntArray(2)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, getWidth(), getHeight(), 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenFramebuffers(1, ids, 1)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[1])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, ids[0], 0)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteFramebuffers(1, ids, 1)
            GLES20.glDeleteTextures(1, ids, 0)
            error("Unable to allocate scene transition framebuffer")
        }
        return Target(ids[1], ids[0])
    }

    private fun createProgram(external: Boolean): Program {
        val sampler = if (external) "samplerExternalOES" else "sampler2D"
        val extension = if (external) "#extension GL_OES_EGL_image_external : require\n" else ""
        val id = GlUtil.createProgram(VERTEX, extension + FRAGMENT.replace("SAMPLER_TYPE", sampler))
        check(id != 0) { "Unable to compile scene layer program" }
        return Program(id, GLES20.glGetAttribLocation(id, "aPosition"),
            GLES20.glGetAttribLocation(id, "aTextureCoord"), GLES20.glGetUniformLocation(id, "uTexMatrix"),
            GLES20.glGetUniformLocation(id, "uSampler"), GLES20.glGetUniformLocation(id, "uOpacity"),
            GLES20.glGetUniformLocation(id, "uOffline"), GLES20.glGetUniformLocation(id, "uPrivacy"))
    }

    private fun attributes(vertices: FloatBuffer, position: Int, uv: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 20, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(3)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, vertices)
        GLES20.glEnableVertexAttribArray(uv)
    }

    override fun drawFilter() = Unit
    override fun disableResources() = Unit

    @Synchronized
    override fun release() {
        textures.detach()
        if (attachedContext != null && EGL14.eglGetCurrentContext() == attachedContext) {
            textureProgram?.let { GLES20.glDeleteProgram(it.id) }
            externalProgram?.let { GLES20.glDeleteProgram(it.id) }
            if (mixProgram != 0) GLES20.glDeleteProgram(mixProgram)
            listOfNotNull(outgoingTarget, incomingTarget).forEach {
                GLES20.glDeleteFramebuffers(1, intArrayOf(it.framebuffer), 0)
                GLES20.glDeleteTextures(1, intArrayOf(it.texture), 0)
            }
        }
        // RootEncoder owns the inherited RenderHandler and may reuse it on SET.
        // If its EGL context has already been destroyed, GL objects died with it.
        textureProgram = null
        externalProgram = null
        mixProgram = 0
        outgoingTarget = null
        incomingTarget = null
        attachedContext = null
        applied = null
        drawLists.clear()
        timeline.clear()
    }

    companion object {
        private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

        private const val VERTEX = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
            }
        """
        private const val FRAGMENT = """
            precision mediump float;
            uniform SAMPLER_TYPE uSampler;
            uniform float uOpacity;
            uniform float uOffline;
            uniform float uPrivacy;
            varying vec2 vTextureCoord;
            void main() {
                if (uOffline > 0.5) {
                    float checker = mod(floor(vTextureCoord.x * 12.0) + floor(vTextureCoord.y * 12.0), 2.0);
                    gl_FragColor = vec4(vec3(0.12 + checker * 0.06), 1.0) * uOpacity;
                } else {
                    vec2 uv = uPrivacy > 0.5 ? (floor(vTextureCoord * 16.0) + 0.5) / 16.0 : vTextureCoord;
                    gl_FragColor = texture2D(uSampler, uv) * uOpacity;
                }
            }
        """
        private const val MIX_VERTEX = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() { gl_Position = aPosition; vTextureCoord = aTextureCoord; }
        """
        private const val MIX_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uFirst;
            uniform sampler2D uSecond;
            uniform float uProgress;
            varying vec2 vTextureCoord;
            void main() {
                gl_FragColor = mix(texture2D(uFirst, vTextureCoord), texture2D(uSecond, vTextureCoord), uProgress);
            }
        """
    }
}
