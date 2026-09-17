package com.vivid.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.location.LocationProvider
import com.vivid.core.location.WidgetLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Anzeige-Zustand des Text-/Info-Widgets (alle Felder bereits formatiert). */
data class TextInfoWidgetUiState(
    val enabled: Boolean = false,
    val showTime: Boolean = true,
    val showLocation: Boolean = true,
    val showSpeed: Boolean = true,
    val showAltitude: Boolean = false,
    val time: String = "--:--:--",
    val date: String = "",
    val location: String = "",
    val speed: String = "",
    val altitude: String = "",
    /** optionales Template — wenn gesetzt, wird das resolved Template statt der Einzelfelder angezeigt. */
    val template: String = "",
    val resolvedTemplate: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val road: String = Placenames.UNKNOWN,
    val city: String = Placenames.UNKNOWN,
    val country: String = Placenames.UNKNOWN,
    /** Stoppuhr seit Widget-Aktivierung (HH:MM:SS) — Vorlage `{timer}`. */
    val timer: String = "–",
    /** Akkumulierte Distanz seit Widget-Aktivierung — Vorlage `{distance}`. */
    val distance: String = "–",
    /** G-Kraft aus GPS-Geschwindigkeits-Deltas — Vorlage `{gforce}`. */
    val gforce: String = "–",
)

/**
 * Steuert das Text-/Info-Widget über der Streaming-Vorschau: Uhrzeit/Datum aus einem
 * Sekunden-Ticker, GPS-Koordinaten + Geschwindigkeit aus dem [LocationProvider].
 * Location-Updates werden nur gesammelt, wenn das Widget aktiv ist und Standortfelder zeigt.
 *
 * Geocoding-Variablen (`{road}`, `{city}`, `{country}`) werden nur aufgelöst, wenn das
 * Template sie tatsächlich verwendet — und dann gedrosselt über [PlacenamesCache]
 * (TTL + 500-m-Schwelle), damit nicht bei jedem GPS-Tick reverse-geocodiert wird.
 *
 * Trip-Variablen: `{timer}` (Stoppuhr ab Widget-Aktivierung), `{distance}` (Haversine-
 * Summe der GPS-Deltas) und `{gforce}` (Beschleunigung aus Geschwindigkeits-Deltas,
 * GPS-Näherung statt Sensor — keine Hardware-Abhängigkeit). Beim Deaktivieren des
 * Widgets werden Timer, Distanz und G-Force zurückgesetzt.
 */
@HiltViewModel
class TextInfoWidgetViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val geocoderResolver: GeocoderResolver,
) : ViewModel() {

    /** Test-Hook für eine feste Uhr (Standard: Systemzeit). */
    internal var now: () -> Long = { System.currentTimeMillis() }

    /**
     * Tick-Quelle für die Uhr. Standard: jede Sekunde ein Tick. Tests ersetzen sie
     * durch einen endlichen Flow, damit der Test-Scheduler nicht endlos weiterläuft.
     */
    internal var ticker: () -> Flow<Long> = ::defaultTicker

    /** Test-Hook: Cache neu (deterministische TTL in Tests). */
    internal val placenamesCache = PlacenamesCache { now() }

    private fun defaultTicker(): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(TICK_MILLIS)
        }
    }

    private val _uiState = MutableStateFlow(TextInfoWidgetUiState())
    val uiState: StateFlow<TextInfoWidgetUiState> = _uiState.asStateFlow()

    /** Aktivierungs-Zeitstempel der Stoppuhr (null → läuft nicht). */
    private var timerStartedAt: Long? = null

    /** Letzte Position für die Distanz-Akkumulation (null → kein Bezugspunkt). */
    private var lastDistanceLocation: WidgetLocation? = null

    /** Akkumulierte Strecke (Haversine-Summe) seit Aktivierung in Metern. */
    private var accumulatedDistanceMeters: Double = 0.0

    init {
        // Settings übernehmen (Toggle + sichtbare Felder + Template).
        viewModelScope.launch {
            var previouslyEnabled = false
            settingsRepository.appSettingsFlow.collect { settings ->
                val enabled = settings.widgetEnabled
                if (enabled && !previouslyEnabled) {
                    // Widget (wieder) aktiviert → Trip-Uhren neu starten (Timer verankert
                    // am nächsten Tick — nicht an now(), damit Tests deterministisch bleiben).
                    timerStartedAt = null
                    lastDistanceLocation = null
                    accumulatedDistanceMeters = 0.0
                } else if (!enabled && previouslyEnabled) {
                    // Deaktiviert → Timer/Distanz/G-Force zurücksetzen („Tageskilometer“-Semantik).
                    timerStartedAt = null
                    lastDistanceLocation = null
                    accumulatedDistanceMeters = 0.0
                    _uiState.update {
                        it.copy(timer = "–", distance = "–", gforce = "–")
                    }
                }
                previouslyEnabled = enabled
                _uiState.update {
                    it.copy(
                        enabled = settings.widgetEnabled,
                        showTime = settings.widgetShowTime,
                        showLocation = settings.widgetShowLocation,
                        showSpeed = settings.widgetShowSpeed,
                        showAltitude = settings.widgetShowAltitude,
                        template = settings.widgetTemplate,
                    )
                }
                resolveTemplate()
            }
        }

        // Uhr: jede Sekunde aktualisieren (läuft unabhängig vom Toggle — das Widget
        // selbst blendet sich bei `enabled = false` aus). Der Timer startet am ersten
        // Tick nach Aktivierung (deterministisch, gleiche Takt-Quelle wie die Uhr).
        viewModelScope.launch {
            ticker().collect { t ->
                val timer = if (timerStartedAt != null) {
                    WidgetFormatters.formatTimer(t - timerStartedAt!!)
                } else if (_uiState.value.enabled) {
                    timerStartedAt = t
                    WidgetFormatters.formatTimer(0L)
                } else {
                    "–"
                }
                _uiState.update {
                    it.copy(
                        time = WidgetFormatters.formatTime(t),
                        date = WidgetFormatters.formatDate(t),
                        timer = timer,
                    )
                }
                resolveTemplate()
            }
        }

        // Standort: nur sammeln, wenn das Widget aktiv ist und GPS/Geschwindigkeit/Template zeigt.
        viewModelScope.launch {
            combine(
                settingsRepository.appSettingsFlow.map { settings ->
                    settings.widgetEnabled && (
                        settings.widgetShowLocation || settings.widgetShowSpeed ||
                            settings.widgetShowAltitude || settings.widgetTemplate.isNotBlank()
                        )
                },
                locationProvider.locationUpdates(),
            ) { active, location -> active to location }
                .collect { (active, location) ->
                    if (active) {
                        // Distanz: Haversine-Delta zur letzten Position akkumulieren.
                        val previous = lastDistanceLocation
                        if (previous != null) {
                            accumulatedDistanceMeters += PlacenamesCache.haversineMeters(
                                previous.latitude, previous.longitude, location.latitude, location.longitude,
                            )
                        }
                        lastDistanceLocation = location

                        // G-Force: Beschleunigung aus Geschwindigkeits-Deltas (GPS-Näherung).
                        var gforceValue: Double? = null
                        if (location.hasSpeed && previous?.hasSpeed == true && location.timestampMillis > previous.timestampMillis) {
                            val deltaSeconds = (location.timestampMillis - previous.timestampMillis) / 1_000.0
                            val deltaSpeed = location.speedMetersPerSecond - previous.speedMetersPerSecond
                            if (deltaSeconds > 0) {
                                gforceValue = deltaSpeed / deltaSeconds / GRAVITY_G
                            }
                        }

                        _uiState.update {
                            it.copy(
                                location = WidgetFormatters.formatCoordinates(location.latitude, location.longitude),
                                speed = WidgetFormatters.formatSpeed(
                                    if (location.hasSpeed) location.speedMetersPerSecond else null,
                                ),
                                altitude = WidgetFormatters.formatAltitude(
                                    if (location.hasAltitude) location.altitudeMeters else null,
                                ),
                                latitude = location.latitude,
                                longitude = location.longitude,
                                distance = WidgetFormatters.formatDistance(accumulatedDistanceMeters),
                                gforce = WidgetFormatters.formatGForce(gforceValue),
                            )
                        }
                        resolveTemplate()
                    }
                }
        }

        // Geocoding: nur wenn das Template {road}/{city}/{country} enthält, auf jedem
        // neuen Standort prüfen (Cache entscheidet über frisch/alt). Vorheriger Job wird
        // abgebrochen — ein GPS-Sturm (Updates alle 2 s) stapelt keine Anfragen.
        viewModelScope.launch {
            combine(
                settingsRepository.appSettingsFlow.map { settings ->
                    settings.widgetEnabled && settings.widgetTemplate.contains(GEO_VARIABLE_PATTERN)
                }.distinctUntilChanged(),
                locationProvider.locationUpdates(),
            ) { needed, location -> needed to location }
                .collect { (needed, location) ->
                    if (!needed) {
                        geocodeJob?.cancel()
                        geocodeJob = null
                        return@collect
                    }
                    requestPlacenames(location.latitude, location.longitude)
                }
        }
    }

    private var geocodeJob: Job? = null

    /**
     * Standort gegen den Cache prüfen und ggf. einen Geocode-Auftrag starten.
     * Cache-Hit → Platzhalter sofort aus dem Cache befüllen (ohne IO).
     */
    private fun requestPlacenames(latitude: Double, longitude: Double) {
        if (placenamesCache.isFresh(latitude, longitude)) {
            applyPlacenames(placenamesCache.get())
            return
        }
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            val result = runCatching { geocoderResolver.placenames(latitude, longitude) }
                .getOrNull()
            if (result != null) {
                placenamesCache.put(latitude, longitude, result)
            }
            applyPlacenames(result ?: placenamesCache.get())
        }
    }

    /** Geocode-Ergebnis (oder „–“-Platzhalter) in den UiState schreiben. */
    private fun applyPlacenames(result: Placenames?) {
        val (road, city, country) = result?.toDisplayValues()
            ?: Triple(Placenames.UNKNOWN, Placenames.UNKNOWN, Placenames.UNKNOWN)
        _uiState.update { it.copy(road = road, city = city, country = country) }
        resolveTemplate()
    }

    /** Aktuelles Template mit den aktuellen Werten auflösen. */
    private fun resolveTemplate() {
        val s = _uiState.value
        if (s.template.isBlank()) {
            _uiState.update { it.copy(resolvedTemplate = "") }
            return
        }
        val values = WidgetVariableResolver.currentValues(
            time = s.time,
            date = s.date,
            speed = s.speed,
            altitude = s.altitude,
            latitude = s.latitude,
            longitude = s.longitude,
            road = s.road,
            city = s.city,
            country = s.country,
            timer = s.timer,
            distance = s.distance,
            gforce = s.gforce,
        )
        _uiState.update { it.copy(resolvedTemplate = WidgetVariableResolver.resolve(s.template, values)) }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L

        /** Standard-Erdbeschleunigung für die G-Force-Ableitung aus GPS-Geschwindigkeits-Deltas. */
        const val GRAVITY_G = 9.80665

        /** Template-Substring-Suche: {road}, {city} oder {country}. */
        val GEO_VARIABLE_PATTERN = Regex("\\{(road|city|country)}")
    }
}
