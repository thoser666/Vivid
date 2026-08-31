package com.vivid.feature.widget

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class BatteryWidgetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun settings(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    private fun createViewModel(
        batteryLevel: Int = 75,
        isCharging: Boolean = false,
        settingsFlow: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings()),
        ticks: List<Unit> = listOf(Unit),
    ): BatteryWidgetViewModel {
        val vm = BatteryWidgetViewModel(
            context = mockk(relaxed = true),
            settingsRepository = settings(settingsFlow),
        )
        vm.batteryLevelReader = { batteryLevel to isCharging }
        vm.ticker = { flowOf(*ticks.toTypedArray()) }
        return vm
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
    fun `isLowBattery returns true when level is at or below threshold`() {
        assertTrue(BatteryWidgetViewModel.isLowBattery(10, 15))
        assertTrue(BatteryWidgetViewModel.isLowBattery(15, 15))
        assertTrue(BatteryWidgetViewModel.isLowBattery(5, 15))
    }

    @Test
    fun `isLowBattery returns false when level is above threshold`() {
        assertFalse(BatteryWidgetViewModel.isLowBattery(20, 15))
        assertFalse(BatteryWidgetViewModel.isLowBattery(50, 15))
        assertFalse(BatteryWidgetViewModel.isLowBattery(100, 15))
    }

    @Test
    fun `initial state defaults when settings are disabled`() = runTest {
        val vm = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.enabled)
        assertTrue(state.showIcon)
        assertTrue(state.showPercent)
        assertEquals(15, state.lowThreshold)
    }

    @Test
    fun `settings update propagates to uiState`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings())
        val vm = createViewModel(settingsFlow = settingsFlow)
        testScheduler.advanceUntilIdle()

        settingsFlow.value = AppSettings(
            batteryEnabled = true,
            batteryShowIcon = false,
            batteryShowPercent = true,
            batteryLowThresholdPercent = 20,
        )
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.enabled)
        assertFalse(state.showIcon)
        assertTrue(state.showPercent)
        assertEquals(20, state.lowThreshold)
    }

    @Test
    fun `battery level is read from system`() = runTest {
        val settingsFlow = MutableStateFlow(AppSettings(batteryEnabled = true))
        val vm = createViewModel(
            batteryLevel = 42,
            isCharging = true,
            settingsFlow = settingsFlow,
        )
        // Advance past the settings collection + first battery read
        testScheduler.advanceTimeBy(1_000)

        val state = vm.uiState.value
        assertEquals(42, state.level)
        assertTrue(state.isCharging)
    }
}
