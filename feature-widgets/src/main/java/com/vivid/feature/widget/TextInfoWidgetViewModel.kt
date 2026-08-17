package com.vivid.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val time: String = "--:--:--",
    val date: String = "",
    val location: String = "",
    val speed: String = "",
)

/**
 * Steuert das Text-/Info-Widget über der Streaming-Vorschau: Uhrzeit/Datum aus einem
 * Sekunden-Ticker, GPS-Koordinaten + Geschwindigkeit aus dem [LocationProvider].
 * Location-Updates werden nur gesammelt, wenn das Widget aktiv ist und Standortfelder zeigt.
 */
@HiltViewModel
class TextInfoWidgetViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    /** Test-Hook für eine feste Uhr (Standard: Systemzeit). */
    internal var now: () -> Long = { System.currentTimeMillis() }

    /**
     * Tick-Quelle für die Uhr. Standard: jede Sekunde ein Tick. Tests ersetzen sie
     * durch einen endlichen Flow, damit der Test-Scheduler nicht endlos weiterläuft.
     */
    internal var ticker: () -> Flow<Long> = ::defaultTicker

    private fun defaultTicker(): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(TICK_MILLIS)
        }
    }

    private val _uiState = MutableStateFlow(TextInfoWidgetUiState())
    val uiState: StateFlow<TextInfoWidgetUiState> = _uiState.asStateFlow()

    init {
        // Settings übernehmen (Toggle + sichtbare Felder).
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.widgetEnabled,
                        showTime = settings.widgetShowTime,
                        showLocation = settings.widgetShowLocation,
                        showSpeed = settings.widgetShowSpeed,
                    )
                }
            }
        }

        // Uhr: jede Sekunde aktualisieren (läuft unabhängig vom Toggle — das Widget
        // selbst blendet sich bei `enabled = false` aus).
        viewModelScope.launch {
            ticker().collect { t ->
                _uiState.update {
                    it.copy(
                        time = WidgetFormatters.formatTime(t),
                        date = WidgetFormatters.formatDate(t),
                    )
                }
            }
        }

        // Standort: nur sammeln, wenn das Widget aktiv ist und GPS/Geschwindigkeit zeigt.
        viewModelScope.launch {
            combine(
                settingsRepository.appSettingsFlow.map { settings ->
                    settings.widgetEnabled && (settings.widgetShowLocation || settings.widgetShowSpeed)
                },
                locationProvider.locationUpdates(),
            ) { active, location -> active to location }
                .collect { (active, location) ->
                    if (active) {
                        _uiState.update {
                            it.copy(
                                location = WidgetFormatters.formatCoordinates(location.latitude, location.longitude),
                                speed = WidgetFormatters.formatSpeed(
                                    if (location.hasSpeed) location.speedMetersPerSecond else null,
                                ),
                            )
                        }
                    }
                }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
