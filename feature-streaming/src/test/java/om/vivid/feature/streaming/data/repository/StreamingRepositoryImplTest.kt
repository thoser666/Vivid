package com.vivid.feature.streaming.data.repository

import com.vivid.data.model.StreamingState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreamingRepositoryImplTest {

    private lateinit var repository: StreamingRepositoryImpl

    @Before
    fun setUp() {
        // Erstelle vor jedem Test eine frische Instanz des Repositories
        repository = StreamingRepositoryImpl()
    }

    @Test
    fun `startStream should transition from Idle to Connecting to Streaming`() = runTest {
        repository.streamingState.test {
            // 1. Initial state should be Idle
            assertEquals(StreamingState.Idle, awaitItem())
            // 2. Start the stream
            repository.startStream()
            // 3. State should transition to Connecting
            assertEquals(StreamingState.Connecting, awaitItem())
            // 4. After delay, state should be Streaming
            assertEquals(StreamingState.Streaming, awaitItem())
            // Ensure no more events are coming
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `stopStream should transition from Streaming to Disconnecting to Idle`() = runTest {
        repository.streamingState.test {
            // Springe zum Streaming-Zustand
            repository.startStream()
            awaitItem() // Idle
            awaitItem() // Connecting
            awaitItem() // Streaming

            // Stoppe den Stream
            repository.stopStream()

            // Der Zustand sollte zu Disconnecting wechseln
            assertThat(awaitItem()).isEqualTo(StreamingState.Disconnecting)

            // Und schließlich zurück zu Idle
            assertThat(awaitItem()).isEqualTo(StreamingState.Idle)
        }
    }

    @Test
    fun `startStream should do nothing if already streaming`() = runTest {
        repository.streamingState.test {
            // Gehe in den Streaming-Zustand
            repository.startStream()
            awaitItem() // Idle
            awaitItem() // Connecting
            awaitItem() // Streaming

            // Versuche, den Stream erneut zu starten
            repository.startStream()

            // Es sollten keine neuen Zustandsänderungen auftreten.
            // 'expectNoEvents' wartet eine kurze Zeit und schlägt fehl, wenn ein Event eintrifft.
            expectNoEvents()
        }
    }
}
