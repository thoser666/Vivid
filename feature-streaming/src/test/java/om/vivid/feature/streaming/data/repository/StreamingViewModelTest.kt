package com.vivid.feature.streaming.ui

import app.cash.turbine.test
import com.vivid.feature.streaming.data.model.StreamingState
import com.vivid.feature.streaming.data.repository.StreamingViewModel
import com.vivid.feature.streaming.domain.repository.FakeStreamingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class StreamingViewModelTest {

    private lateinit var viewModel: StreamingViewModel
    private lateinit var fakeRepository: FakeStreamingRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Set the main dispatcher for coroutine tests
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeStreamingRepository()
        viewModel = StreamingViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        // Reset the main dispatcher after each test
        Dispatchers.resetMain()
    }

    @Test
    fun `state is collected from repository`() = runTest {
        viewModel.streamingState.test {
            // The initial state from the fake repo should be Idle
            assertEquals(StreamingState.Idle, awaitItem())
        }
    }

    @Test
    fun `toggleStreaming calls startStream when state is Idle`() = runTest {
        // Ensure state is Idle
        assertEquals(StreamingState.Idle, viewModel.streamingState.value)

        // Trigger action
        viewModel.toggleStreaming()

        // Verify that the correct method was called on the repository
        assertTrue(fakeRepository.startStreamCalled)
    }

    // ... andere Tests entsprechend anpassen ...
}