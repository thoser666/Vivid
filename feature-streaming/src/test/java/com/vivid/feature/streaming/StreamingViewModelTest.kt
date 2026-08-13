package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.coVerify
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

    @Test
    fun `startStream uses saved url with appended stream key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "key-1"),
        )
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/key-1")) }
        assertNull(viewModel.errorMessage.value)
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
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmps://live.twitch.tv/app/key-1")) }
        assertNull(viewModel.errorMessage.value)
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
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmps://a.rtmp.youtube.com/live2/key-1")) }
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `startStream uses url as-is when key is already the trailing segment`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app/key-1", streamKey = "key-1"),
        )
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app")) }
    }

    @Test
    fun `startStream with blank url sets error message and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertEquals("Keine Stream-URL konfiguriert. Bitte in den Einstellungen hinterlegen.", viewModel.errorMessage.value)
    }

    @Test
    fun `startStream with unsupported scheme shows error and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "http://live.example/app", streamKey = "key-1"),
        )
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertEquals(
            "Nicht unterstütztes Protokoll \"http\". Erlaubt sind rtmp, rtmps und srt.",
            viewModel.errorMessage.value,
        )
    }

    @Test
    fun `startStream with missing host shows error and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp:///app", streamKey = "key-1"),
        )
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(viewModel.errorMessage.value?.contains("Server-Host") == true)
    }

    @Test
    fun `configIssues exposes warning for missing key but stream still starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.twitch.tv/app", streamKey = ""),
        )
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)
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
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        assertNull(viewModel.errorMessage.value)
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
        val viewModel = StreamingViewModel(engine, repository, launcher)

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
        val viewModel = StreamingViewModel(engine, repository, launcher)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { launcher.startStreaming(any()) }
        assertTrue(viewModel.errorMessage.value?.contains("Zweites Ziel") == true)
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
        val viewModel = StreamingViewModel(engine, repository, launcher)

        advanceUntilIdle()

        assertTrue(
            viewModel.configIssues.value.any {
                it.severity == ConfigIssueSeverity.WARNING && it.message.contains("Zweites Ziel")
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
}
