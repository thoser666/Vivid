package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SceneControllerTest {

    private fun controller(
        sceneRepository: SceneRepository = mockk(),
        settingsRepository: SettingsRepository = mockk(),
        streamingEngine: StreamingEngine = mockk(),
    ): SceneController = SceneController(sceneRepository, settingsRepository, streamingEngine)

    private fun scene(
        id: String = "1",
        source: SceneVideoSource = SceneVideoSource.CAMERA,
    ) = StreamScene(
        id = id,
        name = "Szene $id",
        videoSource = source,
        widgetEnabled = true,
        widgetShowTime = false,
        widgetShowLocation = true,
        widgetShowSpeed = true,
        widgetShowAltitude = true,
        widgetTemplate = "{time}",
        streamUrl = "rtmp://live.example/app",
        streamKey = "key-$id",
        streamUseTls = true,
    )

    @Test
    fun `applyScene persists stream target widget state and marks the scene active`() = runTest {
        val sceneRepository = mockk<SceneRepository> {
            coEvery { setActiveScene(any()) } just Runs
        }
        val settingsRepository = mockk<SettingsRepository> {
            coEvery { updateStreamSettings(any(), any(), any()) } just Runs
            coEvery { updateWidgetSettings(
                any(), any(), any(), any(), any(), any(),
            ) } just Runs
        }
        val streamingEngine = mockk<StreamingEngine> {
            every { switchSource(any()) } returns true
        }
        val controller = controller(
            sceneRepository = sceneRepository,
            settingsRepository = settingsRepository,
            streamingEngine = streamingEngine,
        )

        controller.applyScene(scene("1"))

        coVerify(exactly = 1) {
            settingsRepository.updateStreamSettings(
                url = "rtmp://live.example/app",
                key = "key-1",
                useTls = true,
            )
            settingsRepository.updateWidgetSettings(
                enabled = true,
                showTime = false,
                showLocation = true,
                showSpeed = true,
                showAltitude = true,
                template = "{time}",
            )
            streamingEngine.switchSource(VideoSourceKind.CAMERA)
            sceneRepository.setActiveScene("1")
        }
    }

    @Test
    fun `applyScene switches the video source for a screen capture scene`() = runTest {
        val sceneRepository = mockk<SceneRepository> {
            coEvery { setActiveScene(any()) } just Runs
        }
        val settingsRepository = mockk<SettingsRepository> {
            coEvery { updateStreamSettings(any(), any(), any()) } just Runs
            coEvery { updateWidgetSettings(
                any(), any(), any(), any(), any(), any(),
            ) } just Runs
        }
        val streamingEngine = mockk<StreamingEngine> {
            every { switchSource(any()) } returns true
        }
        val controller = controller(
            sceneRepository = sceneRepository,
            settingsRepository = settingsRepository,
            streamingEngine = streamingEngine,
        )

        controller.applyScene(scene("2", source = SceneVideoSource.SCREEN_CAPTURE))

        coVerify(exactly = 1) { streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE) }
    }
}