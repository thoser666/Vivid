package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
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

    private val engine = mockk<StreamingEngine>(relaxed = true)
    private val launcher = mockk<StreamingServiceLauncher>(relaxed = true)
    private val sceneRepository = mockk<SceneRepository>(relaxed = true)
    private val sceneController = mockk<SceneController>(relaxed = true)
    private val autoSceneSwitcher = mockk<AutoSceneSwitcher>(relaxed = true) {
        every { enabled } returns MutableStateFlow(false)
        every { intervalSeconds } returns MutableStateFlow(60L)
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
        )

    private fun settings(
        preset: EncoderPreset = EncoderPreset.S_4K60,
        preference: VideoCodecPreference = VideoCodecPreference.AUTO,
        autoFallback: Boolean = true,
    ) = AppSettings(
        streamUrl = "rtmp://live.example/app",
        streamKey = "key-1",
        encoderPreset = preset,
        videoCodecPreference = preference,
        encoderAutoFallback = autoFallback,
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
}
