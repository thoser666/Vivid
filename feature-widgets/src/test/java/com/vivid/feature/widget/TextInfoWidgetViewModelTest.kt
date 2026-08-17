package com.vivid.feature.widget

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.location.LocationProvider
import com.vivid.core.location.WidgetLocation
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextInfoWidgetViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val previousDefaultZone: TimeZone = TimeZone.getDefault()

    private fun settings(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    private fun locationProvider(flow: MutableStateFlow<WidgetLocation>): LocationProvider = mockk {
        every { locationUpdates() } returns flow
    }

    private fun location(
        lat: Double = 52.52,
        lon: Double = 13.405,
        speed: Float = 10f,
        hasSpeed: Boolean = true,
    ) = WidgetLocation(
        latitude = lat,
        longitude = lon,
        speedMetersPerSecond = speed,
        hasSpeed = hasSpeed,
        altitudeMeters = 34.0,
        hasAltitude = true,
        timestampMillis = 0L,
    )

    /** 2026-08-17 14:05:<seconds> Europe/Berlin. */
    private fun epoch(seconds: Int): Long =
        ZonedDateTime.of(2026, 8, 17, 14, 5, seconds, 0, zone).toInstant().toEpochMilli()

    /**
     * Erstellt das VM mit einem endlichen Ticker (Test-Hook), damit der Test-Scheduler
     * nicht durch den Echtzeit-Ticker endlos weiterläuft.
     */
    private fun createViewModel(
        settingsFlow: MutableStateFlow<AppSettings>,
        locationFlow: MutableStateFlow<WidgetLocation> = MutableStateFlow(location()),
        ticks: List<Long> = emptyList(),
    ): TextInfoWidgetViewModel {
        val viewModel = TextInfoWidgetViewModel(settings(settingsFlow), locationProvider(locationFlow))
        viewModel.ticker = { flowOf(*ticks.toTypedArray()) }
        return viewModel
    }

    @Before
    fun setUp() {
        // Deterministische Zone: das VM formatiert mit TimeZone.getDefault().
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousDefaultZone)
        Dispatchers.resetMain()
    }

    @Test
    fun `widget is disabled by default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(MutableStateFlow(AppSettings()))
        runCurrent()

        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `ticker updates time and date from the tick value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true)),
            ticks = listOf(epoch(32)),
        )
        // Vor dem ersten Scheduler-Step: Platzhalter (Ticker hat noch nicht getickt).
        assertEquals("--:--:--", viewModel.uiState.value.time)

        runCurrent()
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals("14:05:32", viewModel.uiState.value.time)
        assertEquals("17.08.2026", viewModel.uiState.value.date)
    }

    @Test
    fun `latest tick wins when several ticks arrive`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true)),
            ticks = listOf(epoch(32), epoch(33)),
        )
        runCurrent()

        // flowOf emittiert alle Ticks synchron im ersten Scheduler-Step → letzter Wert gewinnt.
        assertEquals("14:05:33", viewModel.uiState.value.time)
    }

    @Test
    fun `location and speed are shown when the widget is active`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(MutableStateFlow(AppSettings(widgetEnabled = true)))
        runCurrent()

        assertEquals("52.5200° N, 13.4050° O", viewModel.uiState.value.location)
        assertEquals("36,0 km/h", viewModel.uiState.value.speed)
    }

    @Test
    fun `speed stays dash when the location has no speed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true)),
            locationFlow = MutableStateFlow(location(hasSpeed = false)),
        )
        runCurrent()

        assertEquals("52.5200° N, 13.4050° O", viewModel.uiState.value.location)
        assertEquals("–", viewModel.uiState.value.speed)
    }

    @Test
    fun `location stays hidden when location and speed fields are off`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(
                AppSettings(widgetEnabled = true, widgetShowLocation = false, widgetShowSpeed = false),
            ),
        )
        runCurrent()

        assertEquals("", viewModel.uiState.value.location)
        assertEquals("", viewModel.uiState.value.speed)
    }

    @Test
    fun `turning the widget off stops location updates`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetShowLocation = true))
        val locationFlow = MutableStateFlow(location())
        val viewModel = createViewModel(settingsFlow, locationFlow)
        runCurrent()
        assertEquals("52.5200° N, 13.4050° O", viewModel.uiState.value.location)

        // Widget aus → neue Location wird nicht mehr übernommen.
        settingsFlow.value = AppSettings(widgetEnabled = false, widgetShowLocation = true)
        runCurrent()
        locationFlow.value = location(lat = 48.13, lon = 11.57)
        runCurrent()

        assertEquals("52.5200° N, 13.4050° O", viewModel.uiState.value.location)
    }
}
