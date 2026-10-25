package com.vivid.feature.streaming.scene

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import com.vivid.feature.streaming.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Permanent final pass: source blackout and premultiplied transparent scene composition. */
class SceneLayerFilterRender(
    private val hideSource: () -> Boolean,
    private val mirrorOutput: () -> Boolean,
    private val onReady: (SceneLayerFilterRender, Surface, Int, Int) -> Unit,
    private val onReleased: (SceneLayerFilterRender) -> Unit,
) : BaseFilterRender() {
    @Volatile var released = false
        private set
    // Fail closed until the persisted active scene has been loaded by the host.
    @Volatile var sceneEmptyFromLayout = true
    private var texture: SurfaceTexture? = null
    private var surface: Surface? = null
    private val textureId = IntArray(1)
    private var program = 0
    private var position = 0
    private var coordinates = 0
    private var mvp = 0
    private var transform = 0
    private var sampler = 0
    private var overlay = 0
    private var mirrored = 0
    private var hidden = 0
    private var ready = 0
    @Volatile private var frameAvailable = false
    private var hasFrame = false

    init {
        val vertices = floatArrayOf(-1f,-1f,0f,0f,0f, 1f,-1f,0f,1f,0f,
            -1f,1f,0f,0f,1f, 1f,1f,0f,1f,1f)
        squareVertex = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(vertices); position(0) }
        Matrix.setIdentityM(MVPMatrix, 0)
        Matrix.setIdentityM(STMatrix, 0)
    }

    override fun initGlFilter(context: Context) {
        released = false
        program = GlUtil.createProgram(GlUtil.getStringFromRaw(context, R.raw.simple_vertex),
            GlUtil.getStringFromRaw(context, R.raw.scene_layer_fragment))
        position = GLES20.glGetAttribLocation(program, "aPosition")
        coordinates = GLES20.glGetAttribLocation(program, "aTextureCoord")
        mvp = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        transform = GLES20.glGetUniformLocation(program, "uSTMatrix")
        sampler = GLES20.glGetUniformLocation(program, "uSampler")
        overlay = GLES20.glGetUniformLocation(program, "uScene")
        hidden = GLES20.glGetUniformLocation(program, "uHidden")
        mirrored = GLES20.glGetUniformLocation(program, "uMirrored")
        ready = GLES20.glGetUniformLocation(program, "uReady")
        GlUtil.createExternalTextures(1, textureId, 0)
        val input = SurfaceTexture(textureId[0]).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener { frameAvailable = true }
        }
        texture = input
        surface = Surface(input).also { onReady(this, it, width, height) }
    }

    override fun drawFilter() {
        if (frameAvailable) {
            frameAvailable = false
            texture?.updateTexImage()
            hasFrame = true
        }
        GLES20.glUseProgram(program)
        squareVertex.position(0)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 20, squareVertex)
        GLES20.glEnableVertexAttribArray(position)
        squareVertex.position(3)
        GLES20.glVertexAttribPointer(coordinates, 2, GLES20.GL_FLOAT, false, 20, squareVertex)
        GLES20.glEnableVertexAttribArray(coordinates)
        GLES20.glUniformMatrix4fv(mvp, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(transform, 1, false, STMatrix, 0)
        GLES20.glUniform1i(sampler, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        GLES20.glUniform1i(overlay, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId[0])
        GLES20.glUniform1f(mirrored, if (mirrorOutput()) 1f else 0f)
        GLES20.glUniform1f(hidden, if (hideSource() || sceneEmptyFromLayout) 1f else 0f)
        GLES20.glUniform1f(ready, if (hasFrame) 1f else 0f)
    }

    override fun disableResources() { GlUtil.disableResources(position, coordinates) }

    override fun release() = synchronized(this) {
        released = true
        onReleased(this)
        surface?.release()
        texture?.release()
        surface = null
        texture = null
        hasFrame = false
        frameAvailable = false
        GLES20.glDeleteTextures(1, textureId, 0)
        GLES20.glDeleteProgram(program)
        com.vivid.feature.streaming.releaseFilterFramebuffer(getRenderHandler())
    }
}
