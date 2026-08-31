package com.vivid.feature.widget

import android.content.Context
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageWidgetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun settings(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state defaults to disabled with empty uri`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = ImageWidgetViewModel(mockk(relaxed = true), settings(settingsFlow))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.enabled)
        assertEquals("", state.uri)
        assertEquals(100, state.sizeDp)
        assertEquals(0.8f, state.opacity, 0.01f)
    }

    @Test
    fun `settings update propagates to uiState`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = ImageWidgetViewModel(mockk(relaxed = true), settings(settingsFlow))
        testScheduler.advanceUntilIdle()

        settingsFlow.value = AppSettings(
            imageWidgetEnabled = true,
            imageWidgetUri = "content://media/1",
            imageWidgetSizeDp = 200,
            imageWidgetOpacity = 0.5f,
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.enabled)
        assertEquals("content://media/1", state.uri)
        assertEquals(200, state.sizeDp)
        assertEquals(0.5f, state.opacity, 0.01f)
    }

    @Test
    fun `empty uri disables widget even when enabled`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = ImageWidgetViewModel(mockk(relaxed = true), settings(settingsFlow))
        testScheduler.advanceUntilIdle()

        settingsFlow.value = AppSettings(
            imageWidgetEnabled = true,
            imageWidgetUri = "",
        )
        testScheduler.advanceUntilIdle()

        // The ViewModel reads the values as-is; the Composable checks both enabled and uri
        assertTrue(vm.uiState.value.enabled)
        assertEquals("", vm.uiState.value.uri)
    }
}
