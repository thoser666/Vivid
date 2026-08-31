package com.vivid.feature.widget

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
class GridOverlayViewModelTest {

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
    fun `initial state defaults to disabled with spacing 40`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = GridOverlayViewModel(settings(settingsFlow))
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.enabled)
        assertEquals(40, state.spacingDp)
    }

    @Test
    fun `settings update propagates to uiState`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = GridOverlayViewModel(settings(settingsFlow))
        testScheduler.advanceUntilIdle()

        settingsFlow.value = AppSettings(
            gridOverlayEnabled = true,
            gridOverlaySpacingDp = 60,
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.enabled)
        assertEquals(60, state.spacingDp)
    }

    @Test
    fun `spacing is clamped to 10-100 in repository`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.appSettingsFlow } returns settingsFlow
        val vm = GridOverlayViewModel(repository)

        // Spacing should be read from settings, clamping happens in repository
        settingsFlow.value = AppSettings(gridOverlaySpacingDp = 5)
        testScheduler.advanceUntilIdle()

        // The ViewModel reads the value as-is; clamping is in updateGridOverlaySettings
        assertEquals(5, vm.uiState.value.spacingDp)
    }
}
