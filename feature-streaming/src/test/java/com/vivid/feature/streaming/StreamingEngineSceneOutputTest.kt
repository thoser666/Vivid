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

    private fun engine(): StreamingEngine {
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
        }, mockk(relaxed = true), mockk(relaxed = true), VideoSourceRegistry()).also { engine ->
            engine.sceneSessionFactory = { checkers = it; created++; session }
            engine.dispatchSceneTask = { it() }
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

}
