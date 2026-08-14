package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.Surface
import android.view.View
import com.pedro.common.ConnectChecker
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiType
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StreamingEngineTest {

    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var cameraFactory: CameraFactory
    private lateinit var streamingEngine: StreamingEngine
    private var capturedCheckers: List<ConnectChecker> = emptyList()

    @BeforeEach
    fun setUp() {
        camera = mockk(relaxed = true)
        glStreamInterface = mockk(relaxed = true)
        every { camera.glInterface } returns glStreamInterface
        cameraFactory = object : CameraFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 {
                capturedCheckers = connectCheckers
                return camera
            }
        }
        streamingEngine = StreamingEngine(cameraFactory)
    }

    private fun streamingCameraReady() {
        every { camera.isStreaming } returns false
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
    }

    @Test
    fun `startStream should not do anything if url is blank`() = runTest {
        // Arrange
        streamingEngine.initializeCamera()

        // Act
        streamingEngine.startStream("")

        // Assert
        coVerify(exactly = 0) { camera.startStream(any(), any(), any()) }
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
                override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 {
                    createCount++
                    return camera
                }
            },
        )

        streamingEngine.initializeCamera()
        streamingEngine.initializeCamera()

        assertEquals(1, createCount)
    }

    @Test
    fun `initializeCamera should create one connect checker per max target`() = runTest {
        streamingEngine.initializeCamera()

        assertEquals(StreamingEngine.MAX_STREAM_TARGETS, capturedCheckers.size)
    }

    @Test
    fun `startStream should call startStream on camera if url is valid`() = runTest {
        // Arrange
        streamingEngine.initializeCamera()
        streamingCameraReady()
        val testUrl = "rtmp://test.com/app"

        // Act
        streamingEngine.startStream(testUrl)

        // Assert
        coVerify { camera.startStream(MultiType.RTMP, 0, testUrl) }
    }

    @Test
    fun `startStream should pass an rtmps url through to the camera unchanged`() = runTest {
        // Arrange: RootEncoder erkennt rtmps:// am Scheme und aktiviert TLS selbst,
        // die Engine darf die URL also nicht umschreiben oder verwerfen.
        streamingEngine.initializeCamera()
        streamingCameraReady()
        val testUrl = "rtmps://live.kick.com/app/live_12345_secret"

        // Act
        streamingEngine.startStream(testUrl)

        // Assert
        coVerify { camera.startStream(MultiType.RTMP, 0, testUrl) }
    }

    @Test
    fun `startStream should set Preparing while starting`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream should fail when audio preparation fails`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns false
        every { camera.prepareAudio() } returns false

        streamingEngine.startStream("rtmp://test.com/app")

        assertEquals(StreamingState.Failed("Failed to prepare audio/video"), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { camera.startStream(any(), any(), any()) }
    }

    @Test
    fun `startStream should not restart when already streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns true

        streamingEngine.startStream("rtmp://test.com/app")

        coVerify(exactly = 0) { camera.startStream(any(), any(), any()) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should stop the camera and reset the state to Idle`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream("rtmp://test.com/app")
        streamingEngine.stopStream()

        verify { camera.stopStream(MultiType.RTMP, 0) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should do nothing when not streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns false

        streamingEngine.stopStream()

        verify(exactly = 0) { camera.stopStream(any(), any()) }
    }

    @Test
    fun `connect checker callbacks should update the target and streaming state`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()
        streamingEngine.startStream("rtmp://test.com/app")
        val checker = capturedCheckers[0]

        checker.onConnectionStarted("rtmp://test.com/app")
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.PREPARING, streamingEngine.targetStates.value[0].status)

        checker.onConnectionSuccess()
        assertEquals(StreamingState.Streaming, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.STREAMING, streamingEngine.targetStates.value[0].status)

        checker.onDisconnect()
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.IDLE, streamingEngine.targetStates.value[0].status)

        checker.onConnectionFailed("boom")
        assertEquals(StreamingState.Failed("boom"), streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.FAILED, streamingEngine.targetStates.value[0].status)
        assertEquals("boom", streamingEngine.targetStates.value[0].failureReason)
        verify { camera.stopStream(MultiType.RTMP, 0) }

        checker.onAuthError()
        assertEquals(StreamingState.Failed("RTMP Auth Error"), streamingEngine.streamingState.value)
    }

    @Test
    fun `targetStates is empty before the first start`() = runTest {
        streamingEngine.initializeCamera()

        assertEquals(0, streamingEngine.targetStates.value.size)
    }

    @Test
    fun `startStream with two urls starts both targets`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(listOf("rtmp://a.example/app", "rtmp://b.example/app"))

        coVerify { camera.startStream(MultiType.RTMP, 0, "rtmp://a.example/app") }
        coVerify { camera.startStream(MultiType.RTMP, 1, "rtmp://b.example/app") }
        assertEquals(2, streamingEngine.targetStates.value.size)
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream filters blank urls`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(listOf("   ", "rtmp://b.example/app"))

        verify(exactly = 1) { camera.startStream(any(), any(), any()) }
        coVerify { camera.startStream(MultiType.RTMP, 0, "rtmp://b.example/app") }
        assertEquals(1, streamingEngine.targetStates.value.size)
    }

    @Test
    fun `startStream caps the number of targets at the max`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(
            listOf("rtmp://a.example/app", "rtmp://b.example/app", "rtmp://c.example/app"),
        )

        verify(exactly = 2) { camera.startStream(any(), any(), any()) }
        assertEquals(StreamingEngine.MAX_STREAM_TARGETS, streamingEngine.targetStates.value.size)
    }

    @Test
    fun `failure of one target leaves the other streaming`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()
        streamingEngine.startStream(listOf("rtmp://a.example/app", "rtmp://b.example/app"))

        capturedCheckers[0].onConnectionSuccess()
        capturedCheckers[1].onConnectionFailed("boom")

        assertEquals(StreamingState.Streaming, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.STREAMING, streamingEngine.targetStates.value[0].status)
        assertEquals(StreamTargetStatus.FAILED, streamingEngine.targetStates.value[1].status)
        verify { camera.stopStream(MultiType.RTMP, 1) }
        verify(exactly = 0) { camera.stopStream(MultiType.RTMP, 0) }
    }

    @Test
    fun `stopStream stops all targets`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()
        streamingEngine.startStream(listOf("rtmp://a.example/app", "rtmp://b.example/app"))

        streamingEngine.stopStream()

        verify { camera.stopStream(MultiType.RTMP, 0) }
        verify { camera.stopStream(MultiType.RTMP, 1) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
        assertNull(streamingEngine.targetStates.value[0].failureReason)
    }

    // --- Preview (GL-freier Pfad): attach/detach unabhängig vom Encoder ---

    @Test
    fun `attachPreview is deferred until the gl pipeline runs and attaches on start`() = runTest {
        streamingEngine.initializeCamera()
        var glRunning = false
        every { glStreamInterface.isRunning } answers { glRunning }
        every { camera.isStreaming } returns false
        every { camera.prepareAudio() } returns true
        // prepareVideo startet die GL-Pipeline (real: prepareGlView -> glInterface.start())
        every { camera.prepareVideo() } answers { glRunning = true; true }
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
        verify(exactly = 0) { camera.stopStream(any(), any()) }
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
        streamingCameraReady()

        streamingEngine.startStream("rtmp://test.com/app")

        // Ohne gemerkte Surface wird nichts angehängt — der Stream läuft trotzdem.
        verify(exactly = 0) { glStreamInterface.attachPreview(any()) }
        coVerify { camera.startStream(MultiType.RTMP, 0, "rtmp://test.com/app") }
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
        every { camera.disableAutoFocus() } returns true

        val result = streamingEngine.toggleFocusLock()

        assertEquals(true, result)
        assertEquals(FocusMode.LOCKED_INFINITY, streamingEngine.focusMode.value)
        verify { camera.disableAutoFocus() }
        verify { camera.setFocusDistance(CameraFocusController.FOCUS_DISTANCE_INFINITY) }
    }

    @Test
    fun `toggleFocusLock unlocks and re-enables autofocus`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.disableAutoFocus() } returns true
        streamingEngine.toggleFocusLock()

        every { camera.enableAutoFocus() } returns true
        val result = streamingEngine.toggleFocusLock()

        assertEquals(true, result)
        assertEquals(FocusMode.AUTO, streamingEngine.focusMode.value)
        verify { camera.enableAutoFocus() }
    }

    @Test
    fun `toggleFocusLock keeps the mode and state when the camera rejects the lock`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.disableAutoFocus() } returns false

        val result = streamingEngine.toggleFocusLock()

        assertEquals(false, result)
        assertEquals(FocusMode.AUTO, streamingEngine.focusMode.value)
        verify(exactly = 0) { camera.setFocusDistance(any()) }
    }

    // --- Tap-to-Focus, Pinch-Zoom, Stabilisierung ---

    private fun zoomRange(lower: Float, upper: Float): android.util.Range<Float> =
        mockk<android.util.Range<Float>>(relaxed = true).also {
            every { it.lower } returns lower
            every { it.upper } returns upper
        }

    @Test
    fun `zoomBy multiplies the current zoom and clamps to the range`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.zoom } returns 2f
        every { camera.zoomRange } returns zoomRange(1f, 8f)

        streamingEngine.zoomBy(2f)

        verify { camera.setZoom(4f) }
    }

    @Test
    fun `zoomBy caps at the maximum zoom`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.zoom } returns 6f
        every { camera.zoomRange } returns zoomRange(1f, 8f)

        streamingEngine.zoomBy(2f)

        verify { camera.setZoom(8f) }
    }

    @Test
    fun `zoomBy does nothing before the camera is initialized`() = runTest {
        streamingEngine.zoomBy(2f)

        verify(exactly = 0) { camera.setZoom(any<Float>()) }
    }

    @Test
    fun `resetZoom sets the zoom back to 1`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.zoomRange } returns zoomRange(1f, 8f)

        streamingEngine.resetZoom()

        verify { camera.setZoom(ZoomCalculator.MIN_ZOOM) }
    }

    @Test
    fun `tapToFocus delegates to the camera`() = runTest {
        streamingEngine.initializeCamera()
        val view: View = mockk()
        val event: MotionEvent = mockk()

        streamingEngine.tapToFocus(view, event)

        verify { camera.tapToFocus(view, event) }
    }

    @Test
    fun `tapToFocus does nothing before the camera is initialized`() = runTest {
        val view: View = mockk()
        val event: MotionEvent = mockk()

        streamingEngine.tapToFocus(view, event)

        verify(exactly = 0) { camera.tapToFocus(any(), any()) }
    }

    @Test
    fun `toggleStabilization returns false before the camera is initialized`() = runTest {
        val result = streamingEngine.toggleStabilization()

        assertEquals(false, result)
        assertEquals(false, streamingEngine.stabilizationEnabled.value)
    }

    @Test
    fun `toggleStabilization enables stabilization and updates the state`() = runTest {
        every { camera.isVideoStabilizationEnabled } returns false
        every { camera.isOpticalVideoStabilizationEnabled } returns false
        every { camera.opticalZooms } returns emptyArray()
        every { camera.enableVideoStabilization() } returns true
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleStabilization()

        assertEquals(true, result)
        assertEquals(true, streamingEngine.stabilizationEnabled.value)
        verify { camera.enableVideoStabilization() }
    }

    @Test
    fun `toggleStabilization disables an enabled stabilization`() = runTest {
        every { camera.isVideoStabilizationEnabled } returns true
        every { camera.isOpticalVideoStabilizationEnabled } returns false
        every { camera.disableVideoStabilization() } just runs
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleStabilization()

        assertEquals(true, result)
        assertEquals(false, streamingEngine.stabilizationEnabled.value)
        verify { camera.disableVideoStabilization() }
    }

    @Test
    fun `toggleStabilization keeps the state when the camera rejects the change`() = runTest {
        every { camera.isVideoStabilizationEnabled } returns false
        every { camera.isOpticalVideoStabilizationEnabled } returns false
        every { camera.opticalZooms } returns emptyArray()
        every { camera.enableVideoStabilization() } returns false
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleStabilization()

        assertEquals(false, result)
        assertEquals(false, streamingEngine.stabilizationEnabled.value)
    }
}
