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
    private var uFullFrameHandle = -1
    private var uPixelGridHandle = -1
    private var uMaskTexHandle = -1
    private var uMaskEnabledHandle = -1

    /** Ellipsen-Snapshot (Textur-Koordinaten 0..1); Index >= count = deaktiviert. */
    @Volatile
    private var ellipses: List<PrivacyEllipse> = emptyList()

    /** Voll-Frame-Modus (P2b): true = das ganze Bild wird pixeliert. */
    @Volatile
    private var fullFrame = false

    /** Mosaik-Kantenlänge in Textur-Koordinaten (Framesync, wie die Ellipsen). */
    @Volatile
    private var pixelGrid = DEFAULT_PIXEL_GRID

    /** P4.1-Soll-Zustand des Umgebungs-Modus (reiner Uniform-Zustand). */
    @Volatile
    private var environmentEnabled = false

    /**
     * Der zuletzt gesetzte Masken-Snapshot. Der Upload passiert erst im
     * GL-Thread ([drawFilter]) — `glTexImage2D` von einem anderen Thread wäre
     * ein Rennen gegen RootEncoders Kontext (Skizze §11.3).
     */
    @Volatile
    private var pendingMask: EnvironmentMask? = null

    /**
     * Generationszähler: jede neue Maske erhöht ihn; der GL-Thread lädt genau
     * dann hoch, wenn er hinterherhinkt. Verhindert, dass dieselbe Maske in
     * jedem Frame erneut hochgeladen wird.
     */
    @Volatile
    private var maskGeneration = 0L

    private var uploadedMaskGeneration = 0L
    private var maskTextureId = 0
    private var maskUploaded = false

    /** Direkter Puffer für `glTexImage2D` (Android verlangt Direct Buffers). */
    private var maskBuffer: ByteBuffer? = null

    /** Test-Lesegröße des Voll-Frame-Snapshots. */
    internal val fullFrameSnapshot: Boolean
        get() = fullFrame

    /** Test-Lesegröße des Mosaik-Grid-Snapshots. */
    internal val pixelGridSnapshot: Float
        get() = pixelGrid

    /** Test-Lesegröße des Umgebungs-Soll-Zustands. */
    internal val environmentEnabledSnapshot: Boolean
        get() = environmentEnabled

    /**
     * Test-Lesegröße: true, sobald eine gültige Maske im GL-Kontext liegt.
     * Ohne GL (JVM) bleibt sie false — genau deshalb startet `uMaskEnabled`
     * mit 0 und der Fail-Safe greift.
     */
    internal val maskUploadedSnapshot: Boolean
        get() = maskUploaded


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

    /**
     * Setzt den Voll-Frame-Modus (P2b): `true` pixeliert das **gesamte**
     * Bild — unabhängig von den Ellipsen-Slots, damit Architektur und
     * Umgebung des Streamers nicht lesbar bleiben (Skizze §5, P2b).
     */
    fun setFullFrame(enabled: Boolean) {
        fullFrame = enabled
    }

    /**
     * Setzt die Mosaik-Kantenlänge in Textur-Koordinaten (framesynchron).
     * Zonen/Gesichter nutzen [DEFAULT_PIXEL_GRID], der Voll-Frame-Modus die
     * grobere Stufe [FULL_FRAME_PIXEL_GRID] (Komponente wählt das Preset).
     */
    fun setPixelGrid(grid: Float) {
        require(grid > 0f) { "PixelGrid muss positiv sein, got $grid" }
        pixelGrid = grid
    }

    /**
     * Schaltet den Umgebungs-Modus (P4.1) um: `true` verpixelt alles, was die
     * Maske **nicht** als Person markiert. Wirksam erst mit einer
     * hochgeladenen Maske; vorher erzwingt [environmentFullFrameFallback]
     * Ganzbild (Skizze §11.6).
     */
    fun setEnvironmentBlurEnabled(enabled: Boolean) {
        environmentEnabled = enabled
    }

    /**
     * Reicht eine neue Personen-Maske ein. Der Snapshot wird beim nächsten
     * [drawFilter] hochgeladen (GL-Thread); ein späterer Aufruf ersetzt ihn
     * vollständig.
     */
    fun setEnvironmentMask(mask: EnvironmentMask) {
        pendingMask = mask
        maskGeneration += 1
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
        uFullFrameHandle = GLES20.glGetUniformLocation(program, "uFullFrame")
        uPixelGridHandle = GLES20.glGetUniformLocation(program, "uPixelGrid")
        uMaskTexHandle = GLES20.glGetUniformLocation(program, "uMaskTex")
        uMaskEnabledHandle = GLES20.glGetUniformLocation(program, "uMaskEnabled")
        createMaskTexture()
    }

    /**
     * Legt die Masken-Textur als 1×1-Schwarz an. Schwarz heißt im Shader
     * „kein Pixel ist Person" — ohne gesetzte Maske wäre das ein Vollbild-
     * Mosaik. Genau deshalb bleibt `uMaskEnabled` 0, bis
     * [uploadPendingMask] einen echten Snapshot hochgeladen hat (§11.6).
     */
    private fun createMaskTexture() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        maskTextureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4).put(0, 0.toByte()),
        )
    }

    /**
     * Lädt einen neuen Masken-Snapshot in die Textur, wenn seit dem letzten
     * Upload einer eingereicht wurde. Muss im GL-Thread laufen — wird nur aus
     * [drawFilter] gerufen.
     */
    private fun uploadPendingMask() {
        val generation = maskGeneration
        if (generation == uploadedMaskGeneration) return
        val mask = pendingMask ?: return
        val expected = mask.width * mask.height * EnvironmentMask.RGBA_CHANNELS
        if (mask.rgba.size != expected) return // Konstruktor verhindert das; doppelt hält.
        val buffer = maskBuffer
            ?.takeIf { it.capacity() == expected }
            ?: ByteBuffer.allocateDirect(expected).also { maskBuffer = it }
        buffer.clear()
        buffer.put(mask.rgba)
        buffer.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mask.width, mask.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer,
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        uploadedMaskGeneration = generation
        maskUploaded = true
    }


    override fun drawFilter() {
        GLES20.glUseProgram(program)
        uploadPendingMask()
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

        // P4.1: Maske auf Einheit 1. `uMaskEnabled` ist erst 1, wenn eine
        // echte Maske hochgeladen ist — sonst wäre die schwarze 1×1-Textur
        // „alles Hintergrund" und würde das ganze Bild verpixeln (§11.6).
        // Der Fail-Safe schaltet in diesem Fenster auf Ganzbild.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glUniform1i(uMaskTexHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1f(uMaskEnabledHandle, if (environmentEnabled && maskUploaded) 1f else 0f)

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
        // Voll-Frame-Flag + Mosaik-Kantenlänge ebenfalls framesynchron
        // (Snapshot, kein Ketten-Rebuild — P2b). Der Umgebungs-Modus erzwingt
        // Ganzbild, solange keine gültige Maske im GL-Kontext liegt (§11.6).
        val effectiveFullFrame = environmentFullFrameFallback(fullFrame, environmentEnabled, maskUploaded)
        GLES20.glUniform1f(uFullFrameHandle, if (effectiveFullFrame) 1f else 0f)
        // Ein erzwungener Ganzbild-Fallback muss die **grobe** Stufe nutzen,
        // sonst bliebe die Umgebung trotz Vollbild fein genug lesbar.
        val grid = if (effectiveFullFrame) maxOf(pixelGrid, FULL_FRAME_PIXEL_GRID) else pixelGrid
        GLES20.glUniform1f(uPixelGridHandle, grid)
    }

    override fun disableResources() {
        GlUtil.disableResources(aTextureHandle, aPositionHandle)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
        if (maskTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(maskTextureId), 0)
            maskTextureId = 0
            maskUploaded = false
        }
    }

    companion object {
        /** Max. Ellipsen-Zonen (Skizze §3: 4 Zonen + 4 Gesichter). */
        const val MAX_ELLIPSES = 8

        /**
         * Mosaik-Kantenlänge für Zonen/Gesichter in Textur-Koordinaten
         * (1/48 ≈ 15 px bei 720p, ≈ 23 px bei 1080p) — Details weg,
         * Silhouetten bleiben als Prüfhinweis erkennbar.
         */
        const val DEFAULT_PIXEL_GRID = 1f / 48f

        /**
         * Grobe Mosaik-Kantenlänge für den Voll-Frame-Modus (1/16 ≈ 45 px
         * bei 720p, ≈ 120 px bei 1080p): Umgebung und Architektur sind dann
         * nicht mehr lesbar — der eigentliche Standort-Schutz (P2b).
         */
        const val FULL_FRAME_PIXEL_GRID = 1f / 16f
    }
}
