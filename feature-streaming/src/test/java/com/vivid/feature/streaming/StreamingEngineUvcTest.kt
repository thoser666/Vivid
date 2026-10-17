package com.vivid.feature.streaming

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.source.VideoSourceRegistry
import com.vivid.feature.streaming.source.uvc.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingEngineUvcTest {
    @TempDir lateinit var directory: File
    private val multi = mockk<MultiStream>(relaxed = true)
    private val manager = mockk<UvcConnectionManager>(relaxed = true)
    private val sourceFactory = mockk<UvcStreamFactory>()
    private val camera = mockk<com.pedro.library.multiple.MultiCamera2>(relaxed = true)
    private val cameraGl = mockk<GlStreamInterface>(relaxed = true)
    private val cameraFactory = mockk<CameraFactory>()
    private val device = UvcDeviceDescriptor("session", 1, 2, "Capture")
    private val mode = UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG)
    private val connection = MutableStateFlow<UvcConnectionState>(UvcConnectionState.Ready(device, mode))
    private lateinit var engine: StreamingEngine
    private lateinit var checkers: List<ConnectChecker>
    private var streaming = false
    private var recording = false

    @BeforeEach fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns directory
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { manager.connectionState } returns connection
        every { manager.isCaptureReady } answers { connection.value is UvcConnectionState.Ready || connection.value is UvcConnectionState.Capturing }
        every { sourceFactory.create(any()) } answers { checkers = firstArg(); multi }
        every { cameraFactory.create(any()) } returns camera
        every { camera.glInterface } returns cameraGl
        every { cameraGl.surfaceTexture } returns mockk<SurfaceTexture>(relaxed = true)
        every { multi.isStreaming } answers { streaming }
        every { multi.isRecording } answers { recording }
        every { multi.prepareAudio(any(), any(), any(), any(), any()) } returns true
        every { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true
        every { multi.startStream(any(), any(), any()) } answers { streaming = true }
        every { multi.stopStream() } answers { streaming = false; true }
        every { multi.startRecord(any<String>(), any(), any()) } answers { recording = true }
        every { multi.stopRecord() } answers { recording = false; true }
        engine = StreamingEngine(context, cameraFactory, mockk(relaxed = true), mockk(relaxed = true),
            VideoSourceRegistry(), sourceFactory, manager)
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test fun `switching away from idle USB releases the native backend and USB session`() {
        assertTrue(engine.switchSource(VideoSourceKind.USB_UVC))
        assertTrue(engine.switchSource(VideoSourceKind.CAMERA))
        verify(exactly = 1) { multi.release() }
        verify(exactly = 1) { manager.disconnect() }
        assertEquals(VideoSourceKind.CAMERA, engine.activeSourceKind.value)
    }

    @Test fun `unavailable USB selection preserves active camera and does not build a backend`() {
        connection.value = UvcConnectionState.Disconnected
        assertFalse(engine.switchSource(VideoSourceKind.USB_UVC))
        assertEquals(VideoSourceKind.CAMERA, engine.activeSourceKind.value)
        verify(exactly = 0) { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `phone preview stays paused through UVC negotiation and surface recomposition`() {
        engine.initializeCamera()
        val phonePreview = mockk<Camera2ApiManager>(relaxed = true)
        engine.idlePreviewFactory = { phonePreview }
        val surface = mockk<Surface>(relaxed = true)
        engine.attachPreview(surface, 720, 1280)
        verify(exactly = 1) { phonePreview.openCameraBack() }

        connection.value = UvcConnectionState.Opening(device)
        engine.connectUsbDevice(device)
        engine.attachPreview(surface, 720, 1280)

        verify(exactly = 1) { phonePreview.closeCamera() }
        verify(exactly = 1) { phonePreview.openCameraBack() }

        connection.value = UvcConnectionState.Error(UvcFailure.OpenFailed, device)
    }

    @Test fun `one factory creates two checker clients and shared target failure preserves healthy output`() {
        assertTrue(engine.switchSource(VideoSourceKind.USB_UVC))
        assertTrue(engine.switchSource(VideoSourceKind.USB_UVC))
        engine.startStream(listOf("rtmp://one/live", "rtmps://two/live"))
        assertEquals(2, checkers.size)
        verify(exactly = 1) { sourceFactory.create(any()) }
        verify(exactly = 1) { multi.startStream(MultiType.RTMP, 0, "rtmp://one/live") }
        verify(exactly = 1) { multi.startStream(MultiType.RTMP, 1, "rtmps://two/live") }
        checkers[1].onConnectionSuccess()
        assertEquals(StreamTargetStatus.PREPARING, engine.targetStates.value[1].status)
        connection.value = UvcConnectionState.Capturing(device, mode)
        checkers[0].onConnectionFailed("offline")
        assertEquals(StreamTargetStatus.FAILED, engine.targetStates.value[0].status)
        assertEquals(StreamingState.Streaming, engine.streamingState.value)
        verify(exactly = 0) { multi.stopStream() }
        checkers[1].onConnectionFailed("offline")
        verify(exactly = 1) { multi.stopStream() }
    }

    @Test fun `offline recording can add RTMP without preparing or opening capture again`() {
        engine.switchSource(VideoSourceKind.USB_UVC)
        assertTrue(engine.startReplay(includeAudio = false))
        clearMocks(multi, answers = false)
        engine.startStream(listOf("rtmp://one/live"))
        verify(exactly = 0) { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { multi.startStream(MultiType.RTMP, 0, "rtmp://one/live") }
        assertTrue(recording)
        engine.stopStream()
        assertTrue(recording)
        verify(exactly = 0) { multi.stopRecord() }
        engine.stopReplay()
        assertFalse(recording)
    }

    @Test fun `recording during RTMP retains controller and encoder ownership`() {
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startStream(listOf("rtmp://one/live", "rtmp://two/live"))
        clearMocks(multi, answers = false)
        assertTrue(engine.startReplay())
        verify(exactly = 0) { multi.setRecordController(any()) }
        verify(exactly = 0) { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        engine.stopReplay()
        assertTrue(streaming)
        verify(exactly = 0) { multi.stopStream() }
    }

    @Test fun `detach finalizes recorder and stops destinations while preserving error identity`() {
        val lost = slot<() -> Unit>()
        every { manager.onCaptureLost = capture(lost) } just Runs
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startStream(listOf("rtmp://one/live"))
        engine.startReplay()
        connection.value = UvcConnectionState.Error(UvcFailure.DetachedDuringCapture)
        lost.captured()
        assertFalse(streaming)
        assertFalse(recording)
        assertTrue(engine.usbRecordingInterrupted.value)
        assertEquals(VideoSourceKind.USB_UVC, engine.activeSourceKind.value)
        verify(exactly = 1) { multi.stopRecord() }
        engine.stopReplay()
        verify(exactly = 1) { multi.stopRecord() }
    }

    @Test fun `USB source and mode changes are blocked while recording and camera controls do nothing`() {
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startReplay()
        assertFalse(engine.switchSource(VideoSourceKind.CAMERA))
        engine.setUsbCaptureMode(mode)
        verify(exactly = 0) { manager.setMode(any()) }
        assertFalse(engine.toggleTorch())
        assertFalse(engine.toggleFocusLock())
        assertFalse(engine.toggleStabilization())
    }

    @Test fun `preview detachment never ends streaming or recording`() {
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startStream(listOf("rtmp://one/live"))
        engine.startReplay()
        engine.detachPreview()
        verify(exactly = 0) { multi.stopStream() }
        verify(exactly = 0) { multi.stopRecord() }
        assertTrue(streaming && recording)
    }
    @Test fun `callbacks from released USB backend cannot mutate the next session`() {
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startStream(listOf("rtmp://old/live"))
        val old = checkers
        engine.disconnectUsbDevice()
        connection.value = UvcConnectionState.Ready(device, mode)
        engine.switchSource(VideoSourceKind.USB_UVC)
        engine.startStream(listOf("rtmp://new/live"))
        old[0].onConnectionFailed("late callback")
        assertEquals(StreamTargetStatus.PREPARING, engine.targetStates.value.single().status)
        assertEquals("rtmp://new/live", engine.targetStates.value.single().url)
    }

}
