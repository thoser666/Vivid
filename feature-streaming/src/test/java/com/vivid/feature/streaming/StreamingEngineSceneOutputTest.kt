package com.vivid.feature.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.*
import com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession
import com.vivid.feature.streaming.source.*
import io.mockk.*
import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StreamingEngineSceneOutputTest {
    @TempDir lateinit var files: File
    private val camera = mockk<MultiCamera2>(relaxed = true)
    private val legacyGl = mockk<GlStreamInterface>(relaxed = true)
    private val sceneGl = mockk<GlStreamInterface>(relaxed = true)
    private val session = mockk<SceneEncoderSession>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val targets = mutableSetOf<Int>()
    private var recording = false
    private var running = false
    private var checkers = emptyList<ConnectChecker>()
    private var created = 0
    private fun graph() = SceneComposition(sources = listOf(SceneSource("image", SceneSourceType.IMAGE, "content://image")),
        layers = listOf(SceneLayer("image-layer", "image")))

    private fun engine(playerFactory: PlayerFactory = mockk(relaxed = true)): StreamingEngine {
        every { camera.glInterface } returns legacyGl
        every { context.filesDir } answers { files }
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_DENIED
        every { session.gl } returns sceneGl
        every { session.isStreaming } answers { targets.isNotEmpty() }
        every { session.isRecording } answers { recording }
        every { sceneGl.isRunning } answers { running }
        every { session.prepare(any()) } returns true
        every { session.startTarget(any(), any()) } answers { targets.add(firstArg()); running = true; Unit }
        every { session.stopTarget(any()) } answers { targets.remove(firstArg<Int>()); Unit }
        every { session.startRecording(any(), any(), any()) } answers { recording = true; running = true; true }
        every { session.stopRecording() } answers { recording = false }
        every { session.startPreview(any(), any(), any()) } answers { running = true }
        every { session.stopPreview() } answers { if (targets.isEmpty() && !recording) running = false }
        return StreamingEngine(context, object : CameraFactory {
            override fun create(connectCheckers: List<ConnectChecker>) = camera
        }, mockk(relaxed = true), playerFactory, VideoSourceRegistry()).also { engine ->
            engine.sceneSessionFactory = { checkers = it; created++; session }
            engine.dispatchSceneTask = { it() }
            engine.sceneFileReadable = { true }
            engine.encoderCapabilities = object : EncoderCapabilities {
                override fun supports(mime: String, width: Int, height: Int, fps: Int) = true
            }
        }
    }

    @Test fun `image scene starts without camera permission and uses only the scene transport`() {
        val engine = engine()
        val fallbackDriver = mockk<com.pedro.library.view.ForceRenderer>(relaxed = true)
        engine.sceneRenderClock = com.vivid.feature.streaming.compositor.SceneRenderClock(fallbackDriver)
        engine.setSceneComposition(graph())
        assertFalse(engine.sceneNeedsCameraPermission())
        engine.startStream(listOf("rtmp://one/live", "rtmp://two/live"))
        assertEquals(1, created)
        verify(exactly = 0) { camera.prepareVideo(); camera.prepareAudio(); camera.startStream(any(), any(), any()) }
        verify(exactly = 1) { session.prepare(any()) }
        checkers[0].onConnectionSuccess()
        checkers[1].onConnectionSuccess()
        assertEquals(StreamingState.Streaming, engine.streamingState.value)
        assertEquals(2, targets.size)
        verify(exactly = 0) { fallbackDriver.start(any()) }
        engine.stopStream()
        assertTrue(targets.isEmpty())
        assertEquals(StreamingState.Idle, engine.streamingState.value)
    }

    @Test fun `scene recording and two streams retain one prepared output until both consumers stop`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        assertTrue(engine.startReplay(100L, includeAudio = false))
        assertTrue(engine.replayState.value is ReplayState.Recording)
        engine.startStream(listOf("rtmp://one/live", "rtmp://two/live"))
        verify(exactly = 1) { session.prepare(any()) }
        engine.stopStream()
        assertTrue(recording)
        engine.stopReplay()
        assertFalse(recording)
        assertEquals(ReplayState.Idle, engine.replayState.value)
        verify { session.startRecording(any(), false, any()) }
    }

    @Test fun `idle image preview creates no hardware encoder and scene edits keep its session`() {
        val engine = engine()
        val surface = mockk<Surface>(relaxed = true)
        engine.setSceneComposition(graph())
        engine.attachPreview(surface, 640, 360)
        engine.setSceneComposition(graph().copy(layers = listOf(SceneLayer("image-layer", "image", SceneTransform(width = .5f)))))
        assertEquals(1, created)
        verify(exactly = 0) { session.prepare(any()) }
        verify(atLeast = 1) { session.startPreview(surface, 640, 360) }
        engine.detachPreview(surface)
        assertFalse(running)
        engine.setSceneComposition(null)
        verify { session.close() }
        assertNull(engine.sceneComposition.value)
    }

    @Test fun `one failed target preserves the remaining connection and retired callbacks are ignored`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live", "rtmp://two/live"))
        checkers[1].onConnectionSuccess()
        checkers[0].onConnectionFailed("offline")
        assertEquals(setOf(1), targets)
        assertEquals(StreamingState.Streaming, engine.streamingState.value)
        engine.stopStream()
        engine.setSceneComposition(null)
        checkers[1].onConnectionFailed("retired")
        assertEquals(StreamingState.Idle, engine.streamingState.value)
    }

    @Test fun `clearing a composition during recording retains its encoder and creates a default graph`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startReplay(100L, false)
        engine.setSceneComposition(null)
        assertNotNull(engine.sceneComposition.value)
        assertTrue(recording)
        verify(exactly = 0) { session.close() }
        engine.stopReplay()
    }
    @Test fun `failed encoder preparation restores idle scene preview without starting transports`() {
        val engine = engine()
        val surface = mockk<Surface>(relaxed = true)
        engine.setSceneComposition(graph())
        engine.attachPreview(surface, 640, 360)
        clearMocks(session, answers = false, recordedCalls = true, childMocks = false)
        every { session.prepare(any()) } returns false
        engine.startStream(listOf("rtmp://one/live"))
        verify(exactly = 0) { session.startTarget(any(), any()) }
        verify(atLeast = 1) { session.startPreview(surface, 640, 360) }
        assertTrue(running)
    }

    @Test fun `native screen scene offers recording without a camera base source`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        assertTrue(engine.switchSource(VideoSourceKind.SCREEN_CAPTURE))
        assertTrue(engine.canRecordReplay())
        assertFalse(engine.sceneNeedsCameraPermission())
        assertTrue(engine.startReplay(100L, false))
        engine.stopReplay()
    }

    @Test fun `scene added to existing legacy stream starts an independent clock and clearing it retires ticks`() {
        val engine = engine()
        val driver = mockk<com.pedro.library.view.ForceRenderer>(relaxed = true)
        val tick = slot<() -> Unit>()
        every { driver.start(capture(tick)) } just Runs
        engine.sceneRenderClock = com.vivid.feature.streaming.compositor.SceneRenderClock(driver)
        engine.initializeCamera()
        every { camera.isStreaming } returns true
        every { legacyGl.isRunning } returns true
        engine.setSceneComposition(graph())
        tick.captured()
        verify { legacyGl.onFrameAvailable(null) }
        verify(exactly = 0) { camera.prepareVideo(); camera.prepareAudio() }
        assertEquals(0, created)
        engine.setSceneComposition(graph().copy(canvasWidth = 1920))
        verify(exactly = 1) { driver.start(any()) }
        engine.setSceneComposition(null)
        clearMocks(legacyGl, answers = false, recordedCalls = true, childMocks = false)
        tick.captured()
        verify(exactly = 0) { legacyGl.onFrameAvailable(any()) }
    }

    @Test fun `stopping a headless legacy stream retires its scene clock`() {
        val engine = engine()
        val driver = mockk<com.pedro.library.view.ForceRenderer>(relaxed = true)
        val tick = slot<() -> Unit>()
        every { driver.start(capture(tick)) } just Runs
        engine.sceneRenderClock = com.vivid.feature.streaming.compositor.SceneRenderClock(driver)
        engine.initializeCamera()
        var live = true
        every { camera.isStreaming } answers { live }
        every { legacyGl.isRunning } answers { live }
        every { camera.stopStream() } answers { live = false }
        engine.setSceneComposition(graph())
        engine.stopStream()
        clearMocks(legacyGl, answers = false, recordedCalls = true, childMocks = false)
        live = true // Even if this old GL is restarted, its retired callback must stay inert.
        tick.captured()
        verify(exactly = 0) { legacyGl.onFrameAvailable(any()) }
    }

    private fun sceneRender(engine: StreamingEngine): com.vivid.feature.streaming.compositor.gl.SceneCompositorFilterRender =
        StreamingEngine::class.java.getDeclaredField("sceneRender").run {
            isAccessible = true
            get(engine) as com.vivid.feature.streaming.compositor.gl.SceneCompositorFilterRender
        }

    @Test fun `effect changes reuse the scene renderer queued for the current GL owner`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.attachPreview(mockk(relaxed = true), 640, 360)
        val first = sceneRender(engine)
        engine.setVideoFilter(VideoFilter.GRAYSCALE)
        assertSame(first, sceneRender(engine))
        engine.setVideoFilter(VideoFilter.SEPIA)
        assertSame(first, sceneRender(engine))
        assertFalse(first.isReleased)
    }

    @Test fun `a released scene renderer is replaced after the same GL owner restarts`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.attachPreview(mockk(relaxed = true), 640, 360)
        val first = sceneRender(engine)
        running = false
        first.release()
        assertTrue(first.isReleased)
        running = true
        engine.setVideoFilter(VideoFilter.GRAYSCALE)
        assertNotSame(first, sceneRender(engine))
        assertFalse(sceneRender(engine).isReleased)
    }

    @Test fun `the first filter selection and its replacement reach the existing scene output immediately`() {
        val queued = slot<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>()
        every { sceneGl.setFilter(capture(queued)) } just Runs
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.attachPreview(mockk(relaxed = true), 640, 360)
        val chain = queued.captured as com.vivid.feature.streaming.compositor.gl.SceneFilterChainRender
        val gray = mockk<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>(relaxed = true)
        val sepia = mockk<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>(relaxed = true)
        mockkObject(VideoFilterController.Companion)
        try {
            every { VideoFilterController.createFilterRender(VideoFilter.GRAYSCALE) } returns gray
            every { VideoFilterController.createFilterRender(VideoFilter.SEPIA) } returns sepia
            assertTrue(engine.setVideoFilter(VideoFilter.GRAYSCALE))
            assertSame(gray, chain.requestedChain().last())
            assertTrue(engine.setVideoFilter(VideoFilter.SEPIA))
            assertSame(sepia, chain.requestedChain().last())
            verify(exactly = 1) { sceneGl.setFilter(any<com.pedro.encoder.input.gl.render.filters.BaseFilterRender>()) }
        } finally { unmockkObject(VideoFilterController.Companion) }
    }

    @Test fun `live movie switch uses scene audio without preparing a legacy file encoder`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        assertTrue(engine.startReplay(100L, true))
        val uri = mockk<android.net.Uri>()
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://movie"
        assertTrue(engine.setVideoPlayerUri(uri))
        assertTrue(engine.switchSource(VideoSourceKind.VIDEO_PLAYER))
        val next = engine.sceneComposition.value!!
        assertEquals(graph().sources.first(), next.sources.first { it.id == "image" })
        val video = next.sources.single { it.type == SceneSourceType.VIDEO }
        assertEquals("content://movie", video.locator)
        verify(exactly = 1) { session.prepare(any()) }
        verify(exactly = 0) { session.close() }
        for (field in listOf("videoPlayerSource", "replaySource")) {
            assertNull(StreamingEngine::class.java.getDeclaredField(field).apply { isAccessible = true }.get(engine))
        }
        assertTrue(recording); assertEquals(setOf(0), targets)
    }

    @Test fun `replay routes audio after the first video frame and follows only its current loop identity`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        val replay = File(files, "replay.mp4").apply { writeBytes(byteArrayOf(1)) }
        val inputs = mutableListOf<com.vivid.feature.streaming.compositor.audio.SceneAudioInput?>()
        every { session.setAudioInput(any(), any()) } answers { inputs.add(firstArg()); Unit }
        assertTrue(engine.useReplayAsSource(replay))
        assertTrue(inputs.last()!!.create() is com.pedro.encoder.input.sources.audio.SilenceAudioSource)
        val source = engine.sceneComposition.value!!.sources.single { it.type == SceneSourceType.VIDEO }
        val textures = mockk<com.vivid.feature.streaming.compositor.gl.AndroidSceneTextureProvider>(relaxed = true)
        val identity = Any()
        val playback = com.vivid.feature.streaming.compositor.capture.SceneVideoPlayback(identity, 12.5, 0)
        every { textures.videoPlayback(source.id) } returns playback
        StreamingEngine::class.java.getDeclaredField("sceneTextures").apply { isAccessible = true }.set(engine, textures)
        StreamingEngine::class.java.getDeclaredMethod("sceneSourceReady", String::class.java).apply { isAccessible = true }
            .invoke(engine, source.id)
        assertTrue(inputs.last()!!.create() is com.vivid.feature.streaming.compositor.audio.SceneFileAudioSource)
        val loop = StreamingEngine::class.java.getDeclaredMethod("sceneVideoLooped", String::class.java,
            com.vivid.feature.streaming.compositor.capture.SceneVideoPlayback::class.java).apply { isAccessible = true }
        every { textures.videoPlayback(source.id) } returns playback.copy(positionSeconds = 0.0, cycle = 1)
        loop.invoke(engine, source.id, playback.copy(positionSeconds = 0.0, cycle = 1))
        verify(exactly = 1) { session.seekFileAudio(0.0) }
        loop.invoke(engine, source.id, playback.copy(identity = Any(), positionSeconds = 0.0, cycle = 2))
        verify(exactly = 1) { session.seekFileAudio(any()) }
        verify(exactly = 1) { session.prepare(any()) }
    }

    @Test fun `an unreadable replay leaves the live scene and selected source untouched`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        val previous = engine.activeSourceKind.value
        val file = File(files, "broken.mp4").apply { writeBytes(byteArrayOf(1)) }
        engine.sceneFileReadable = { false }
        assertFalse(engine.useReplayAsSource(file))
        assertEquals(previous, engine.activeSourceKind.value)
        assertEquals(graph(), engine.sceneComposition.value)
        assertNull(engine.activeReplayFile)
        verify(exactly = 0) { session.close() }
    }

    private fun movieUri(name: String) = mockk<android.net.Uri>().also { uri ->
        every { uri.scheme } returns "content"
        every { uri.toString() } returns "content://$name"
    }

    private fun fullImageGraph() = graph().copy(
        sources = List(SceneComposition.MAX_SOURCES) { SceneSource("image-$it", SceneSourceType.IMAGE, "content://image-$it") },
        layers = List(SceneComposition.MAX_SOURCES) { SceneLayer("layer-$it", "image-$it") },
    )

    @Test fun `a rejected live movie change restores its previous document and graph`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        val first = movieUri("first")
        assertTrue(engine.setVideoPlayerUri(first))
        assertTrue(engine.switchSource(VideoSourceKind.VIDEO_PLAYER))
        val full = fullImageGraph()
        engine.setSceneComposition(full)
        assertFalse(engine.setVideoPlayerUri(movieUri("second")))
        val selected = StreamingEngine::class.java.getDeclaredField("sceneVideoUri").apply { isAccessible = true }.get(engine)
        assertSame(first, selected)
        assertEquals(full, engine.sceneComposition.value)
        verify(exactly = 1) { session.prepare(any()) }
    }

    @Test fun `a rejected replay switch restores the previous file selection`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        val first = File(files, "first.mp4").apply { writeBytes(byteArrayOf(1)) }
        val second = File(files, "second.mp4").apply { writeBytes(byteArrayOf(2)) }
        assertTrue(engine.useReplayAsSource(first))
        assertTrue(engine.switchSource(VideoSourceKind.CAMERA))
        val full = fullImageGraph()
        engine.setSceneComposition(full)
        assertFalse(engine.useReplayAsSource(second))
        assertEquals(first, engine.activeReplayFile)
        assertEquals(VideoSourceKind.CAMERA, engine.activeSourceKind.value)
        assertEquals(full, engine.sceneComposition.value)
    }

    private fun verifyLegacyFileHandoff(kind: VideoSourceKind) {
        val old = mockk<com.pedro.library.multiple.MultiFromFile>(relaxed = true)
        val fresh = mockk<com.pedro.library.multiple.MultiFromFile>(relaxed = true)
        for (player in listOf(old, fresh)) {
            every { player.isStreaming } returns false
            every { player.prepareVideo(any<Context>(), any<android.net.Uri>()) } returns true
            every { player.prepareAudio(any<Context>(), any<android.net.Uri>()) } returns true
            every { player.prepareVideo(any<String>()) } returns true
            every { player.prepareAudio(any<String>()) } returns true
        }
        val retiredCallbacks = mutableListOf<List<ConnectChecker>>()
        var allocated = 0
        val factory = object : PlayerFactory {
            override fun create(connectCheckers: List<ConnectChecker>): com.pedro.library.multiple.MultiFromFile {
                retiredCallbacks.add(connectCheckers)
                return if (allocated++ == 0) old else fresh
            }
        }
        val engine = engine(factory)
        val uri = movieUri("movie")
        val replay = File(files, "replay.mp4").apply { writeBytes(byteArrayOf(1)) }
        if (kind == VideoSourceKind.VIDEO_PLAYER) {
            assertTrue(engine.setVideoPlayerUri(uri))
            assertTrue(engine.switchSource(kind))
        } else assertTrue(engine.useReplayAsSource(replay))
        engine.setSceneComposition(graph())
        engine.startStream(listOf("rtmp://one/live"))
        verifyOrder { old.stopStream(); session.prepare(any()) }
        assertEquals(1, allocated)
        checkers[0].onConnectionSuccess()
        retiredCallbacks.first()[0].apply {
            onConnectionStarted("rtmp://retired/live"); onConnectionFailed("retired")
            onDisconnect(); onAuthError(); onNewBitrate(1234); onConnectionSuccess()
        }
        assertEquals(StreamingState.Streaming, engine.streamingState.value)
        assertNull(engine.targetStates.value.first().bitrateKbps)
        engine.stopStream()
        engine.setSceneComposition(null)
        assertEquals(1, allocated, "Removing a scene must not eagerly allocate legacy encoders")
        engine.startStream(listOf("rtmp://restored/live"))
        assertEquals(2, allocated)
        if (kind == VideoSourceKind.VIDEO_PLAYER) {
            verify(exactly = 1) { fresh.prepareVideo(context, uri); fresh.prepareAudio(context, uri) }
        } else {
            verify(exactly = 1) { fresh.setLoopMode(true); fresh.prepareVideo(replay.absolutePath); fresh.prepareAudio(replay.absolutePath) }
        }
        verify(exactly = 1) { fresh.startStream(com.pedro.library.multiple.MultiType.RTMP, 0, "rtmp://restored/live") }
        retiredCallbacks.last()[0].onConnectionSuccess()
        assertEquals(StreamingState.Streaming, engine.streamingState.value)
    }

    @Test fun `prepared legacy movie encoders retire before native output and restore lazily`() =
        verifyLegacyFileHandoff(VideoSourceKind.VIDEO_PLAYER)

    @Test fun `prepared legacy replay encoders retire before native output and restore lazily`() =
        verifyLegacyFileHandoff(VideoSourceKind.REPLAY)

    @Test fun `a missing legacy movie fails every requested target without allocating a scene output`() {
        val engine = engine()
        assertTrue(engine.switchSource(VideoSourceKind.VIDEO_PLAYER))
        val urls = listOf("rtmp://one/live", "rtmp://two/live")
        engine.startStream(urls)
        assertTrue(engine.streamingState.value is StreamingState.Failed)
        assertEquals(urls, engine.targetStates.value.map { it.url })
        assertTrue(engine.targetStates.value.all { it.status == StreamTargetStatus.FAILED })
        assertEquals(0, created)
    }

    @Test fun `the selected movie ends once while independent video layers and replay sources loop`() {
        val engine = engine()
        engine.setSceneComposition(graph())
        assertTrue(engine.setVideoPlayerUri(movieUri("movie")))
        assertTrue(engine.switchSource(VideoSourceKind.VIDEO_PLAYER))
        val primary = engine.sceneComposition.value!!.sources.single { it.type == SceneSourceType.VIDEO }
        val looping = StreamingEngine::class.java.getDeclaredMethod("sceneVideoLooping", SceneSource::class.java)
            .apply { isAccessible = true }
        assertEquals(false, looping.invoke(engine, primary))
        assertEquals(true, looping.invoke(engine, primary.copy(id = "independent-video")))
        val replay = File(files, "replay.mp4").apply { writeBytes(byteArrayOf(1)) }
        assertTrue(engine.useReplayAsSource(replay))
        assertEquals(true, looping.invoke(engine, engine.sceneComposition.value!!.sources.single { it.type == SceneSourceType.VIDEO }))
    }

}
