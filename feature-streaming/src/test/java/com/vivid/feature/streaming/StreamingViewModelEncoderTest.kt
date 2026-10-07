package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
import com.vivid.core.data.ZoneRepository
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.scene.AutoSceneSwitcher
import com.vivid.feature.streaming.scene.SceneController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Encoder-Preset-Auflösung im [StreamingViewModel] (v0.6.0 „4K/60fps + HEVC“):
 * der Go-Live löst die gespeicherte Wunsch-Kombi gegen die Geräte-Fähigkeiten
 * auf (HEVC-First bei AUTO, Preset-Abstufung, Strict-Modus) und konfiguriert
 * die Engine vor dem Start. Die Fähigkeiten kommen aus einem Fake — kein
 * Android-Framework nötig.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingViewModelEncoderTest {

    private data class Cap(val mime: String, val width: Int, val height: Int, val fps: Int)

    private class FakeCaps(private val supported: Set<Cap>) : EncoderCapabilities {
        override fun supports(mime: String, width: Int, height: Int, fps: Int): Boolean =
            Cap(mime, width, height, fps) in supported
    }

    private val engine = mockk<StreamingEngine>(relaxed = true) {
        every { streamingState } returns MutableStateFlow(StreamingState.Idle)
    }
    private val launcher = mockk<StreamingServiceLauncher>(relaxed = true)
    private val sceneRepository = mockk<SceneRepository>(relaxed = true)
    private val sceneController = mockk<SceneController>(relaxed = true)
    private val autoSceneSwitcher = mockk<AutoSceneSwitcher>(relaxed = true) {
        every { enabled } returns MutableStateFlow(false)
        every { intervalSeconds } returns MutableStateFlow(60L)
    }
    private val zoneRepository = mockk<ZoneRepository>(relaxed = true) {
        every { zonesFlow } returns MutableStateFlow(emptyList())
        every { privacyEnabledFlow } returns MutableStateFlow(false)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repositoryWith(settings: AppSettings): SettingsRepository = mockk {
        every { appSettingsFlow } returns MutableStateFlow(settings)
    }

    private fun viewModel(repository: SettingsRepository): StreamingViewModel =
        StreamingViewModel(
            engine,
            repository,
            launcher,
            sceneRepository,
            sceneController,
            autoSceneSwitcher,
            zoneRepository,
            StreamStartCoordinator(repository, engine, launcher, com.vivid.core.data.AndroidEncoderCapabilities()),
        )

    private suspend fun startViaRoute(remote: Boolean, settings: AppSettings, caps: EncoderCapabilities, scope: CoroutineScope) {
        val repository = repositoryWith(settings)
        if (remote) {
            val coordinator = StreamStartCoordinator(repository, engine, launcher, caps)
            StreamingEngineStreamControl(engine, coordinator, launcher, scope).start()
        } else {
            viewModel(repository).also { it.encoderCapabilities = caps }.startStream()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `local and remote configure fallback profile and adaptive control before service launch`(remote: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = settings(adaptiveBitrate = true).copy(
            streamUseTls = true,
            secondaryStreamUrl = "rtmp://second.example/app", secondaryStreamKey = "key-2",
        )
        startViaRoute(remote, saved, FakeCaps(setOf(Cap("video/hevc", 1280, 720, 30))), backgroundScope)
        advanceUntilIdle()
        verifyOrder {
            engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H265, EncoderPreset.HD30, true), true)
            engine.configureAdaptiveBitrate(true)
            launcher.startStreaming(listOf("rtmps://live.example/app/key-1", "rtmp://second.example/app/key-2"))
        }
        verify(exactly = 0) { engine.startStream(any<List<String>>()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `both routes block invalid secondary settings before applying encoder configuration`(remote: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val invalid = settings().copy(secondaryStreamUrl = "https://second.example/app")
        startViaRoute(remote, invalid, FakeCaps(emptySet()), backgroundScope)
        advanceUntilIdle()
        verify(exactly = 0) { engine.configureEncoder(any(), any()) }
        verify(exactly = 0) { engine.configureAdaptiveBitrate(any()) }
        verify(exactly = 0) { launcher.startStreaming(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `both routes preserve strict profile and disabled adaptive mode`(remote: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        startViaRoute(remote, settings(autoFallback = false, preference = VideoCodecPreference.H265), FakeCaps(emptySet()), backgroundScope)
        advanceUntilIdle()
        verifyOrder {
            engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H265, EncoderPreset.S_4K60, false), false)
            engine.configureAdaptiveBitrate(false)
            launcher.startStreaming(listOf("rtmp://live.example/app/key-1"))
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `both routes ignore repeated start while streaming without reconfiguring adaptive control`(remote: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { engine.streamingState } returns MutableStateFlow(StreamingState.Streaming)
        startViaRoute(remote, settings(adaptiveBitrate = false), FakeCaps(emptySet()), backgroundScope)
        advanceUntilIdle()
        verify(exactly = 0) { engine.configureEncoder(any(), any()) }
        verify(exactly = 0) { engine.configureAdaptiveBitrate(any()) }
        verify(exactly = 0) { launcher.startStreaming(any()) }
    }

    private fun settings(
        preset: EncoderPreset = EncoderPreset.S_4K60,
        preference: VideoCodecPreference = VideoCodecPreference.AUTO,
        autoFallback: Boolean = true,
        adaptiveBitrate: Boolean = false,
    ) = AppSettings(
        streamUrl = "rtmp://live.example/app",
        streamKey = "key-1",
        encoderPreset = preset,
        videoCodecPreference = preference,
        encoderAutoFallback = autoFallback,
        adaptiveBitrateEnabled = adaptiveBitrate,
    )

    @Test
    fun `AUTO mit HEVC-faehiger Hardware resolvt H265 im Wunsch-Preset`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings()))
        vm.encoderCapabilities = FakeCaps(setOf(Cap("video/hevc", 3840, 2160, 60)))

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) {
            engine.configureEncoder(
                match {
                    it.codec == VideoCodecPreference.H265 &&
                        it.preset == EncoderPreset.S_4K60 &&
                        !it.fallbackApplied
                },
                true,
            )
        }
    }

    @Test
    fun `AUTO ohne HEVC faellt auf H264 zurueck`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings()))
        vm.encoderCapabilities = FakeCaps(setOf(Cap("video/avc", 3840, 2160, 60)))

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) {
            engine.configureEncoder(
                match {
                    it.codec == VideoCodecPreference.H264 &&
                        it.preset == EncoderPreset.S_4K60 &&
                        it.fallbackApplied
                },
                true,
            )
        }
    }

    @Test
    fun `HEVC nur in kleinerer Aufloesung - Preset stuft ab`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings()))
        vm.encoderCapabilities = FakeCaps(setOf(Cap("video/hevc", 1280, 720, 30)))

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) {
            engine.configureEncoder(
                match {
                    it.codec == VideoCodecPreference.H265 &&
                        it.preset == EncoderPreset.HD30
                },
                true,
            )
        }
    }

    @Test
    fun `Strict-Modus uebernimmt die Wunsch-Kombi ohne Faehigkeits-Pruefung`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings(autoFallback = false, preference = VideoCodecPreference.H265)))
        vm.encoderCapabilities = FakeCaps(emptySet()) // nichts unterstützt — egal im Strict-Modus

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) {
            engine.configureEncoder(
                ResolvedEncoderConfig(VideoCodecPreference.H265, EncoderPreset.S_4K60, fallbackApplied = false),
                false,
            )
        }
    }

    // --- Adaptive Bitrate (v0.6.0) ------------------------------------------

    @Test
    fun `Go-Live verdrahtet die adaptive-Bitrate-Einstellung in die Engine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings(adaptiveBitrate = true)))
        vm.encoderCapabilities = FakeCaps(setOf(Cap("video/avc", 3840, 2160, 60)))

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) { engine.configureAdaptiveBitrate(true) }
    }

    @Test
    fun `Go-Live uebergibt adaptive Bitrate aus bei deaktivierter Einstellung`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(repositoryWith(settings(adaptiveBitrate = false)))
        vm.encoderCapabilities = FakeCaps(emptySet())

        vm.startStream()
        advanceUntilIdle()

        verify(exactly = 1) { engine.configureAdaptiveBitrate(false) }
    }
}
