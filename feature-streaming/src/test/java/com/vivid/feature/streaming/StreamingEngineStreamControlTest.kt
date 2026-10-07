package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteStreamStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingEngineStreamControlTest {
    private val launcher = mockk<StreamingServiceLauncher>(relaxed = true)
    private val capabilities = object : com.vivid.core.data.EncoderCapabilities {
        override fun supports(mime: String, width: Int, height: Int, fps: Int) = true
    }


    private fun controlWith(
        engine: StreamingEngine,
        settings: AppSettings,
        state: StreamingState = StreamingState.Idle,
    ): StreamingEngineStreamControl {
        every { engine.streamingState } returns MutableStateFlow(state)
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(settings)
        }
        return StreamingEngineStreamControl(engine, StreamStartCoordinator(repository, engine, launcher, capabilities), launcher, scope)
    }

    @Test
    fun `status maps idle to IDLE`() = runTest {
        val control = controlWith(mockk(relaxed = true), AppSettings(), StreamingState.Idle)
        assertEquals(RemoteStreamStatus.IDLE, control.status.value)
    }

    @Test
    fun `status maps preparing to PREPARING`() = runTest {
        val control = controlWith(mockk(relaxed = true), AppSettings(), StreamingState.Preparing)
        assertEquals(RemoteStreamStatus.PREPARING, control.status.value)
    }

    @Test
    fun `status maps streaming to STREAMING`() = runTest {
        val control = controlWith(mockk(relaxed = true), AppSettings(), StreamingState.Streaming)
        assertEquals(RemoteStreamStatus.STREAMING, control.status.value)
    }

    @Test
    fun `status maps failed to FAILED`() = runTest {
        val control = controlWith(mockk(relaxed = true), AppSettings(), StreamingState.Failed("boom"))
        assertEquals(RemoteStreamStatus.FAILED, control.status.value)
    }

    @Test
    fun `start prepares settings and launches the foreground service`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(
            engine,
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "key-1"),
        )
        control.start()
        coVerify { launcher.startStreaming(listOf("rtmp://live.example/app/key-1")) }
    }

    @Test
    fun `start with blank url does not start the engine`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(engine, AppSettings())
        control.start()
        coVerify(exactly = 0) { launcher.startStreaming(any()) }
    }

    @Test
    fun `start builds urls for primary and secondary targets`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(
            engine,
            AppSettings(
                streamUrl = "rtmp://live.example/app",
                streamKey = "key-1",
                secondaryStreamUrl = "rtmp://second.example/app",
                secondaryStreamKey = "key-2",
                secondaryStreamUseTls = true,
            ),
        )
        control.start()
        coVerify {
            launcher.startStreaming(
                listOf(
                    "rtmp://live.example/app/key-1",
                    "rtmps://second.example/app/key-2",
                ),
            )
        }
    }

    @Test
    fun `start with only a secondary target is blocked like local start`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(
            engine,
            AppSettings(
                secondaryStreamUrl = "rtmp://second.example/app",
                secondaryStreamKey = "key-2",
            ),
        )
        control.start()
        verify(exactly = 0) { launcher.startStreaming(any()) }
    }

    @Test
    fun `start rejects a blank required primary target`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(
            engine,
            AppSettings(
                streamUrl = "   ",
                streamKey = "key-1",
                secondaryStreamUrl = "rtmp://second.example/app",
                secondaryStreamKey = "",
            ),
        )
        control.start()
        verify(exactly = 0) { launcher.startStreaming(any()) }
    }

    @Test
    fun `stop delegates to the foreground service`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(engine, AppSettings())
        control.stop()
        verify { launcher.stopStreaming() }
    }

    @Test
    fun `mapping function covers all engine states`() {
        assertEquals(RemoteStreamStatus.IDLE, mapToRemoteStatus(StreamingState.Idle))
        assertEquals(RemoteStreamStatus.PREPARING, mapToRemoteStatus(StreamingState.Preparing))
        assertEquals(RemoteStreamStatus.STREAMING, mapToRemoteStatus(StreamingState.Streaming))
        assertEquals(RemoteStreamStatus.FAILED, mapToRemoteStatus(StreamingState.Failed("x")))
    }
}
