package com.vivid.feature.streaming

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.common.VideoCodec
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import com.pedro.library.util.FpsListener
import com.pedro.library.view.GlStreamInterface
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.source.DisplayFactory
import com.vivid.feature.streaming.source.PlayerFactory
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.source.VideoSourceRegistry
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class StreamingEngineTest {

    private lateinit var camera: MultiCamera2
    private lateinit var glStreamInterface: GlStreamInterface
    private lateinit var display: MultiDisplay
    private lateinit var player: MultiFromFile
    private lateinit var cameraFactory: CameraFactory
    private lateinit var displayFactory: DisplayFactory
    private lateinit var playerFactory: PlayerFactory
    private lateinit var streamingEngine: StreamingEngine
    private lateinit var context: Context
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
        display = mockk(relaxed = true)
        displayFactory = object : DisplayFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiDisplay = display
        }
        player = mockk(relaxed = true)
        playerFactory = object : PlayerFactory {
            override fun create(connectCheckers: List<ConnectChecker>): MultiFromFile = player
        }
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.WINDOW_SERVICE) } returns mockk<android.view.WindowManager>(relaxed = true)
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_DENIED
        streamingEngine = StreamingEngine(
            context,
            cameraFactory,
            displayFactory,
            playerFactory,
            VideoSourceRegistry(),
        )
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.captureCapabilities = { CameraCaptureCapabilities { _, _, _ -> true } }
        streamingEngine.encoderCapabilities = object : EncoderCapabilities {
            override fun supports(mime: String, width: Int, height: Int, fps: Int) = true
        }
    }

    private fun streamingCameraReady() {
        every { camera.isStreaming } returns false
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        every { camera.prepareVideo(any(), any(), any(), any(), any(), any()) } returns true
    }

    @Test
    fun `legacy startStream ohne Encoder-Konfiguration ruft prepareVideo ohne Argumente`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.startStream(LIVE_URL)

        verify(exactly = 1) { camera.prepareVideo() }
        verify(exactly = 0) { camera.prepareVideo(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { camera.setVideoCodec(any()) }
    }

    @Test
    fun `startStream mit Encoder-Konfiguration setzt Codec und Preset`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H265, EncoderPreset.S_4K60, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.startStream(LIVE_URL)

        verify(exactly = 1) { camera.setVideoCodec(VideoCodec.H265) }
        verify(exactly = 1) {
            camera.prepareVideo(3840, 2160, 60, 24_000_000, 2, 0)
        }
    }

    @Test
    fun `startStream mit Preset ruft prepareVideo ohne Argumente nicht mehr auf`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.startStream(LIVE_URL)

        verify(exactly = 0) { camera.prepareVideo() }
        assertEquals(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, fallbackApplied = false),
            streamingEngine.activeEncoder.value,
        )
    }

    @ParameterizedTest
    @EnumSource(EncoderPreset::class)
    fun `all presets pass bits per second to RootEncoder`(preset: EncoderPreset) = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, preset, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.startStream(LIVE_URL)

        verify(exactly = 1) {
            camera.prepareVideo(preset.width, preset.height, preset.fps, preset.videoBitrateKbps * 1_000, 2, 0)
        }
        verify(exactly = 1) { camera.startStream(MultiType.RTMP, 0, LIVE_URL) }
        assertEquals(preset.videoBitrateKbps, streamingEngine.activeEncoder.value?.preset?.videoBitrateKbps)
    }

    @Test
    fun `camera fallback prepares actual profile and adaptive bitrate uses its bitrate`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.captureCapabilities = { CameraCaptureCapabilities { _, _, fps -> fps == 30 } }
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD60, false), true,
        )
        streamingEngine.configureAdaptiveBitrate(true)
        var now = 0L
        streamingEngine.timeSource = { now }
        streamingEngine.startStream(LIVE_URL)

        verify { camera.prepareVideo(1920, 1080, 30, 6_000_000, 2, 0) }
        assertEquals(EncoderPreset.FHD30, streamingEngine.activeEncoder.value?.preset)
        repeat(3) {
            now += 2_500
            capturedCheckers[0].onNewBitrate(2_000_000)
        }
        verify(exactly = 1) { camera.setVideoBitrateOnFly(4_200_000) }
    }

    @Test
    fun `unsupported camera profile fails before starting RTMP and publishes no applied profile`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.captureCapabilities = { CameraCaptureCapabilities { _, _, _ -> false } }
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD60, false), true,
        )
        streamingEngine.startStream(LIVE_URL)

        verify(exactly = 0) { camera.startStream(any(), any(), any()) }
        assertTrue(streamingEngine.streamingState.value is StreamingState.Failed)
        assertNull(streamingEngine.activeEncoder.value)
    }

    @Test
    fun `failed video preparation does not claim applied parameters`() = runTest {
        streamingCameraReady()
        every { camera.prepareVideo(any(), any(), any(), any(), any(), any()) } returns false
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD60, false), true,
        )
        streamingEngine.startStream(LIVE_URL)
        assertNull(streamingEngine.activeEncoder.value)
    }

    @Test
    fun `measured fps comes from encoded frames and does not report the requested rate`() = runTest {
        streamingCameraReady()
        val callback = slot<FpsListener.Callback>()
        every { camera.setFpsListener(capture(callback)) } just runs
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), true,
        )
        streamingEngine.startStream(LIVE_URL)
        assertNull(streamingEngine.measuredEncoderFps.value)
        callback.captured.onFps(14)
        assertEquals(14, streamingEngine.measuredEncoderFps.value)
        assertEquals(30, streamingEngine.activeEncoder.value?.preset?.fps)
    }

    @Test
    fun `idle selected lens is transferred before checking capture capabilities`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        var selectedId = "0"
        every { camera.currentCameraId } answers { selectedId }
        every { camera.switchCamera(any<String>()) } answers { selectedId = firstArg() }
        val idle = mockk<Camera2ApiManager>(relaxed = true)
        every { idle.getCurrentCameraId() } returns "1"
        streamingEngine.idlePreviewFactory = { idle }
        streamingEngine.attachPreview(mockk(relaxed = true), 640, 480)
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("test.encoder", EncoderBitrateMode.CBR, true, 6_000)
        }
        streamingEngine.captureCapabilities = { cam ->
            assertEquals("1", cam.currentCameraId)
            CameraCaptureCapabilities { _, _, fps -> fps == 30 }
        }
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD60, false), true,
        )
        streamingEngine.startStream(LIVE_URL)
        verify { camera.switchCamera("1") }
        assertEquals(EncoderPreset.FHD30, streamingEngine.activeEncoder.value?.preset)
    }

    @Test
    fun `VBR fallback is published for the actual encoder`() = runTest {
        streamingCameraReady()
        streamingEngine.readBitrateDiagnostics = {
            EncoderBitrateDiagnostics("vbr.only.encoder", EncoderBitrateMode.VBR, false, 6_000)
        }
        streamingEngine.initializeCamera()
        streamingEngine.startStream(LIVE_URL)
        assertEquals(EncoderBitrateMode.VBR, streamingEngine.bitrateDiagnostics.value?.mode)
        assertFalse(streamingEngine.bitrateDiagnostics.value!!.cbrSupported)
        assertEquals("vbr.only.encoder", streamingEngine.bitrateDiagnostics.value?.encoderName)
    }

    @Test
    fun `unverifiable encoder mode fails preparation before network start`() = runTest {
        streamingCameraReady()
        streamingEngine.readBitrateDiagnostics = { error("CBR supported but not configured") }
        streamingEngine.initializeCamera()
        streamingEngine.startStream(LIVE_URL)
        verify(exactly = 0) { camera.startStream(any(), any(), any()) }
        assertNull(streamingEngine.bitrateDiagnostics.value)
    }

    enum class StartRoute { LOCAL, REMOTE }

    @ParameterizedTest
    @EnumSource(StartRoute::class)
    fun `both start routes prepare actual encoder and enable adaptive bitrate`(route: StartRoute) = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        val capabilities = object : EncoderCapabilities {
            override fun supports(mime: String, width: Int, height: Int, fps: Int) =
                mime == "video/avc" && width == 1920 && height == 1080 && fps == 30
        }
        streamingEngine.encoderCapabilities = capabilities
        val repository = mockk<com.vivid.core.data.SettingsRepository> {
            every { appSettingsFlow } returns kotlinx.coroutines.flow.MutableStateFlow(
                com.vivid.core.data.AppSettings(
                    streamUrl = "rtmp://live.example/app", streamKey = "key-1",
                    videoCodecPreference = VideoCodecPreference.H265, encoderPreset = EncoderPreset.S_4K60,
                    adaptiveBitrateEnabled = true,
                ),
            )
        }
        val launcher = mockk<StreamingServiceLauncher>(relaxed = true)
        every { launcher.startStreaming(any()) } answers {
            streamingEngine.startStream(firstArg<List<String>>())
        }
        val coordinator = StreamStartCoordinator(repository, streamingEngine, launcher, capabilities)
        var now = 0L
        streamingEngine.timeSource = { now }
        if (route == StartRoute.REMOTE) {
            StreamingEngineStreamControl(streamingEngine, coordinator, launcher, backgroundScope).start()
        } else {
            coordinator.start()
        }
        verify(exactly = 1) { camera.setVideoCodec(VideoCodec.H264) }
        verify(exactly = 1) { camera.prepareVideo(1920, 1080, 30, 6_000_000, 2, 0) }
        verify(exactly = 0) { camera.prepareVideo() }
        assertEquals(EncoderPreset.FHD30, streamingEngine.activeEncoder.value?.preset)
        repeat(3) {
            now += 2_500
            capturedCheckers[0].onNewBitrate(2_000_000)
        }
        verify(exactly = 1) { camera.setVideoBitrateOnFly(4_200_000) }
        assertEquals(4_200, streamingEngine.bitrateDiagnostics.value?.targetKbps)
    }

    // --- Adaptive Bitrate (v0.6.0) ------------------------------------------

    @Test
    fun `adaptive Bitrate aus - onNewBitrate aendert die Encoder-Bitrate nicht`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.startStream(LIVE_URL)

        capturedCheckers[0].onNewBitrate(2_000_000)

        verify(exactly = 0) { camera.setVideoBitrateOnFly(any()) }
    }

    @Test
    fun `adaptive Bitrate an - Sättigung senkt die Zielbitrate on-the-fly`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.configureAdaptiveBitrate(true)
        var fakeTime = 0L
        streamingEngine.timeSource = { fakeTime }
        streamingEngine.startStream(LIVE_URL)

        // 3 Low-Samples (je 2,5 s auseinander) → 6000 * 0.7 = 4200.
        // Startzeit 2 s: der startStream-Reset setzt lastSample auf 0,
        // das erste Sample muss das 2-s-Intervall also erst clearing.
        fakeTime = 2_000
        capturedCheckers[0].onNewBitrate(2_000_000)
        fakeTime = 4_500
        capturedCheckers[0].onNewBitrate(2_000_000)
        fakeTime = 7_000
        capturedCheckers[0].onNewBitrate(2_000_000)

        verify(exactly = 1) { camera.setVideoBitrateOnFly(4_200_000) }
    }

    @Test
    fun `adaptive Bitrate respektiert das Sample-Intervall`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.configureAdaptiveBitrate(true)
        var fakeTime = 0L
        streamingEngine.timeSource = { fakeTime }
        streamingEngine.startStream(LIVE_URL)

        // 3 Low-Samples OHNE Zeitabstand: nur das erste zählt (Rate-Limit).
        fakeTime = 2_000
        capturedCheckers[0].onNewBitrate(2_000_000)
        capturedCheckers[0].onNewBitrate(2_000_000)
        capturedCheckers[0].onNewBitrate(2_000_000)

        verify(exactly = 0) { camera.setVideoBitrateOnFly(any()) }
    }

    @Test
    fun `onNewBitrate publishst die gemessene Bitrate im Ziel-Status`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.startStream(LIVE_URL)

        capturedCheckers[0].onNewBitrate(3_500_999)

        assertEquals(3_500, streamingEngine.targetStates.value[0].bitrateKbps)
    }

    @Test
    fun `adaptive bitrate recovers in kbps and applies bits per second`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.configureEncoder(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, fallbackApplied = false),
            autoFallback = true,
        )
        streamingEngine.configureAdaptiveBitrate(true)
        var fakeTime = 0L
        streamingEngine.timeSource = { fakeTime }
        streamingEngine.startStream(LIVE_URL)

        repeat(3) {
            fakeTime += 2_500
            capturedCheckers[0].onNewBitrate(2_000_000)
        }
        repeat(10) {
            fakeTime += 2_500
            capturedCheckers[0].onNewBitrate(4_200_000)
        }

        verify(exactly = 1) { camera.setVideoBitrateOnFly(4_200_000) }
        verify(exactly = 1) { camera.setVideoBitrateOnFly(4_700_000) }
        assertEquals(4_200, streamingEngine.targetStates.value[0].bitrateKbps)
        assertEquals(4_700, streamingEngine.bitrateDiagnostics.value?.targetKbps)
        assertEquals(EncoderBitrateMode.CBR, streamingEngine.bitrateDiagnostics.value?.mode)
    }

    @Test
    fun `network statistics convert before narrowing to Int and remain per target`() = runTest {
        streamingCameraReady()
        streamingEngine.initializeCamera()
        streamingEngine.startStream(listOf(LIVE_URL, "rtmp://example.com/live/second"))

        capturedCheckers[0].onNewBitrate(3_000_000_000L)
        capturedCheckers[1].onNewBitrate(999L)

        assertEquals(3_000_000, streamingEngine.targetStates.value[0].bitrateKbps)
        assertEquals(0, streamingEngine.targetStates.value[1].bitrateKbps)
        capturedCheckers[1].onNewBitrate(0L)
        assertEquals(0, streamingEngine.targetStates.value[1].bitrateKbps)
    }

    private fun screenCaptureReady() {
        every { display.isStreaming } returns false
        every { display.prepareAudio() } returns true
        every { display.prepareVideo() } returns true
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
            mockk<Context>(relaxed = true),
            object : CameraFactory {
                override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 {
                    createCount++
                    return camera
                }
            },
            displayFactory,
            playerFactory,
            VideoSourceRegistry(),
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
        val testUrl = TEST_URL

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

        streamingEngine.startStream(TEST_URL)

        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream should fail when audio preparation fails`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns false
        every { camera.prepareAudio() } returns false

        streamingEngine.startStream(TEST_URL)

        assertEquals(StreamingState.Failed(PREPARATION_ERROR), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { camera.startStream(any(), any(), any()) }
    }

    @Test
    fun `startStream should not restart when already streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns true

        streamingEngine.startStream(TEST_URL)

        coVerify(exactly = 0) { camera.startStream(any(), any(), any()) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `stopStream should stop the camera and reset the state to Idle`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(TEST_URL)
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
        streamingEngine.startStream(TEST_URL)
        val checker = capturedCheckers[0]

        checker.onConnectionStarted(TEST_URL)
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.PREPARING, streamingEngine.targetStates.value[0].status)

        checker.onConnectionSuccess()
        assertEquals(StreamingState.Streaming, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.STREAMING, streamingEngine.targetStates.value[0].status)

        checker.onDisconnect()
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.IDLE, streamingEngine.targetStates.value[0].status)

        checker.onConnectionFailed(CAMERA_ERROR)
        assertEquals(StreamingState.Failed(CAMERA_ERROR), streamingEngine.streamingState.value)
        assertEquals(StreamTargetStatus.FAILED, streamingEngine.targetStates.value[0].status)
        assertEquals(CAMERA_ERROR, streamingEngine.targetStates.value[0].failureReason)
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

        streamingEngine.startStream(listOf(PRIMARY_URL, SECONDARY_URL))

        coVerify { camera.startStream(MultiType.RTMP, 0, PRIMARY_URL) }
        coVerify { camera.startStream(MultiType.RTMP, 1, SECONDARY_URL) }
        assertEquals(2, streamingEngine.targetStates.value.size)
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream filters blank urls`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(listOf("   ", SECONDARY_URL))

        verify(exactly = 1) { camera.startStream(any(), any(), any()) }
        coVerify { camera.startStream(MultiType.RTMP, 0, SECONDARY_URL) }
        assertEquals(1, streamingEngine.targetStates.value.size)
    }

    @Test
    fun `startStream caps the number of targets at the max`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()

        streamingEngine.startStream(
            listOf(PRIMARY_URL, SECONDARY_URL, "rtmp://c.example/app"),
        )

        verify(exactly = 2) { camera.startStream(any(), any(), any()) }
        assertEquals(StreamingEngine.MAX_STREAM_TARGETS, streamingEngine.targetStates.value.size)
    }

    @Test
    fun `failure of one target leaves the other streaming`() = runTest {
        streamingEngine.initializeCamera()
        streamingCameraReady()
        streamingEngine.startStream(listOf(PRIMARY_URL, SECONDARY_URL))

        capturedCheckers[0].onConnectionSuccess()
        capturedCheckers[1].onConnectionFailed(CAMERA_ERROR)

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
        streamingEngine.startStream(listOf(PRIMARY_URL, SECONDARY_URL))

        streamingEngine.stopStream()

        verify { camera.stopStream(MultiType.RTMP, 0) }
        verify { camera.stopStream(MultiType.RTMP, 1) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
        assertNull(streamingEngine.targetStates.value[0].failureReason)
    }

    // --- S1/S2: Source-Abstraktion (VideoSourceRegistry) ---

    @Test
    fun `activeSourceKind defaults to CAMERA`() = runTest {
        streamingEngine.initializeCamera()

        assertEquals(VideoSourceKind.CAMERA, streamingEngine.activeSourceKind.value)
    }

    @Test
    fun `switchSource accepts CAMERA and updates the active source`() = runTest {
        streamingEngine.initializeCamera()

        val result = streamingEngine.switchSource(VideoSourceKind.CAMERA)

        assertEquals(true, result)
        assertEquals(VideoSourceKind.CAMERA, streamingEngine.activeSourceKind.value)
    }

    @Test
    fun `switchSource accepts SCREEN_CAPTURE in S2 and updates the active source`() = runTest {
        streamingEngine.initializeCamera()

        val result = streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)

        assertEquals(true, result)
        assertEquals(VideoSourceKind.SCREEN_CAPTURE, streamingEngine.activeSourceKind.value)
    }

    @Test
    fun `switchSource accepts VIDEO_PLAYER in S3 and updates the active source`() = runTest {
        streamingEngine.initializeCamera()

        val result = streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)

        assertEquals(true, result)
        assertEquals(VideoSourceKind.VIDEO_PLAYER, streamingEngine.activeSourceKind.value)
    }

    @Test
    fun `setVideoPlayerUri prepares the player and reports success`() = runTest {
        streamingEngine.initializeCamera()
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns true

        val ok = streamingEngine.setVideoPlayerUri(uri)

        assertEquals(true, ok)
        verify { player.prepareVideo(any(), uri) }
        verify { player.prepareAudio(any(), uri) }
    }

    @Test
    fun `setVideoPlayerUri reports failure when prepare fails`() = runTest {
        streamingEngine.initializeCamera()
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns false

        val ok = streamingEngine.setVideoPlayerUri(uri)

        assertEquals(false, ok)
    }

    @Test
    fun `startStream routes to the video player source when active`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns true
        streamingEngine.setVideoPlayerUri(uri)
        every { player.isStreaming } returns false

        streamingEngine.startStream(TEST_URL)

        coVerify { player.startStream(MultiType.RTMP, 0, TEST_URL) }
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream on video player fails without a set video`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)
        // Keine Datei gesetzt -> start() liefert false, kein Start.
        every { player.isStreaming } returns false

        streamingEngine.startStream(TEST_URL)

        assertEquals(StreamingState.Failed(PREPARATION_ERROR), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { player.startStream(any(), any(), any()) }
    }

    @Test
    fun `stopStream stops the video player targets when active`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns true
        streamingEngine.setVideoPlayerUri(uri)
        every { player.isStreaming } returns false

        streamingEngine.startStream(listOf(PRIMARY_URL, SECONDARY_URL))
        streamingEngine.stopStream()

        verify { player.stopStream(MultiType.RTMP, 0) }
        verify { player.stopStream(MultiType.RTMP, 1) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
    }

    @Test
    fun `switchSource SCREEN_CAPTURE exposes the consent intent`() = runTest {
        streamingEngine.initializeCamera()
        val consentIntent: Intent = mockk()
        every { display.sendIntent() } returns consentIntent
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)

        val intent = streamingEngine.createScreenCaptureConsentIntent()

        assertEquals(consentIntent, intent)
    }

    @Test
    fun `onScreenCaptureConsentResult grants consent with RESULT_OK`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)

        val granted = streamingEngine.onScreenCaptureConsentResult(Activity.RESULT_OK, mockk())

        assertEquals(true, granted)
    }

    @Test
    fun `onScreenCaptureConsentResult keeps consent false when denied`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)

        val granted = streamingEngine.onScreenCaptureConsentResult(Activity.RESULT_CANCELED, null)

        assertEquals(false, granted)
    }

    @Test
    fun `startStream routes to the screen capture source when active`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)
        streamingEngine.onScreenCaptureConsentResult(Activity.RESULT_OK, mockk())
        screenCaptureReady()

        streamingEngine.startStream(TEST_URL)

        coVerify { display.startStream(MultiType.RTMP, 0, TEST_URL) }
        assertEquals(StreamingState.Preparing, streamingEngine.streamingState.value)
    }

    @Test
    fun `startStream on screen capture fails without consent`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)
        // Kein Consent erteilt -> prepareAudio/Video werden nicht aufgerufen.
        every { display.isStreaming } returns false

        streamingEngine.startStream(TEST_URL)

        assertEquals(StreamingState.Failed(PREPARATION_ERROR), streamingEngine.streamingState.value)
        coVerify(exactly = 0) { display.startStream(any(), any(), any()) }
    }

    @Test
    fun `stopStream stops the screen capture targets when active`() = runTest {
        streamingEngine.initializeCamera()
        streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)
        streamingEngine.onScreenCaptureConsentResult(Activity.RESULT_OK, mockk())
        screenCaptureReady()

        streamingEngine.startStream(listOf(PRIMARY_URL, SECONDARY_URL))
        streamingEngine.stopStream()

        verify { display.stopStream(MultiType.RTMP, 0) }
        verify { display.stopStream(MultiType.RTMP, 1) }
        assertEquals(StreamingState.Idle, streamingEngine.streamingState.value)
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
        streamingEngine.startStream(TEST_URL)

        verify(exactly = 1) { glStreamInterface.attachPreview(surface) }
        verify(exactly = 1) { glStreamInterface.setPreviewResolution(640, 480) }
    }

    @Test
    fun `attachPreview attaches immediately when the gl pipeline is already running`() = runTest {
        streamingEngine.initializeCamera()
        every { glStreamInterface.isRunning } returns true
        every { camera.isStreaming } returns true
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 1280, 720)

        verify(exactly = 1) { glStreamInterface.attachPreview(surface) }
        verify(exactly = 1) { glStreamInterface.setPreviewResolution(1280, 720) }
        verify(exactly = 1) { glStreamInterface.setPreviewIsPortrait(false) }
    }

    @Test
    fun `preview rotation updates GL orientation without stopping the stream`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isStreaming } returns true
        every { glStreamInterface.isRunning } returns true
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 1080, 1920)
        streamingEngine.attachPreview(surface, 1920, 1080)

        verify(exactly = 1) { glStreamInterface.setPreviewIsPortrait(true) }
        verify(exactly = 1) { glStreamInterface.setPreviewIsPortrait(false) }
        verify(exactly = 0) { camera.stopStream(any(), any()) }
    }

    @Test
    fun `recording preview uses display orientation and crops without letterboxing`() = runTest {
        streamingEngine.initializeCamera()
        every { camera.isRecording } returns true
        every { glStreamInterface.isRunning } returns true
        val surface: Surface = mockk(relaxed = true)
        streamingEngine.attachPreview(surface, 1088, 1088, rotationDegrees = 0)
        verify { glStreamInterface.setPreviewIsPortrait(true) }
        verify { glStreamInterface.setPreviewRotation(0) }
        verify { glStreamInterface.setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.Fill) }

        streamingEngine.attachPreview(surface, 1920, 1080, rotationDegrees = 90)
        verify { glStreamInterface.setPreviewRotation(270) }
        verify { glStreamInterface.setPreviewIsPortrait(false) }
        streamingEngine.attachPreview(surface, 1920, 1080, rotationDegrees = 270)
        verify { glStreamInterface.setPreviewRotation(90) }
        verify(exactly = 0) { camera.stopRecord() }
    }

    @Test
    fun `stopping recording releases GL producer before reopening idle preview`(
        @org.junit.jupiter.api.io.TempDir directory: java.io.File,
    ) = runTest {
        every { context.filesDir } returns directory
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        every { camera.prepareAudio() } returns true
        every { camera.prepareVideo() } returns true
        val idleCamera = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { idleCamera }
        val surface: Surface = mockk(relaxed = true)
        streamingEngine.attachPreview(surface, 1088, 1088, rotationDegrees = 0)
        var recording = false
        every { camera.isRecording } answers { recording }
        every { glStreamInterface.isRunning } answers { recording }
        every { camera.startRecord(any<String>()) } answers { recording = true }
        every { camera.stopRecord() } answers { recording = false }
        assertTrue(streamingEngine.startReplay())
        io.mockk.clearMocks(glStreamInterface, idleCamera, camera, answers = false)
        streamingEngine.stopReplay()
        io.mockk.verifyOrder {
            glStreamInterface.deAttachPreview()
            camera.stopRecord()
            idleCamera.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30)
            idleCamera.openCameraBack()
        }
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
        every { camera.isStreaming } returns true
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
    fun `idle camera preview opens on a surface and closes before streaming`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        streamingCameraReady()
        val idleCamera = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { idleCamera }
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 640, 480)
        verify(exactly = 1) { idleCamera.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30) }
        verify(exactly = 1) { idleCamera.openCameraBack() }

        streamingEngine.startStream(TEST_URL)
        verify(exactly = 1) { idleCamera.closeCamera() }
        verify { camera.startStream(MultiType.RTMP, 0, TEST_URL) }
    }

    @Test
    fun `old surface teardown does not close the replacement camera preview`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        val firstCamera = mockk<Camera2ApiManager>(relaxed = true)
        val secondCamera = mockk<Camera2ApiManager>(relaxed = true)
        var nextCamera = firstCamera
        streamingEngine.idlePreviewFactory = { nextCamera }
        val firstSurface: Surface = mockk(relaxed = true)
        val secondSurface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(firstSurface, 640, 480)
        nextCamera = secondCamera
        streamingEngine.attachPreview(secondSurface, 640, 480)
        streamingEngine.detachPreview(firstSurface)

        verify(exactly = 1) { firstCamera.closeCamera() }
        verify(exactly = 0) { secondCamera.closeCamera() }
    }

    @Test
    fun `current surface teardown closes the idle camera preview`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        val idleCamera = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { idleCamera }
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 1088, 1088)
        streamingEngine.detachPreview(surface)

        verify(exactly = 1) { idleCamera.closeCamera() }
        verify(exactly = 1) { glStreamInterface.deAttachPreview() }
    }

    @Test
    fun `idle preview reopens when the same surface changes orientation`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        val portraitCamera = mockk<Camera2ApiManager>(relaxed = true)
        val landscapeCamera = mockk<Camera2ApiManager>(relaxed = true)
        var nextCamera = portraitCamera
        streamingEngine.idlePreviewFactory = { nextCamera }
        val surface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(surface, 1080, 1920)
        streamingEngine.attachPreview(surface, 1080, 1920)
        nextCamera = landscapeCamera
        streamingEngine.attachPreview(surface, 1920, 1080)

        verify(exactly = 1) { portraitCamera.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30) }
        verify(exactly = 1) { portraitCamera.closeCamera() }
        verify(exactly = 1) { landscapeCamera.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30) }
        verify(exactly = 1) { landscapeCamera.openCameraBack() }
    }

    @Test
    fun `returning from screen capture opens the camera on the new surface`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        val firstCamera = mockk<Camera2ApiManager>(relaxed = true)
        val secondCamera = mockk<Camera2ApiManager>(relaxed = true)
        var nextCamera = firstCamera
        streamingEngine.idlePreviewFactory = { nextCamera }
        val firstSurface: Surface = mockk(relaxed = true)
        val secondSurface: Surface = mockk(relaxed = true)

        streamingEngine.attachPreview(firstSurface, 640, 480)
        assertTrue(streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE))
        verify(exactly = 1) { firstCamera.closeCamera() }

        nextCamera = secondCamera
        assertTrue(streamingEngine.switchSource(VideoSourceKind.CAMERA))
        streamingEngine.attachPreview(secondSurface, 640, 480)
        verify(exactly = 1) { secondCamera.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30) }
        verify(exactly = 1) { secondCamera.openCameraBack() }
    }

    @Test
    fun `idle preview updates upside down rotation without reopening the camera`() = runTest {
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        val idle = mockk<Camera2ApiManager>(relaxed = true)
        streamingEngine.idlePreviewFactory = { idle }
        every { glStreamInterface.isRunning } returns true
        val surface = mockk<Surface>(relaxed = true)
        streamingEngine.attachPreview(surface, 1080, 1920, 0)
        streamingEngine.attachPreview(surface, 1080, 1920, 180)
        verify { glStreamInterface.setPreviewRotation(180) }
        verify(exactly = 1) { idle.prepareCamera(any<android.graphics.SurfaceTexture>(), 1280, 720, 30) }
        verify(exactly = 0) { idle.closeCamera() }
    }

    @Test
    fun `idle menu controls target the open preview camera and refresh stable state flows`() = runTest {
        val exposureFlow = streamingEngine.exposure
        val rangeFlow = streamingEngine.exposureRange
        streamingEngine.initializeCamera()
        every { context.checkSelfPermission(android.Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
        every { camera.currentCameraId } returns ""
        val idle = mockk<Camera2ApiManager>(relaxed = true)
        var torch = false
        var exposure = 0
        var autoExposure = true
        var autoWhiteBalance = true
        every { idle.isLanternEnabled } answers { torch }
        every { idle.enableLantern() } answers { torch = true }
        every { idle.disableLantern() } answers { torch = false }
        every { idle.minExposure } returns -4
        every { idle.maxExposure } returns 4
        every { idle.exposure } answers { exposure }
        every { idle.exposure = any() } answers { exposure = firstArg() }
        every { idle.isAutoExposureEnabled } answers { autoExposure }
        every { idle.enableAutoExposure() } answers { autoExposure = true; true }
        every { idle.disableAutoExposure() } answers { autoExposure = false }
        every { idle.getAutoWhiteBalanceModesAvailable() } returns listOf(2, 1)
        every { idle.isAutoWhiteBalanceEnabled } answers { autoWhiteBalance }
        every { idle.enableAutoWhiteBalance(1) } answers { autoWhiteBalance = true; true }
        every { idle.disableAutoWhiteBalance() } answers { autoWhiteBalance = false }
        every { idle.disableAutoFocus() } returns true
        streamingEngine.idlePreviewFactory = { idle }
        streamingEngine.attachPreview(mockk(relaxed = true), 1080, 1920)
        assertEquals(-4..4, rangeFlow.value)
        assertTrue(streamingEngine.toggleTorch())
        assertTrue(streamingEngine.torchEnabled.value)
        assertTrue(streamingEngine.toggleTorch())
        assertFalse(streamingEngine.torchEnabled.value)
        assertTrue(streamingEngine.setExposure(3))
        assertEquals(3, exposureFlow.value)
        assertTrue(streamingEngine.setAutoExposure(false))
        assertFalse(streamingEngine.autoExposureEnabled.value)
        assertTrue(streamingEngine.setAutoWhiteBalance(false))
        assertFalse(streamingEngine.autoWhiteBalanceEnabled.value)
        assertTrue(streamingEngine.setAutoWhiteBalance(true))
        assertTrue(streamingEngine.toggleFocusLock())
        verify { idle.setFocusDistance(0f) }
        verify(exactly = 0) { camera.enableLantern() }
        verify(exactly = 0) { camera.setExposure(3) }
    }

    @Test
    fun `startStream without a preview surface streams view-less`() = runTest {
        streamingEngine.initializeCamera()
        every { glStreamInterface.isRunning } returns true
        streamingCameraReady()

        streamingEngine.startStream(TEST_URL)

        // Ohne gemerkte Surface wird nichts angehängt — der Stream läuft trotzdem.
        verify(exactly = 0) { glStreamInterface.attachPreview(any()) }
        coVerify { camera.startStream(MultiType.RTMP, 0, TEST_URL) }
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
        var digital = true
        every { camera.isVideoStabilizationEnabled } answers { digital }
        every { camera.isOpticalVideoStabilizationEnabled } returns false
        every { camera.disableVideoStabilization() } answers { digital = false }
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

    // --- Taschenlampe (Torch/Lantern) ---

    @Test
    fun `toggleTorch returns false before the camera is initialized`() = runTest {
        val result = streamingEngine.toggleTorch()

        assertEquals(false, result)
        assertEquals(false, streamingEngine.torchEnabled.value)
    }

    @Test
    fun `toggleTorch enables the torch and updates the state`() = runTest {
        every { camera.isLanternSupported } returns true
        var torch = false
        every { camera.isLanternEnabled } answers { torch }
        every { camera.enableLantern() } answers { torch = true }
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleTorch()

        assertEquals(true, result)
        assertTrue(streamingEngine.torchEnabled.value)
        verify { camera.enableLantern() }
    }

    @Test
    fun `toggleTorch disables an enabled torch`() = runTest {
        every { camera.isLanternSupported } returns true
        var torch = true
        every { camera.isLanternEnabled } answers { torch }
        every { camera.disableLantern() } answers { torch = false }
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleTorch()

        assertEquals(true, result)
        verify { camera.disableLantern() }
    }

    @Test
    fun `toggleTorch keeps state false when enableLantern throws`() = runTest {
        every { camera.isLanternSupported } returns true
        every { camera.isLanternEnabled } returns false
        every { camera.enableLantern() } throws RuntimeException("no flash")
        streamingEngine.initializeCamera()

        val result = streamingEngine.toggleTorch()

        assertEquals(false, result)
        assertEquals(false, streamingEngine.torchEnabled.value)
    }

    // --- Belichtung + Weißabgleich (Streaming-Screen-Regler) ---

    @Test
    fun `exposure range is synced from the camera after initialization`() = runTest {
        every { camera.minExposure } returns -3
        every { camera.maxExposure } returns 3
        streamingEngine.initializeCamera()

        assertEquals(-3..3, streamingEngine.exposureRange.value)
    }

    @Test
    fun `exposure range stays null when the camera reports an invalid range`() = runTest {
        every { camera.minExposure } returns 5
        every { camera.maxExposure } returns -5
        streamingEngine.initializeCamera()

        assertNull(streamingEngine.exposureRange.value)
    }

    @Test
    fun `setExposure forwards to the camera and updates the state`() = runTest {
        every { camera.minExposure } returns -3
        every { camera.maxExposure } returns 3
        streamingEngine.initializeCamera()

        every { camera.getExposure() } returns 2
        val ok = streamingEngine.setExposure(2)

        assertTrue(ok)
        assertEquals(2, streamingEngine.exposure.value)
        verify { camera.setExposure(2) }
    }

    @Test
    fun `setAutoExposure toggles the state`() = runTest {
        var auto = true
        every { camera.isAutoExposureEnabled } answers { auto }
        every { camera.disableAutoExposure() } answers { auto = false }
        every { camera.enableAutoExposure() } answers { auto = true; true }
        streamingEngine.initializeCamera()

        assertTrue(streamingEngine.setAutoExposure(false))
        assertFalse(streamingEngine.autoExposureEnabled.value)
        assertTrue(streamingEngine.setAutoExposure(true))
        assertTrue(streamingEngine.autoExposureEnabled.value)
    }

    @Test
    fun `setAutoWhiteBalance toggles the state when the camera supports it`() = runTest {
        every { camera.autoWhiteBalanceModesAvailable } returns listOf(1, 2)
        var auto = true
        every { camera.isAutoWhiteBalanceEnabled } answers { auto }
        every { camera.disableAutoWhiteBalance() } answers { auto = false }
        every { camera.enableAutoWhiteBalance(any()) } answers { auto = true; true }
        streamingEngine.initializeCamera()

        assertTrue(streamingEngine.hasWhiteBalanceControl())
        assertTrue(streamingEngine.setAutoWhiteBalance(false))
        assertFalse(streamingEngine.autoWhiteBalanceEnabled.value)
        assertTrue(streamingEngine.setAutoWhiteBalance(true))
        assertTrue(streamingEngine.autoWhiteBalanceEnabled.value)
    }

    @Test
    fun `white balance control is unavailable without camera modes`() = runTest {
        every { camera.autoWhiteBalanceModesAvailable } returns emptyList()
        every { camera.isAutoWhiteBalanceEnabled } returns true
        streamingEngine.initializeCamera()

        // UI-Gate: Ohne verfügbare Auto-Modi blendet der Screen den WB-Toggle aus.
        assertFalse(streamingEngine.hasWhiteBalanceControl())
    }
    private companion object {
        private const val LIVE_URL = "rtmp://live/app"
        private const val TEST_URL = "rtmp://test.com/app"
        private const val PREPARATION_ERROR = "Failed to prepare audio/video"
        private const val CAMERA_ERROR = "boom"
        private const val PRIMARY_URL = "rtmp://a.example/app"
        private const val SECONDARY_URL = "rtmp://b.example/app"
    }

}
