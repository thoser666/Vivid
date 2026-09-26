package com.vivid.feature.streaming

import android.content.Context
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Anonymisierungs-Filter (P0 der Skizze
 * docs/architecture/privacy-anonymization.md): ein [BaseFilterRender], der bis
 * zu [MAX_ELLIPSES] Ellipsen-Zonen (Zentrum + Radii in Textur-Koordinaten
 * 0..1) per Mosaik-Pixelierung unkenntlich macht (Shader:
 * `res/raw/privacy_blur_fragment.fsh`).
 *
 * Der Render ist absichtlich **zustandslos gegenüber RootEncoder**: er wird
 * vom [PrivacyComposer] erzeugt und über die Uniform-Setter gesteuert —
 * [PrivacyComposer] hält die einzige Referenz und aktualisiert die Ellipsen
 * framesynchron ohne Rebuild (die Uniform-Arrays sind Render-Zustand, kein
 * Ketten-Zustand).
 *
 * Wie [LowLightBrightnessFilterRender] eine Vivid-eigene Implementierung auf
 * RootEncoder-Basis (custom GLSL statt mitgelieferter Filter).
 */
class PrivacyBlurFilterRender : BaseFilterRender() {

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
    private var uCenterHandle = -1
    private var uRadiiHandle = -1

    /** Ellipsen-Snapshot (Textur-Koordinaten 0..1); Index >= count = deaktiviert. */
    @Volatile
    private var ellipses: List<PrivacyEllipse> = emptyList()

    init {
        squareVertex = ByteBuffer
            .allocateDirect(squareVertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .also { it.put(squareVertexData).position(0) }

        android.opengl.Matrix.setIdentityM(MVPMatrix, 0)
        android.opengl.Matrix.setIdentityM(STMatrix, 0)
    }

    /** Ersetzt den Ellipsen-Snapshot (framesynchron, ohne Ketten-Rebuild). */
    fun setEllipses(ellipses: List<PrivacyEllipse>) {
        require(ellipses.size <= MAX_ELLIPSES) {
            "Maximal $MAX_ELLIPSES Ellipsen, got ${ellipses.size}"
        }
        this.ellipses = ellipses
    }

    override fun initGlFilter(context: Context) {
        val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
        val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.privacy_blur_fragment)
        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
        uCenterHandle = GLES20.glGetUniformLocation(program, "uEllipseCenter")
        uRadiiHandle = GLES20.glGetUniformLocation(program, "uEllipseRadii")
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

        // Ellipsen-Uniforms: kompakter Snapshot (leere Slots = Radius 0,
        // deaktiviert im Shader ohne Branch).
        val snapshot = ellipses
        val centers = FloatArray(MAX_ELLIPSES * 2)
        val radii = FloatArray(MAX_ELLIPSES * 2)
        snapshot.forEachIndexed { i, e ->
            centers[i * 2] = e.centerX
            centers[i * 2 + 1] = e.centerY
            radii[i * 2] = e.radiusX
            radii[i * 2 + 1] = e.radiusY
        }
        GLES20.glUniform2fv(uCenterHandle, MAX_ELLIPSES, centers, 0)
        GLES20.glUniform2fv(uRadiiHandle, MAX_ELLIPSES, radii, 0)
    }

    override fun disableResources() {
        GlUtil.disableResources(aTextureHandle, aPositionHandle)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
    }

    companion object {
        /** Max. Ellipsen-Zonen (Skizze §3: 4 Zonen + 4 Gesichter). */
        const val MAX_ELLIPSES = 8
    }
}
