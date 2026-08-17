package com.vivid.core.location

import kotlinx.coroutines.flow.Flow

/** Eine Standortmessung des Text-/Info-Widgets — plattformneutral (kein android.location-Typ). */
data class WidgetLocation(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float,
    val hasSpeed: Boolean,
    val altitudeMeters: Double,
    val hasAltitude: Boolean,
    val timestampMillis: Long,
)

/**
 * Quelle für Standort-Updates des Text-/Info-Widgets. Abstrahiert, damit Tests
 * einen Fake liefern können (der Android-Provider nutzt [android.location.LocationManager]).
 */
interface LocationProvider {

    /**
     * Kalter Flow mit Standort-Updates. Bei fehlender Location-Permission oder fehlendem
     * Provider liefert der Flow keine Emissionen — das Widget zeigt dann „–“ für
     * GPS/Geschwindigkeit, während Uhrzeit/Datum weiterlaufen.
     */
    fun locationUpdates(): Flow<WidgetLocation>
}
