package com.vivid.feature.streaming

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
}
