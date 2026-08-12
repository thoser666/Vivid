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
        return StreamingEngineStreamControl(engine, repository, scope)
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
    fun `start builds url from settings and starts the engine`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(
            engine,
            AppSettings(streamUrl = "rtmp://live.example/app", streamKey = "key-1"),
        )
        control.start()
        coVerify { engine.startStream("rtmp://live.example/app/key-1") }
    }

    @Test
    fun `start with blank url does not start the engine`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(engine, AppSettings())
        control.start()
        coVerify(exactly = 0) { engine.startStream(any()) }
    }

    @Test
    fun `stop delegates to the engine`() = runTest {
        val engine = mockk<StreamingEngine>(relaxed = true)
        val control = controlWith(engine, AppSettings())
        control.stop()
        verify { engine.stopStream() }
    }

    @Test
    fun `mapping function covers all engine states`() {
        assertEquals(RemoteStreamStatus.IDLE, mapToRemoteStatus(StreamingState.Idle))
        assertEquals(RemoteStreamStatus.PREPARING, mapToRemoteStatus(StreamingState.Preparing))
        assertEquals(RemoteStreamStatus.STREAMING, mapToRemoteStatus(StreamingState.Streaming))
        assertEquals(RemoteStreamStatus.FAILED, mapToRemoteStatus(StreamingState.Failed("x")))
    }
}
