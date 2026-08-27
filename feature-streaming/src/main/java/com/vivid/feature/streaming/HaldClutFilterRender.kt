package com.vivid.feature.streaming

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OpenGL 3D-LUT-Filter der eine Hald-CLUT-Textur auf den Frame anwendet.
 *
 * Die Hald-CLUT (Hardware-Agnostic Lookup) kodiert eine 3D-Farbtabelle als 2D-Bild:
 * - Für N Einträge pro Achse ist das Bild N^2 breit und N hoch
 * - Innerhalb einer Zeile (Rot-Achse) kodiert die Spalte (Grün × N + Blau)
 * - Der GLSL-Shader liest die Input-Farbe, berechnet die LUT-Position und
 *   gibt die transformierte Farbe aus
 *
 * Die Filter wirken auf den **Encoder-Pfad** (Vorschau + gestreamtes Video),
 * da RootEncoder's `setFilter` die GL-Pipeline beider Pfade speist.
 * (Verifiziert an RootEncoder 2.7.5 per Wiki-Doku und Bytecode.)
 *
 * @param lutBitmap Das Hald-CLUT-Bild (PNG) oder null für Identitäts-LUT (Passthrough)
 * @param lutSize Anzahl der Einträge pro Achse (z.B. 16 für 16×16×16 = 4096 Einträge)
 * @param gamma Gamma-Korrektur (2.2 für sRGB, 1.0 für linear)
 */
class HaldClutFilterRender(
    private val lutBitmap: Bitmap? = null,
    private val lutSize: Int = 16,
    private val gamma: Float = 2.2f,
) : BaseFilterRender() {

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
    private var uLutSamplerHandle = -1
    private var uLutSizeHandle = -1
    private var uLutTexWidthHandle = -1
    private var uLutTexHeightHandle = -1
    private var uGammaHandle = -1

    private var lutTextureId = -1
    private var lutTexWidth = 0
    private var lutTexHeight = 0

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
        val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.hald_clut_fragment)
        program = GlUtil.createProgram(vertexShader, fragmentShader)

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
        uLutSamplerHandle = GLES20.glGetUniformLocation(program, "uLutSampler")
        uLutSizeHandle = GLES20.glGetUniformLocation(program, "uLutSize")
        uLutTexWidthHandle = GLES20.glGetUniformLocation(program, "uLutTexWidth")
        uLutTexHeightHandle = GLES20.glGetUniformLocation(program, "uLutTexHeight")
        uGammaHandle = GLES20.glGetUniformLocation(program, "uGamma")

        // LUT-Textur laden
        lutTextureId = createLutTexture(lutBitmap)
    }

    override fun drawFilter() {
        GLES20.glUseProgram(program)

        // Vertex-Buffer für das Quad
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

        // Matrizen
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)

        // Video-Textur (Eingabe)
        GLES20.glUniform1i(uSamplerHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)

        // LUT-Textur (3D-LUT als 2D)
        GLES20.glUniform1i(uLutSamplerHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)

        // LUT-Parameter
        GLES20.glUniform1f(uLutSizeHandle, lutSize.toFloat())
        GLES20.glUniform1f(uLutTexWidthHandle, lutTexWidth.toFloat())
        GLES20.glUniform1f(uLutTexHeightHandle, lutTexHeight.toFloat())
        GLES20.glUniform1f(uGammaHandle, gamma)
    }

    override fun disableResources() {
        GlUtil.disableResources(aTextureHandle, aPositionHandle)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
        if (lutTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = -1
        }
    }

    /**
     * Erzeugt die OpenGL-Textur aus dem Hald-CLUT-Bild.
     * Bei null-Bitmap wird eine Identitäts-LUT erzeugt (Passthrough).
     */
    private fun createLutTexture(bitmap: Bitmap?): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val bmp = bitmap ?: generateIdentityLut(lutSize)
        lutTexWidth = bmp.width
        lutTexHeight = bmp.height

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)

        if (bitmap == null) {
            bmp.recycle() // Identitäts-LUT nur temp
        }

        return texId
    }

    companion object {
        /**
         * Erzeugt eine Identitäts-LUT (Passthrough) als Bitmap.
         * Für N Einträge pro Achse: Bildgröße = N^2 × N Pixel.
         */
        fun generateIdentityLut(size: Int): Bitmap {
            val width = size * size
            val height = size
            val pixels = IntArray(width * height)

            for (r in 0 until size) {
                for (g in 0 until size) {
                    for (b in 0 until size) {
                        val x = g * size + b
                        val y = r
                        val index = y * width + x
                        // ARGB: A=255, R/G/B normalisiert auf 0-255
                        val rn = (r * 255.0f / (size - 1)).toInt().coerceIn(0, 255)
                        val gn = (g * 255.0f / (size - 1)).toInt().coerceIn(0, 255)
                        val bn = (b * 255.0f / (size - 1)).toInt().coerceIn(0, 255)
                        pixels[index] = (0xFF shl 24) or (rn shl 16) or (gn shl 8) or bn
                    }
                }
            }

            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        /**
         * Erzeugt eine Test-LUT mit warmer Farbton-Verschiebung (leichtes Orange/Amber).
         * Nützlich als visueller PoC-Nachweis, dass der LUT-Filter funktioniert.
         */
        fun generateWarmToneLut(size: Int): Bitmap {
            val width = size * size
            val height = size
            val pixels = IntArray(width * height)

            for (r in 0 until size) {
                for (g in 0 until size) {
                    for (b in 0 until size) {
                        val x = g * size + b
                        val y = r
                        val index = y * width + x

                        // Warm-Tone: Rot +10%, Grün +5%, Blau -15%
                        val rn = ((r * 255.0f / (size - 1)) * 1.10f).toInt().coerceIn(0, 255)
                        val gn = ((g * 255.0f / (size - 1)) * 1.05f).toInt().coerceIn(0, 255)
                        val bn = ((b * 255.0f / (size - 1)) * 0.85f).toInt().coerceIn(0, 255)
                        pixels[index] = (0xFF shl 24) or (rn shl 16) or (gn shl 8) or bn
                    }
                }
            }

            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        /**
         * Erzeugt eine Cool-Tone-LUT (leichtes Blau/Cyan-Verschiebung).
         */
        fun generateCoolToneLut(size: Int): Bitmap {
            val width = size * size
            val height = size
            val pixels = IntArray(width * height)

            for (r in 0 until size) {
                for (g in 0 until size) {
                    for (b in 0 until size) {
                        val x = g * size + b
                        val y = r
                        val index = y * width + x

                        // Cool-Tone: Rot -10%, Grün +5%, Blau +10%
                        val rn = ((r * 255.0f / (size - 1)) * 0.90f).toInt().coerceIn(0, 255)
                        val gn = ((g * 255.0f / (size - 1)) * 1.05f).toInt().coerceIn(0, 255)
                        val bn = ((b * 255.0f / (size - 1)) * 1.10f).toInt().coerceIn(0, 255)
                        pixels[index] = (0xFF shl 24) or (rn shl 16) or (gn shl 8) or bn
                    }
                }
            }

            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
