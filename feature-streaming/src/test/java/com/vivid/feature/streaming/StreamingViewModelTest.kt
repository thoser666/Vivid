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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingViewModelTest {

    private val engine = mockk<StreamingEngine>(relaxed = true)

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
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { engine.startStream("rtmp://live.example/app/key-1") }
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `startStream uses url as-is when key is already the trailing segment`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app/key-1", streamKey = "key-1"),
        )
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { engine.startStream("rtmp://live.example/app/key-1") }
    }

    @Test
    fun `startStream appends key even when it appears earlier in the url`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // Regression: "live" erscheint in der URL, ist aber nicht das Key-Segment
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "live-key"),
        )
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { engine.startStream("rtmp://live.example/app/live-key") }
    }

    @Test
    fun `startStream without stream key starts with plain url`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = ""),
        )
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify { engine.startStream("rtmp://live.example/app") }
    }

    @Test
    fun `startStream with blank url sets error message and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.startStream()
        advanceUntilIdle()

        coVerify(exactly = 0) { engine.startStream(any()) }
        assertEquals("Keine Stream-URL konfiguriert. Bitte in den Einstellungen hinterlegen.", viewModel.errorMessage.value)
    }

    @Test
    fun `stopStream delegates to the engine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repositoryWith(AppSettings())
        val viewModel = StreamingViewModel(engine, repository)

        viewModel.stopStream()

        verify { engine.stopStream() }
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
}
