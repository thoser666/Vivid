package com.vivid.feature.streaming

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
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
    private lateinit var cameraFactory: CameraFactory
    private lateinit var streamingEngine: StreamingEngine

    @BeforeEach
    fun setUp() {
        rtmpCamera = mockk(relaxed = true)
        cameraFactory = object : CameraFactory {
            override fun create(openGlView: OpenGlView, connectChecker: ConnectChecker): RtmpCamera2 {
                return rtmpCamera
            }
        }
        streamingEngine = StreamingEngine(cameraFactory)
    }

    @Test
    fun `startStream should not do anything if url is blank`() = runTest {
        // Arrange
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)

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
    fun `startStream should call startStream on camera if url is valid`() = runTest {
        // Arrange
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
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
    fun `startStream should set Preparing while starting`() = runTest {
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns true
        every { rtmpCamera.prepareVideo() } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream should fail when audio preparation fails`() = runTest {
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
        every { rtmpCamera.isStreaming } returns false
        every { rtmpCamera.prepareAudio() } returns false

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Failed("Failed to prepare audio/video"), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { rtmpCamera.startStream(any()) }
    }

    @Test
    fun `startStream should not restart when already streaming`() = runTest {
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
        every { rtmpCamera.isStreaming } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        coVerify(exactly = 0) { rtmpCamera.startStream(any()) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should stop the camera and reset the state to Idle`() = runTest {
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
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
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine.initializeCamera(openGlView)
        every { rtmpCamera.isStreaming } returns false

        streamingEngine.stopStream()

        verify(exactly = 0) { rtmpCamera.stopStream() }
    }

    @Test
    fun `connect checker callbacks should update the streaming state`() = runTest {
        var capturedChecker: ConnectChecker? = null
        val openGlView: OpenGlView = mockk(relaxed = true)
        streamingEngine = StreamingEngine(
            object : CameraFactory {
                override fun create(openGlView: OpenGlView, connectChecker: ConnectChecker): RtmpCamera2 {
                    capturedChecker = connectChecker
                    return rtmpCamera
                }
            },
        )
        streamingEngine.initializeCamera(openGlView)
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
}
