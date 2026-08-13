package com.vivid.feature.streaming

import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.GlStreamInterface
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreamingEngineTest {

    private lateinit var rtmpCamera: RtmpCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var cameraFactory: CameraFactory
    private lateinit var streamingEngine: StreamingEngine

    @BeforeEach
    fun setUp() {
        rtmpCamera = mockk(relaxed = true)
        glStreamInterface = mockk(relaxed = true)
        every { rtmpCamera.glInterface } returns glStreamInterface
        cameraFactory = object : CameraFactory {
            override fun create(connectChecker: ConnectChecker): RtmpCamera2 {
                return rtmpCamera
            }
        }
        streamingEngine = StreamingEngine(cameraFactory)
    }

    @Test
    fun `startStream should not do anything if url is blank`() = runTest {
        // Arrange
        streamingEngine.initializeCamera()

        // Act
        streamingEngine.startStream("")

        // Assert
        coVerify(exactly = 0) { rtmpCamera.startStream(any()) }
    }

    @Test
    fun `streamingState should be Idle initially`() = runTest {
        val state = streamingEngine.streamingState.first()
        assertEquals(StreamingState.Idle, state)
    }

    @Test
    fun `initializeCamera should only create the camera once`() = runTest {
        var createCount = 0
        streamingEngine = StreamingEngine(
            object : CameraFactory {
                override fun create(connectChecker: ConnectChecker): RtmpCamera2 {
                    createCount++
                    return rtmpCamera
                }
            },
        )

        streamingEngine.initializeCamera()
        streamingEngine.initializeCamera()

        assertEquals(1, createCount)
    }

    @Test
    fun `startStream should call startStream on camera if url is valid`() = runTest {
        // Arrange
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true
        val testUrl = "rtmp://test.com/app"

        // Act
        streamingEngine.startStream(testUrl)

        // Assert
        coVerify { rtmpCamera.startStream(testUrl) }
    }

    @Test
    fun `startStream should pass an rtmps url through to the camera unchanged`() = runTest {
        // Arrange: RootEncoder erkennt rtmps:// am Scheme und aktiviert TLS selbst,
        // die Engine darf die URL also nicht umschreiben oder verwerfen.
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true
        val testUrl = "rtmps://live.kick.com/app/live_12345_secret"

        // Act
        streamingEngine.startStream(testUrl)

        // Assert
        coVerify { rtmpCamera.startStream(testUrl) }
    }

    @Test
    fun `startStream should set Preparing while starting`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream should fail when audio preparation fails`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns false

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Failed("Failed to prepare audio/video"), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { rtmpCamera.startStream(any()) }
    }

    @Test
    fun `startStream should not restart when already streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        coVerify(exactly = 0) { rtmpCamera.startStream(any()) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should stop the camera and reset the state to Idle`() = runTest {
        streamingEngine.initializeCamera()
        var isStreaming = false
        every { rtmpCamera.isStreaming } answers { isStreaming }
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true
        every { rtmpCamera.startStream("rtmp://test.com/app") } just runs

        streamingEngine.startStream("rtmp://test.com/app")
        isStreaming = true
        streamingEngine.stopStream()

        verify { rtmpCamera.stopStream() }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should do nothing when not streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.isStreaming } returns false

        streamingEngine.stopStream()

        verify(exactly = 0) { rtmpCamera.stopStream() }
    }

    @Test
    fun `connect checker callbacks should update the streaming state`() = runTest {
        var capturedChecker: ConnectChecker? = null
        streamingEngine = StreamingEngine(
            object : CameraFactory {
                override fun create(connectChecker: ConnectChecker): RtmpCamera2 {
                    capturedChecker = connectChecker
                    return rtmpCamera
                }
            },
        )
        streamingEngine.initializeCamera()
        val checker = requireNotNull(capturedChecker)

        checker.onConnectionStarted("rtmp://test.com/app")
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)

        checker.onConnectionSuccess()
        assertEquals(StreamingState.Streaming, streamingEngine.streamingState.value)

        checker.onDisconnect()
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)

        checker.onConnectionFailed("boom")
        assertEquals(StreamingState.Failed("boom"), streamingEngine.streamingState.value)
        verify { rtmpCamera.stopStream() }

        checker.onAuthError()
        assertEquals(StreamingState.Failed("RTMP Auth Error"), streamingEngine.streamingState.value)
    }

    // --- Preview (GL-freier Pfad): attach/detach unabhängig vom Encoder ---

    @Test
    fun `attachPreview is deferred until the gl pipeline runs and attaches on start`() = runTest {
        streamingEngine.initializeCamera()
        var glRunning = false
        every { glStreamInterface.isRunning } answers { glRunning }
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        // prepareVideo startet die GL-Pipeline (real: prepareGlView -> glInterface.start())
        every { rtmpCamera.prepareVideo() } answers { glRunning = true; true }
        val surface: Surface = mockk(relaxed = true)

        // Vorschau kommt vor dem Stream-Start an -> GL läuft noch nicht.
        streamingEngine.attachPreview(surface, 640, 480)
        verify(exactly = 0) { glStreamInterface.attachPreview(any()) }

        // Beim Stream-Start (nach prepareVideo) wird die gemerkte Surface angehängt.
        streamingEngine.startStream("rtmp://test.com/app")

        verify(exactly = 1) { glStreamInterface.attachPreview(surface) }
        verify(exactly = 1) { glStreamInterface.setPreviewResolution(640, 480) }
    }

    @Test
    fun `attachPreview attaches immediately when the gl pipeline is already running`() = runTest {
        streamingEngine.initializeCamera()
        every { glStreamInterface.isRunning } returns true
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 1280, 720)

        verify(exactly = 1) { glStreamInterface.attachPreview(surface) }
        verify(exactly = 1) { glStreamInterface.setPreviewResolution(1280, 720) }
    }

    @Test
    fun `detachPreview releases only the preview surface`() = runTest {
        streamingEngine.initializeCamera()
        val surface: Surface = mockk(relaxed = true)
        streamingEngine.attachPreview(surface, 640, 480)

        streamingEngine.detachPreview()

        verify(exactly = 1) { glStreamInterface.deAttachPreview() }
        // Der Stream selbst wird durch das Ablösen der Vorschau nicht gestoppt.
        verify(exactly = 0) { rtmpCamera.stopStream() }
    }

    @Test
    fun `re-attaching a new preview surface after activity recreate keeps the stream`() = runTest {
        streamingEngine.initializeCamera()
        every { glStreamInterface.isRunning } returns true
        val firstSurface: Surface = mockk(relaxed = true)
        val secondSurface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(firstSurface, 640, 480)
        streamingEngine.detachPreview()
        streamingEngine.attachPreview(secondSurface, 640, 480)

        verify { glStreamInterface.attachPreview(firstSurface) }
        verify { glStreamInterface.attachPreview(secondSurface) }
        verify(exactly = 1) { glStreamInterface.deAttachPreview() }
    }

    @Test
    fun `startStream without a preview surface streams view-less`() = runTest {
        streamingEngine.initializeCamera()
        every { glStreamInterface.isRunning } returns true
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        // Ohne gemerkte Surface wird nichts angehängt — der Stream läuft trotzdem.
        verify(exactly = 0) { glStreamInterface.attachPreview(any()) }
        coVerify { rtmpCamera.startStream("rtmp://test.com/app") }
    }

    // --- Fokus-Lock (Moblin #377) ---

    @Test
    fun `toggleFocusLock returns false and keeps AUTO before the camera is initialized`() = runTest {
        val result = streamingEngine.toggleFocusLock()

        assertEquals(false, result)
        assertEquals(FocusMode.AUTO, streamingEngine.focusMode.value)
    }

    @Test
    fun `toggleFocusLock locks the camera to infinity`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.disableAutoFocus() } returns true

        val result = streamingEngine.toggleFocusLock()

        assertEquals(true, result)
        assertEquals(FocusMode.LOCKED_INFINITY, streamingEngine.focusMode.value)
        verify { rtmpCamera.disableAutoFocus() }
        verify { rtmpCamera.setFocusDistance(CameraFocusController.FOCUS_DISTANCE_INFINITY) }
    }

    @Test
    fun `toggleFocusLock unlocks and re-enables autofocus`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.disableAutoFocus() } returns true
        streamingEngine.toggleFocusLock()

        every { rtmpCamera.enableAutoFocus() } returns true
        val result = streamingEngine.toggleFocusLock()

        assertEquals(true, result)
        assertEquals(FocusMode.AUTO, streamingEngine.focusMode.value)
        verify { rtmpCamera.enableAutoFocus() }
    }

    @Test
    fun `toggleFocusLock keeps the mode and state when the camera rejects the lock`() = runTest {
        streamingEngine.initializeCamera()
        every { rtmpCamera.disableAutoFocus() } returns false

        val result = streamingEngine.toggleFocusLock()

        assertEquals(false, result)
        assertEquals(FocusMode.AUTO, streamingEngine.focusMode.value)
        verify(exactly = 0) { rtmpCamera.setFocusDistance(any()) }
    }
}
