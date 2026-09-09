package com.vivid.feature.widget

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.location.LocationProvider
import com.vivid.core.location.WidgetLocation
import io.mockk.coEvery
import io.mockk.coVerify
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
        geocoder: GeocoderResolver = this.geocoder,
    ): TextInfoWidgetViewModel {
        val viewModel = TextInfoWidgetViewModel(
            settings(settingsFlow),
            locationProvider(locationFlow),
            geocoder,
        )
        viewModel.ticker = { flowOf(*ticks.toTypedArray()) }
        return viewModel
    }

    @Before
    fun setUp() {
        // Deterministische Zone: das VM formatiert mit TimeZone.getDefault().
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    /** Relaxed Fake-Geocoder: liefert per Default „Kurfürstendamm/Berlin/Deutschland“. */
    private fun defaultGeocoder(): GeocoderResolver = mockk(relaxed = true) {
        coEvery { placenames(any(), any()) } returns Placenames("Kurfürstendamm", "Berlin", "Deutschland")
    }

    private val geocoder: GeocoderResolver = defaultGeocoder()

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

    // --- Template-Variablen ---

    @Test
    fun `empty template does not resolve`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "")),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertEquals("", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `template with time variable resolves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "Time: {time}")),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertEquals("Time: 14:05:32", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `template with speed variable resolves from location`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(
                AppSettings(widgetEnabled = true, widgetTemplate = "Speed: {speed}"),
            ),
            locationFlow = MutableStateFlow(location(speed = 14.5f)),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertEquals("Speed: 52,2 km/h", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `template with multiple variables resolves all`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(
                AppSettings(widgetEnabled = true, widgetTemplate = "{time} | {speed} | {altitude}"),
            ),
            locationFlow = MutableStateFlow(location(speed = 10f).copy(hasAltitude = true, altitudeMeters = 120.0)),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertEquals("14:05:32 | 36,0 km/h | 120 m", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `template with unknown variables keeps them unchanged`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(
                AppSettings(widgetEnabled = true, widgetTemplate = "{time} | {unknown}"),
            ),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertEquals("14:05:32 | {unknown}", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `changing template re-resolves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsFlow = MutableStateFlow(
            AppSettings(widgetEnabled = true, widgetTemplate = "{time}"),
        )
        val viewModel = createViewModel(settingsFlow, ticks = listOf(epoch(32)))
        runCurrent()

        assertEquals("14:05:32", viewModel.uiState.value.resolvedTemplate)

        settingsFlow.value = AppSettings(widgetEnabled = true, widgetTemplate = "Now: {time}")
        runCurrent()

        assertEquals("Now: 14:05:32", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `template triggers location updates even without showLocation toggles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(
                AppSettings(
                    widgetEnabled = true,
                    widgetShowLocation = false,
                    widgetShowSpeed = false,
                    widgetShowAltitude = false,
                    widgetTemplate = "GPS: {lat},{lon}",
                ),
            ),
            locationFlow = MutableStateFlow(location()),
            ticks = listOf(epoch(32)),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.resolvedTemplate.contains("52.52"))
    }

    // --- Geocoding-Variablen ({road}/{city}/{country}) ---

    @Test
    fun `geocode runs when the template uses a geo variable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{road}")),
        )
        runCurrent()

        assertEquals("Kurfürstendamm", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `geocode does not run when the template has no geo variable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{time}")),
        )
        runCurrent()

        coVerify(exactly = 0) { geocoder.placenames(any(), any()) }
    }

    @Test
    fun `geocode does not run while the widget is disabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = false, widgetTemplate = "{road}")),
        )
        runCurrent()

        coVerify(exactly = 0) { geocoder.placenames(any(), any()) }
    }

    @Test
    fun `geo variables fall back to dash when the geocoder yields nothing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val emptyGeocoder: GeocoderResolver = mockk {
            coEvery { placenames(any(), any()) } returns null
        }
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{city}")),
            geocoder = emptyGeocoder,
        )

        // Vor dem ersten Scheduler-Step: Template noch nicht geladen → leer.
        assertEquals("", viewModel.uiState.value.resolvedTemplate)
        runCurrent()

        // Geocode liefert null → echter „–“-Platzhalter statt rohem {city}.
        assertEquals("–", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `cache prevents a second geocode for nearby positions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val locationFlow = MutableStateFlow(location(lat = 52.52, lon = 13.405))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{road}")),
            locationFlow = locationFlow,
        )
        runCurrent()
        coVerify(exactly = 1) { geocoder.placenames(any(), any()) }

        // ~50 m weiter (unter der 500-m-Schwelle): kein erneuter Geocode.
        locationFlow.value = location(lat = 52.52045, lon = 13.405)
        runCurrent()
        coVerify(exactly = 1) { geocoder.placenames(any(), any()) }
        assertEquals("Kurfürstendamm", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `movement beyond the threshold triggers a new geocode`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val locationFlow = MutableStateFlow(location(lat = 52.52, lon = 13.405))
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{road}")),
            locationFlow = locationFlow,
        )
        runCurrent()

        // ~1.1 km weiter: neuer Geocode-Auftrag.
        locationFlow.value = location(lat = 52.53, lon = 13.405)
        runCurrent()
        coVerify(atLeast = 2) { geocoder.placenames(any(), any()) }
    }

    @Test
    fun `geocode error keeps the last known value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val failingGeocoder: GeocoderResolver = mockk {
            coEvery { placenames(any(), any()) } throws RuntimeException("backend down")
        }
        val locationFlow = MutableStateFlow(location())
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{city}")),
            locationFlow = locationFlow,
            geocoder = failingGeocoder,
        )
        runCurrent()

        // Erstaufruf schlägt fehl → „–“ (kein Crash, kein altes Ergebnis).
        assertEquals("–", viewModel.uiState.value.resolvedTemplate)

        // Späterer Erfolg → Wert erscheint.
        locationFlow.value = location(lat = 48.13, lon = 11.57)
        runCurrent()
        coEvery { failingGeocoder.placenames(any(), any()) } returns Placenames("r", "München", "D")
        locationFlow.value = location(lat = 48.20, lon = 11.60)
        runCurrent()
        assertEquals("München", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `null geocode result keeps the last known value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val flakyGeocoder: GeocoderResolver = mockk {
            coEvery { placenames(any(), any()) } returnsMany listOf(
                Placenames("A", "B", "C"),
                null,
            )
        }
        val locationFlow = MutableStateFlow(location())
        val viewModel = createViewModel(
            settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{city}")),
            locationFlow = locationFlow,
            geocoder = flakyGeocoder,
        )
        runCurrent()
        assertEquals("B", viewModel.uiState.value.resolvedTemplate)

        // Zweiter Standort (>500 m), Geocode liefert null → letzter Wert bleibt.
        locationFlow.value = location(lat = 48.13, lon = 11.57)
        runCurrent()
        assertEquals("B", viewModel.uiState.value.resolvedTemplate)
    }

    @Test
    fun `disabling the geo template stops further geocodes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsFlow = MutableStateFlow(AppSettings(widgetEnabled = true, widgetTemplate = "{road}"))
        val locationFlow = MutableStateFlow(location())
        val viewModel = createViewModel(settingsFlow, locationFlow)
        runCurrent()
        assertEquals("Kurfürstendamm", viewModel.uiState.value.resolvedTemplate)

        // Template ohne Geo-Variable: Pipeline stoppt — Standortwechsel löst keinen Geocode mehr aus.
        settingsFlow.value = AppSettings(widgetEnabled = true, widgetTemplate = "{time}")
        runCurrent()
        locationFlow.value = location(lat = 48.13, lon = 11.57)
        runCurrent()

        coVerify(exactly = 1) { geocoder.placenames(any(), any()) }
    }
}
