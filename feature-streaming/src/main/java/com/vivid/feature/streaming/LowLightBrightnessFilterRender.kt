package com.vivid.feature.streaming

import android.content.Context
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OpenGL-Brightness-Filter für den Low-Light-Boost: multipliziert die RGB-Kanäle
 * mit einem festen Faktor (1.5x), um das Bild in schlechten Lichtverhältnissen
 * aufzuhellen. Wirkt auf Vorschau + Encoder (gestreamtes Video).
 *
 * Verwendet eigene GLSL-Shader (brightness_fragment.fsh + simple_vertex.vsh)
 * statt der RootEncoder-Mitgelieferten, da kein eingebauter Brightness-Filter
 * existiert.
 */
class LowLightBrightnessFilterRender : BaseFilterRender() {

    private val squareVertexData = floatArrayOf(
        // X,    Y,  Z,  U,  V
        -1f, -1f, 0f, 0f, 0f, // bottom left
         1f, -1f, 0f, 1f, 0f, // bottom right
        -1f,  1f, 0f, 0f, 1f, // top left
         1f,  1f, 0f, 1f, 1f, // top right
    )

    private var program = -1
    private var aPositionHandle = -1
    private var aTextureHandle = -1
    private var uMVPMatrixHandle = -1
    private var uSTMatrixHandle = -1
    private var uSamplerHandle = -1

    init {
        squareVertex = ByteBuffer
            .allocateDirect(squareVertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { it.put(squareVertexData).position(0) }

        android.opengl.Matrix.setIdentityM(MVPMatrix, 0)
        android.opengl.Matrix.setIdentityM(STMatrix, 0)
    }

    override fun initGlFilter(context: Context) {
        val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
        val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.brightness_fragment)
        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
    }

    override fun drawFilter() {
        GLES20.glUseProgram(program)
        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex,
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex,
        )
        GLES20.glEnableVertexAttribArray(aTextureHandle)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)
        GLES20.glUniform1i(uSamplerHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
    }

    override fun disableResources() {
        GlUtil.disableResources(aTextureHandle, aPositionHandle)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
    }
}
