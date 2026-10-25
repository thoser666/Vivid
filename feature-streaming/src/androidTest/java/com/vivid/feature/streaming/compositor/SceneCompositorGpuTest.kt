package com.vivid.feature.streaming.compositor

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.core.data.*
import com.vivid.feature.streaming.compositor.gl.*
import com.vivid.feature.streaming.compositor.capture.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** Real GLES2 pixel tests: JVM mocks cannot prove shader output or framebuffer orientation. */
@RunWith(AndroidJUnit4::class)
class SceneCompositorGpuTest {
    private lateinit var display: EGLDisplay
    private lateinit var context: EGLContext
    private lateinit var surface: EGLSurface
    private val owned = mutableListOf<Int>()
    private val frames = mutableMapOf<String, SceneTextureFrame>()
    private var now = 0L
    private var lastPlan: SceneFramePlan? = null
    private var renderer: SceneCompositorFilterRender? = null

    @Before fun createEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE,
        ), 0, configs, 0, 1, count, 0))
        assertTrue(count[0] > 0)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
    }

    @After fun destroyEgl() {
        renderer?.release()
        owned.forEach { GLES20.glDeleteTextures(1, intArrayOf(it), 0) }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (this::surface.isInitialized) EGL14.eglDestroySurface(display, surface)
        if (this::context.isInitialized) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    @Test fun orderedLayersBlendPremultipliedAlphaAndOpacity() {
        frames["red"] = texture(1, 1, intArrayOf(255, 0, 0, 255))
        frames["green"] = texture(1, 1, intArrayOf(0, 128, 0, 128))
        val scene = composition("red", "green").copy(layers = listOf(
            SceneLayer("background", "red"),
            SceneLayer("pip", "green", SceneTransform(0.5f, 0.5f, 0.5f, 0.5f), opacity = 0.5f),
        ))
        val render = start(scene)
        render.draw()
        assertColor(pixel(4, 4), 255, 0, 0)
        assertColor(pixel(24, 24), 191, 64, 0)
        render.setScene(SceneRenderState(scene.moveLayer("background", 1)), animate = false)
        render.draw()
        assertColor(pixel(24, 24), 255, 0, 0)
    }

    @Test fun cropAndRotationUseTheSameCoordinatesAsTheEditor() {
        frames["split"] = texture(2, 1, intArrayOf(255, 0, 0, 255, 0, 255, 0, 255))
        val cropped = composition("split").copy(layers = listOf(SceneLayer("crop", "split",
            SceneTransform(crop = SceneCrop(left = 0.5f)))))
        val render = start(cropped)
        render.draw()
        assertColor(pixel(2, 2), 0, 255, 0)
        assertColor(pixel(29, 29), 0, 255, 0)
        val rotated = cropped.copy(layers = listOf(SceneLayer("rotate", "split",
            SceneTransform(0.25f, 0.375f, 0.5f, 0.25f, 90f, SceneCrop(left = 0.5f)))))
        render.setScene(SceneRenderState(rotated), animate = false)
        render.draw()
        assertColor(pixel(16, 9), 0, 255, 0)
        assertColor(pixel(9, 16), 0, 0, 0)
    }

    @Test fun fadesBlendWholeScenesAndReleaseTheOutgoingPlan() {
        frames["red"] = texture(1, 1, intArrayOf(255, 0, 0, 255))
        frames["blue"] = texture(1, 1, intArrayOf(0, 0, 255, 255))
        val render = start(composition("red"))
        render.draw()
        now = 100
        render.setScene(SceneRenderState(composition("blue").copy(
            transition = SceneTransition(SceneTransitionType.FADE, 200))))
        render.draw()
        assertColor(pixel(16, 16), 255, 0, 0)
        now = 200
        render.draw()
        assertColor(pixel(16, 16), 128, 0, 128)
        now = 300
        render.draw()
        assertColor(pixel(16, 16), 0, 0, 255)
        assertNull(lastPlan!!.outgoing)
    }

    @Test fun missingSourceNeverReusesItsLastVisibleFrame() {
        frames["green"] = texture(1, 1, intArrayOf(0, 255, 0, 255))
        var rendered = 0
        val render = start(composition("green"), frameRendered = { rendered++ })
        render.draw()
        assertEquals(1, rendered)
        assertColor(pixel(16, 16), 0, 255, 0)
        frames.clear()
        render.draw()
        val offline = pixel(16, 16)
        assertEquals(offline[0], offline[1])
        assertEquals(offline[1], offline[2])
        assertTrue(offline[0] in 25..50)
        assertEquals(255, offline[3])
        assertEquals(2, rendered)
    }

    @Test fun cameraSamplingKeepsEmbeddedSensorRotationAndCropExactlyOnce() {
        val raw = texture(4, 2, intArrayOf(0, 0, 0, 255, 255, 0, 0, 255,
            0, 0, 255, 255, 255, 255, 255, 255, 0, 0, 0, 255, 0, 255, 0, 255,
            255, 255, 0, 255, 255, 255, 255, 255))
        val crop = identityTextureMatrix().apply { this[0] = .5f; this[12] = .25f }
        val surfaceTransform = FloatArray(16)
        android.opengl.Matrix.multiplyMM(surfaceTransform, 0, crop, 0,
            SceneCameraGeometry.samplingMatrix(270), 0) // Embedded sensor rotation and GL flip.
        val transform = FloatArray(16)
        android.opengl.Matrix.multiplyMM(transform, 0, surfaceTransform, 0,
            SceneCameraGeometry.samplingMatrix(0), 0)
        frames["camera"] = raw.copy(transform = transform)
        val render = start(composition("camera"))
        render.draw()
        assertColor(pixel(2, 2), 0, 255, 0)
        assertColor(pixel(29, 2), 255, 0, 0)
        assertColor(pixel(2, 29), 255, 255, 0)
        assertColor(pixel(29, 29), 0, 0, 255)
        android.opengl.Matrix.multiplyMM(transform, 0, surfaceTransform, 0,
            SceneCameraGeometry.samplingMatrix(SceneCameraGeometry.displayCorrection(90)), 0)
        render.draw()
        assertColor(pixel(2, 2), 255, 0, 0)
        assertColor(pixel(29, 2), 0, 0, 255)
        assertColor(pixel(2, 29), 0, 255, 0)
        assertColor(pixel(29, 29), 255, 255, 0)
    }

    @Test fun legacyInputRetainsFramebufferOrientationAndCanReturnToPassthrough() {
        val input = texture(1, 2, intArrayOf(255, 0, 0, 255, 0, 0, 255, 255))
        val scene = composition("camera")
        val render = start(scene, input.textureId, "camera")
        render.draw()
        assertColor(pixel(16, 2), 0, 0, 255)
        assertColor(pixel(16, 29), 255, 0, 0)
        render.setScene(null)
        render.draw()
        assertColor(pixel(16, 2), 0, 0, 255)
        assertColor(pixel(16, 29), 255, 0, 0)
        assertTrue(lastPlan!!.current.composition.layers.isEmpty())
    }

    @Test fun externalSurfaceIsSharedByDuplicateLayersAndDisconnectDropsItsFrame() {
        var opened = 0
        var stopped = 0
        var released = 0
        var disconnect: ((Boolean) -> Unit)? = null
        var states: Map<String, SceneProducerState> = emptyMap()
        val provider = AndroidSceneTextureProvider(SceneProjectionRegistry(), SceneBitmapRegistry(),
            publishStates = { states = it }, backendFactory = {
                object : SceneCaptureBackend {
                    override fun configure(sources: List<SceneSource>) = Unit
                    override fun create(source: SceneSource, target: SceneCaptureTarget,
                        failed: (Boolean) -> Unit): SceneProducer {
                        opened++
                        disconnect = failed
                        return object : SceneProducer {
                            override fun start() {
                                val canvas = target.surface.lockCanvas(null)
                                canvas.drawColor(android.graphics.Color.GREEN)
                                target.surface.unlockCanvasAndPost(canvas)
                            }
                            override fun stop() { stopped++ }
                            override fun release() { released++ }
                        }
                    }
                    override fun release() = Unit
                }
            })
        val render = SceneCompositorFilterRender(provider)
        renderer = render
        render.initGl(SIZE, SIZE, InstrumentationRegistry.getInstrumentation().targetContext, SIZE, SIZE)
        render.initFBOLink()
        render.setPreviousTexId(texture(1, 1, intArrayOf(0, 0, 0, 255)).textureId)
        val scene = SceneComposition(canvasWidth = SIZE, canvasHeight = SIZE,
            sources = listOf(SceneSource("capture", SceneSourceType.CAMERA)),
            layers = listOf(SceneLayer("first", "capture"), SceneLayer("copy", "capture")))
        render.setScene(SceneRenderState(scene), animate = false)
        val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
        do {
            render.draw()
            if (provider.texture("capture") != null) break
            Thread.sleep(10)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        assertNotNull("Surface frame did not arrive", provider.texture("capture"))
        assertColor(pixel(16, 16), 0, 255, 0)
        assertEquals(1, opened)
        assertEquals(2, states.getValue("capture").references)
        requireNotNull(disconnect).invoke(false)
        render.draw()
        assertNull(provider.texture("capture"))
        assertEquals(SceneProducerStatus.OFFLINE, states.getValue("capture").status)
        assertEquals(1, stopped)
        assertEquals(1, released)
    }

    @Test fun staticScreenKeepsItsFrameUntilTheGrantIsRevoked() {
        val capture = captureFixture(SceneSourceType.SCREEN_CAPTURE)
        awaitCapture(capture.provider)
        now = 60_000
        requireNotNull(renderer).draw()
        assertNotNull(capture.provider.texture("capture"))
        assertColor(pixel(16, 16), 0, 255, 0)
        assertEquals(1, capture.opened)
        assertEquals(SceneProducerStatus.ACTIVE, capture.states.getValue("capture").status)
        requireNotNull(capture.disconnect).invoke(true)
        requireNotNull(renderer).draw()
        assertNull(capture.provider.texture("capture"))
        assertEquals(SceneProducerStatus.PERMISSION_REQUIRED, capture.states.getValue("capture").status)
        assertEquals(1, capture.stopped)
    }

    @Test fun silentCameraDropsItsLastFrameAndBecomesOffline() {
        val capture = captureFixture(SceneSourceType.CAMERA)
        awaitCapture(capture.provider)
        now = 6_000
        requireNotNull(renderer).draw()
        assertNull(capture.provider.texture("capture"))
        assertEquals(SceneProducerStatus.OFFLINE, capture.states.getValue("capture").status)
        val offline = pixel(16, 16)
        assertEquals(offline[0], offline[1])
        assertEquals(offline[1], offline[2])
        assertEquals(1, capture.stopped)
    }

    @Test fun screenWithoutAnInitialFrameStillTimesOut() {
        val capture = captureFixture(SceneSourceType.SCREEN_CAPTURE, postFrame = false)
        requireNotNull(renderer).draw()
        now = 6_000
        requireNotNull(renderer).draw()
        assertNull(capture.provider.texture("capture"))
        assertEquals(SceneProducerStatus.OFFLINE, capture.states.getValue("capture").status)
        assertEquals(1, capture.stopped)
    }

    @Test fun lateCameraMetadataMakesTheExistingTextureUsableWithoutRestartingCapture() {
        val capture = captureFixture(SceneSourceType.CAMERA)
        capture.rotation = null
        awaitSampling(capture, previousTimestamp = null)
        assertNull(capture.provider.texture("capture"))
        capture.rotation = 90
        requireNotNull(renderer).draw()
        assertNotNull(capture.provider.texture("capture"))
        assertColor(pixel(16, 16), 0, 255, 0)
        assertEquals(1, capture.opened)
        assertEquals(1, capture.ready)
        capture.rotation = null
        requireNotNull(renderer).draw()
        capture.rotation = 90
        requireNotNull(renderer).draw()
        assertEquals(1, capture.ready)
    }

    @Test fun cameraFramesWithoutUsableMetadataTimeOutEvenWhileBuffersKeepArriving() {
        val capture = captureFixture(SceneSourceType.CAMERA)
        awaitCapture(capture.provider)
        capture.rotation = null
        repeat(6) { index ->
            now = (index + 1) * 1_000L
            val previous = capture.sampledTimestamp
            val surface = requireNotNull(capture.surface)
            val canvas = surface.lockCanvas(null)
            canvas.drawColor(android.graphics.Color.GREEN)
            surface.unlockCanvasAndPost(canvas)
            awaitSampling(capture, previous)
        }
        assertNull(capture.provider.texture("capture"))
        assertEquals(SceneProducerStatus.OFFLINE, capture.states.getValue("capture").status)
        assertEquals(1, capture.stopped)
    }

    private fun awaitSampling(capture: CaptureFixture, previousTimestamp: Long?) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
        do {
            requireNotNull(renderer).draw()
            if (capture.sampledTimestamp != previousTimestamp) return
            Thread.sleep(10)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        fail("Camera texture was not sampled")
    }

    @Test fun outputRateChangesReachCaptureWithoutReopeningTheSource() {
        val capture = captureFixture(SceneSourceType.CAMERA, outputFrameRate = 60)
        awaitCapture(capture.provider)
        assertEquals(60, capture.initialFrameRate)
        assertEquals(60, capture.rateRequests.last())
        capture.outputFrameRate = 30
        requireNotNull(renderer).draw()
        assertEquals(30, capture.rateRequests.last())
        assertEquals(1, capture.opened)
        assertEquals(0, capture.stopped)
        assertNotNull(capture.provider.texture("capture"))
    }

    private class CaptureFixture {
        lateinit var provider: AndroidSceneTextureProvider
        var opened = 0
        var stopped = 0
        var ready = 0
        var outputFrameRate = 30
        var initialFrameRate = 0
        val rateRequests = mutableListOf<Int>()
        var rotation: Int? = 0
        var sampledTimestamp: Long? = null
        var surface: android.view.Surface? = null
        var disconnect: ((Boolean) -> Unit)? = null
        var states: Map<String, SceneProducerState> = emptyMap()
    }

    private fun captureFixture(type: SceneSourceType, postFrame: Boolean = true, outputFrameRate: Int = 30): CaptureFixture {
        val fixture = CaptureFixture().also { it.outputFrameRate = outputFrameRate }
        fixture.provider = AndroidSceneTextureProvider(SceneProjectionRegistry(), SceneBitmapRegistry(),
            clockMillis = { now }, publishStates = { fixture.states = it }, sourceReady = { fixture.ready++ }, captureFrameRate = { fixture.outputFrameRate }, backendFactory = {
                object : SceneCaptureBackend {
                    override fun configure(sources: List<SceneSource>) = Unit
                    override fun setTargetFrameRate(frameRate: Int) {
                        if (fixture.rateRequests.lastOrNull() != frameRate) fixture.rateRequests.add(frameRate)
                    }
                    override fun samplingRotationDegrees(sourceId: String, timestamp: Long): Int? {
                        fixture.sampledTimestamp = timestamp
                        return fixture.rotation
                    }
                    override fun create(source: SceneSource, target: SceneCaptureTarget,
                        failed: (Boolean) -> Unit): SceneProducer {
                        fixture.opened++
                        fixture.initialFrameRate = target.frameRate
                        fixture.surface = target.surface
                        fixture.disconnect = failed
                        return object : SceneProducer {
                            override fun start() {
                                if (postFrame) {
                                    val canvas = target.surface.lockCanvas(null)
                                    canvas.drawColor(android.graphics.Color.GREEN)
                                    target.surface.unlockCanvasAndPost(canvas)
                                }
                            }
                            override fun stop() { fixture.stopped++ }
                            override fun release() = Unit
                        }
                    }
                    override fun release() = Unit
                }
            })
        renderer = SceneCompositorFilterRender(fixture.provider, clockMillis = { now }).also {
            it.initGl(SIZE, SIZE, InstrumentationRegistry.getInstrumentation().targetContext, SIZE, SIZE)
            it.initFBOLink()
            it.setPreviousTexId(texture(1, 1, intArrayOf(0, 0, 0, 255)).textureId)
            it.setScene(SceneRenderState(SceneComposition(canvasWidth = SIZE, canvasHeight = SIZE,
                sources = listOf(SceneSource("capture", type)),
                layers = listOf(SceneLayer("capture-layer", "capture")))), animate = false)
        }
        return fixture
    }

    private fun awaitCapture(provider: AndroidSceneTextureProvider) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
        do {
            requireNotNull(renderer).draw()
            if (provider.texture("capture") != null) return
            Thread.sleep(10)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        fail("Surface frame did not arrive")
    }

    @Test fun imageDocumentIsDecodedAndUploadedWithoutASeparateVideoProducer() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val image = java.io.File.createTempFile("scene-source", ".png", app.cacheDir)
        val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val provider = AndroidSceneTextureProvider(SceneProjectionRegistry(), SceneBitmapRegistry(),
                backendFactory = {
                    object : SceneCaptureBackend {
                        override fun configure(sources: List<SceneSource>) = Unit
                        override fun create(source: SceneSource, target: SceneCaptureTarget,
                            failed: (Boolean) -> Unit): SceneProducer = error("Image requested a capture producer")
                        override fun release() = Unit
                    }
                })
            val render = SceneCompositorFilterRender(provider)
            renderer = render
            render.initGl(SIZE, SIZE, app, SIZE, SIZE)
            render.initFBOLink()
            render.setPreviousTexId(texture(1, 1, intArrayOf(0, 0, 0, 255)).textureId)
            render.setScene(SceneRenderState(SceneComposition(canvasWidth = SIZE, canvasHeight = SIZE,
                sources = listOf(SceneSource("image", SceneSourceType.IMAGE, image.absolutePath)),
                layers = listOf(SceneLayer("image-layer", "image")))), animate = false)
            val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
            do {
                render.draw()
                if (provider.texture("image") != null) break
                Thread.sleep(10)
            } while (android.os.SystemClock.elapsedRealtime() < deadline)
            assertNotNull("Image did not upload", provider.texture("image"))
            assertColor(pixel(16, 16), 0, 0, 255)
        } finally { image.delete() }
    }

    @Test fun stableFilterContainerKeepsPrivacyPixelsAndSourceAttachmentAcrossEffectChanges() {
        val pattern = IntArray(SIZE * SIZE * 4) { index ->
            when (index % 4) {
                0 -> if ((index / 4) % SIZE % 2 == 0) 255 else 0
                1 -> if ((index / 4) % SIZE % 2 == 0) 0 else 255
                2 -> 0
                else -> 255
            }
        }
        frames["pattern"] = texture(SIZE, SIZE, pattern)
        var attached = 0
        var detached = 0
        val provider = object : SceneTextureProvider {
            override fun attach(context: Context, width: Int, height: Int) { attached++ }
            override fun update(plan: SceneFramePlan, nowMillis: Long) = Unit
            override fun texture(sourceId: String) = frames[sourceId]
            override fun detach() { detached++ }
        }
        val scene = SceneCompositorFilterRender(provider).apply {
            setScene(SceneRenderState(composition("pattern")), animate = false)
        }
        val privacy = com.vivid.feature.streaming.PrivacyBlurFilterRender().apply {
            setFullFrame(true)
            setPixelGrid(0.25f)
        }
        val chain = com.vivid.feature.streaming.compositor.gl.SceneFilterChainRender()
        chain.setChain(listOf(scene, privacy))
        chain.initGl(SIZE, SIZE, InstrumentationRegistry.getInstrumentation().targetContext, SIZE, SIZE)
        chain.initFBOLink()
        chain.setPreviousTexId(texture(1, 1, intArrayOf(0, 0, 0, 255)).textureId)
        try {
            val output = chain.texId
            repeat(6) { index ->
                val effects = if (index % 2 == 0) listOf(
                    com.pedro.encoder.input.gl.render.filters.NegativeFilterRender()) else emptyList()
                chain.setChain(listOf(scene, privacy) + effects)
                chain.draw()
                assertEquals(output, chain.texId)
                val protected = pixel(1, 1, chain)
                for (x in 2..6) assertArrayEquals(protected, pixel(x, 1, chain))
                assertEquals(1, attached)
                assertEquals(0, detached)
            }
            privacy.setFullFrame(false)
            chain.setChain(listOf(scene, privacy))
            chain.draw()
            assertFalse("Negative control must reveal the original alternating pixels",
                pixel(1, 1, chain).contentEquals(pixel(2, 1, chain)))
            privacy.setFullFrame(true)
            chain.setChain(listOf(privacy))
            chain.draw()
            assertEquals(1, detached)
            assertColor(pixel(16, 16, chain), 0, 0, 0)
            chain.setChain(emptyList())
            chain.draw()
            assertEquals(output, chain.texId)
            assertColor(pixel(16, 16, chain), 0, 0, 0)
        } finally { chain.release() }
        assertEquals(1, detached)
    }

    private fun composition(vararg ids: String) = SceneComposition(
        canvasWidth = SIZE, canvasHeight = SIZE,
        sources = ids.map { SceneSource(it, SceneSourceType.IMAGE) },
        layers = ids.map { SceneLayer("layer:$it", it) },
    )

    private fun start(scene: SceneComposition, input: Int? = null, inputId: String? = null,
        frameRendered: () -> Unit = {}): SceneCompositorFilterRender {
        val provider = object : SceneTextureProvider {
            override fun attach(context: Context, width: Int, height: Int) = Unit
            override fun update(plan: SceneFramePlan, nowMillis: Long) { lastPlan = plan }
            override fun texture(sourceId: String) = frames[sourceId]
            override fun detach() = Unit
        }
        return SceneCompositorFilterRender(provider, clockMillis = { now }, frameRendered = frameRendered).also {
            renderer = it
            it.initGl(SIZE, SIZE, InstrumentationRegistry.getInstrumentation().targetContext, SIZE, SIZE)
            it.initFBOLink()
            it.setPreviousTexId(input ?: texture(1, 1, intArrayOf(0, 0, 0, 255)).textureId)
            it.setScene(SceneRenderState(scene, inputId), animate = false)
        }
    }

    private fun texture(width: Int, height: Int, rgba: IntArray): SceneTextureFrame {
        val id = IntArray(1)
        GLES20.glGenTextures(1, id, 0)
        owned.add(id[0])
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val pixels = ByteBuffer.allocateDirect(rgba.size).apply { rgba.forEach { put(it.toByte()) }; position(0) }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        return SceneTextureFrame(id[0])
    }

    private fun pixel(x: Int, yFromTop: Int,
        target: com.pedro.encoder.input.gl.render.filters.BaseFilterRender = requireNotNull(renderer)): IntArray {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.getRenderHandler().fboId[0])
        val pixels = ByteBuffer.allocateDirect(4)
        GLES20.glReadPixels(x, SIZE - 1 - yFromTop, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        return IntArray(4) { pixels.get(it).toInt() and 255 }
    }

    private fun assertColor(actual: IntArray, red: Int, green: Int, blue: Int) {
        listOf(red, green, blue, 255).forEachIndexed { index, value ->
            assertEquals("RGBA channel $index", value.toDouble(), actual[index].toDouble(), 2.0)
        }
    }

    companion object { private const val SIZE = 32 }
}
