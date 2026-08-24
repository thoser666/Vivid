package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.scene.AutoSceneSwitcher
import com.vivid.feature.streaming.scene.SceneController
import com.vivid.feature.streaming.source.VideoSourceKind
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingViewModelTest {

    private val engine = mockk<StreamingEngine>(relaxed = true)
    private val launcher = mockk<StreamingServiceLauncher>(relaxed = true)

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repositoryWith(settings: AppSettings): SettingsRepository = mockk {
        every { appSettingsFlow } returns MutableStateFlow(settings)
    }

    // Achtung: Felder NICHT wie die Mock-Eigenschaften benennen (scenesFlow/...),
    // sonst schattiert der Klassen-Feldzugriff in `every { }` die Mock-Property.
    private val scenesState = MutableStateFlow<List<StreamScene>>(emptyList())
    private val activeSceneState = MutableStateFlow<String?>(null)
    private val sceneRepository = mockk<SceneRepository> {
        every { scenesFlow } returns scenesState
        every { activeSceneIdFlow } returns activeSceneState
        coEvery { saveScene(any()) } just Runs
        coEvery { deleteScene(any()) } just Runs
    }
    private val sceneController = mockk<SceneController> {
        coEvery { applyScene(any()) } just Runs
    }
    private val autoSceneSwitcher = mockk<AutoSceneSwitcher> {
        every { enabled } returns MutableStateFlow(false)
        every { intervalSeconds } returns MutableStateFlow(60L)
        every { setEnabled(any()) } just Runs
        every { setIntervalSeconds(any()) } just Runs
    }

    private fun viewModel(
        repository: SettingsRepository = repositoryWith(AppSettings()),
    ) = StreamingViewModel(engine, repository, launcher, sceneRepository, sceneController, autoSceneSwitcher)

    @Test
    fun `startStream uses saved url with appended stream key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "key-1"),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/key-1")) }
        assertTrue(viewModel.configIssues.value.none { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `startStream upgrades rtmp to rtmps when tls is enabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.twitch.tv/app",
                streamKey = "key-1",
                streamUseTls = true,
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmps://live.twitch.tv/app/key-1")) }
        assertTrue(viewModel.configIssues.value.none { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `startStream secures youtube ingest url when tls is enabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://a.rtmp.youtube.com/live2",
                streamKey = "key-1",
                streamUseTls = true,
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmps://a.rtmp.youtube.com/live2/key-1")) }
        assertTrue(viewModel.configIssues.value.none { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `startStream uses url as-is when key is already the trailing segment`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app/key-1", streamKey = "key-1"),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/key-1")) }
    }

    @Test
    fun `startStream appends key even when it appears earlier in the url`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // Regression: "live" erscheint in der URL, ist aber nicht das Key-Segment
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "live-key"),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/live-key")) }
    }

    @Test
    fun `startStream without stream key starts with plain url`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = ""),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app")) }
    }

    @Test
    fun `startStream with blank url sets error message and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.ERROR && it.messageRes == R.string.stream_error_no_url
            },
        )
    }

    @Test
    fun `startStream with unsupported scheme shows error and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "http://live.example/app", streamKey = "key-1"),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.messageRes == R.string.stream_error_bad_scheme &&
                    it.formatArgs == listOf("http")
            },
        )
    }

    @Test
    fun `startStream with missing host shows error and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp:///app", streamKey = "key-1"),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.ERROR && it.messageRes == R.string.stream_error_no_host
            },
        )
    }

    @Test
    fun `configIssues exposes warning for missing key but stream still starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.twitch.tv/app", streamKey = ""),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.twitch.tv/app")) }
        assertTrue(viewModel.configIssues.value.any { it.severity == ConfigIssueSeverity.WARNING })
        assertTrue(viewModel.configIssues.value.none { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `configIssues is populated on init`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        assertTrue(viewModel.configIssues.value.any { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `runConfigCheck revalidates after settings change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsFlow = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns settingsFlow
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.configIssues.value.any { it.severity == ConfigIssueSeverity.ERROR })

        settingsFlow.value = AppSettings(streamUrl = "rtmp://live.twitch.tv/app", streamKey = "key-1")
        viewModel.runConfigCheck()
        advanceUntilIdle()

        assertTrue(viewModel.configIssues.value.isEmpty())
    }

    @Test
    fun `stopStream delegates to the engine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = viewModel(repository)

        viewModel.stopStream()

        verify { launcher.stopStreaming() }
    }

    // --- Multi-Streaming (primär + optional sekundär) ---

    @Test
    fun `startStream starts primary and secondary targets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-1",
                secondaryStreamUrl = "rtmp://second.example/app",
                secondaryStreamKey = "key-2",
                secondaryStreamUseTls = true,
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify {
            launcher.startStreaming(
                listOf(
                    "rtmp://live.example/app/key-1",
                    "rtmps://second.example/app/key-2",
                ),
            )
        }
        assertTrue(viewModel.configIssues.value.none { it.severity == ConfigIssueSeverity.ERROR })
    }

    @Test
    fun `startStream with blank secondary url starts only the primary`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-1",
                secondaryStreamUrl = "  ",
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/key-1")) }
    }

    @Test
    fun `startStream with invalid secondary url blocks the start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-1",
                secondaryStreamUrl = "http://second.example/app",
                secondaryStreamKey = "key-2",
            ),
        )
        val viewModel = viewModel(repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.ERROR && it.prefixRes == R.string.stream_secondary_label
            },
        )
    }

    @Test
    fun `configIssues reports a warning for a secondary url without key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-1",
                secondaryStreamUrl = "rtmp://second.example/app",
            ),
        )
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.WARNING && it.prefixRes == R.string.stream_secondary_label
            },
        )
    }

    @Test
    fun `buildStreamUrl returns null for blank url`() {
        assertNull(buildStreamUrl("  ", "key-1"))
        assertNull(buildStreamUrl("", ""))
    }

    @Test
    fun `buildStreamUrl keeps url when key is blank`() {
        assertEquals("rtmp://live.example/app", buildStreamUrl("rtmp://live.example/app", ""))
    }

    @Test
    fun `buildStreamUrl appends key without double slash`() {
        assertEquals("rtmp://live.example/app/key-1", buildStreamUrl("rtmp://live.example/app/", "key-1"))
    }

    @Test
    fun `buildStreamUrl does not duplicate an already embedded trailing key`() {
        assertEquals(
            "rtmp://live.example/app/key-1",
            buildStreamUrl("rtmp://live.example/app/key-1", "key-1"),
        )
    }

    @Test
    fun `buildStreamUrl leaves plain rtmp url unchanged when tls is off`() {
        assertEquals(
            "rtmp://live.example/app/key-1",
            buildStreamUrl("rtmp://live.example/app", "key-1", useTls = false),
        )
    }

    @Test
    fun `buildStreamUrl upgrades rtmp scheme to rtmps when tls is on`() {
        assertEquals(
            "rtmps://live.example/app/key-1",
            buildStreamUrl("rtmp://live.example/app", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl does not duplicate rtmps scheme when tls is on`() {
        assertEquals(
            "rtmps://live.example/app/key-1",
            buildStreamUrl("rtmps://live.example/app", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl leaves non-rtmp schemes untouched when tls is on`() {
        assertEquals(
            "srt://live.example:9000?streamid=key-1",
            buildStreamUrl("srt://live.example:9000?streamid=key-1", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl rewrites standard rtmp port 1935 to 443 when tls is on`() {
        assertEquals(
            "rtmps://live.example.com:443/app/key-1",
            buildStreamUrl("rtmp://live.example.com:1935/app", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl rewrites standard rtmp port 1935 without a path`() {
        assertEquals(
            "rtmps://live.example.com:443/key-1",
            buildStreamUrl("rtmp://live.example.com:1935", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl keeps custom tls port when tls is on`() {
        assertEquals(
            "rtmps://live.example.com:8443/app/key-1",
            buildStreamUrl("rtmp://live.example.com:8443/app", "key-1", useTls = true),
        )
    }

    @Test
    fun `buildStreamUrl keeps standard port when tls is off`() {
        assertEquals(
            "rtmp://live.example.com:1935/app/key-1",
            buildStreamUrl("rtmp://live.example.com:1935/app", "key-1", useTls = false),
        )
    }

    @Test
    fun `buildStreamUrl does not rewrite already secure rtmps urls`() {
        assertEquals(
            "rtmps://live.example.com:1935/app/key-1",
            buildStreamUrl("rtmps://live.example.com:1935/app", "key-1", useTls = true),
        )
    }

    // --- Szenen (Basic Scenes) ---

    @Test
    fun `saveScene captures the current configuration as a scene`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-9",
                streamUseTls = true,
                widgetEnabled = true,
                widgetShowTime = false,
                widgetShowSpeed = false,
                widgetTemplate = "{time}",
            ),
        )
        every { engine.activeSourceKind } returns MutableStateFlow(VideoSourceKind.SCREEN_CAPTURE)
        val viewModel = viewModel(repository)

        viewModel.saveScene("Kamera B")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            sceneRepository.saveScene(
                match {
                    it.name == "Kamera B" &&
                        it.videoSource == SceneVideoSource.SCREEN_CAPTURE &&
                        it.streamUrl == "rtmp://live.example/app" &&
                        it.streamKey == "key-9" &&
                        it.streamUseTls &&
                        it.widgetEnabled &&
                        !it.widgetShowTime &&
                        !it.widgetShowSpeed &&
                        it.widgetTemplate == "{time}"
                },
            )
        }
    }

    @Test
    fun `saveScene with a blank name is a no-op`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()

        viewModel.saveScene("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { sceneRepository.saveScene(any()) }
    }

    @Test
    fun `applyScene delegates to the scene controller`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()
        val scene = StreamScene(id = "1", name = "Szene 1")

        viewModel.applyScene(scene)
        advanceUntilIdle()

        coVerify(exactly = 1) { sceneController.applyScene(scene) }
    }

    @Test
    fun `deleteScene delegates to the scene repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()

        viewModel.deleteScene("1")
        advanceUntilIdle()

        coVerify(exactly = 1) { sceneRepository.deleteScene("1") }
    }

    @Test
    fun `scenes and activeSceneId expose the repository flows`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()

        scenesState.value = listOf(StreamScene(id = "1", name = "Szene 1"))
        advanceUntilIdle()

        assertEquals(listOf("1"), viewModel.scenes.first().map { it.id })
    }

    @Test
    fun `setAutoSwitchEnabled delegates to the auto scene switcher`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()

        viewModel.setAutoSwitchEnabled(true)
        viewModel.setAutoSwitchIntervalSeconds(30)

        verify(exactly = 1) { autoSceneSwitcher.setEnabled(true) }
        verify(exactly = 1) { autoSceneSwitcher.setIntervalSeconds(30) }
    }
}
