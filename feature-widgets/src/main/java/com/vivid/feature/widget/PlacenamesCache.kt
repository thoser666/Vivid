package com.vivid.feature.widget

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cache für Reverse-Geocoding-Ergebnisse.
 *
 * Geocoding ist teuer (Systemdienst/Netzwerk) — das Widget bekommt GPS-Ticks alle
 * 2 s und darf nicht bei jedem Tick geocodieren. Der Cache liefert den letzten Wert
 * für [CACHE_TTL_MILLIS] oder solange sich die Position um weniger als
 * [MIN_DISTANCE_METERS] bewegt hat; danach wird neu geocodiert. Rein synchrone,
 * plattformfreie Logik → deterministisch testbar.
 */
class PlacenamesCache(
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    private var cached: Placenames? = null
    private var cachedAtMillis: Long = 0L
    private var cachedLatitude: Double = 0.0
    private var cachedLongitude: Double = 0.0

    /**
     * Liefert `true`, wenn für [latitude]/[longitude] zur aktuellen Zeit ein
     * Cache-Treffer vorliegt (noch gültig und nah genug an der gecachten Position).
     */
    fun isFresh(latitude: Double, longitude: Double): Boolean {
        val entry = cached ?: return false
        val elapsed = now() - cachedAtMillis
        if (elapsed >= CACHE_TTL_MILLIS) return false
        return distanceMeters(cachedLatitude, cachedLongitude, latitude, longitude) <= MIN_DISTANCE_METERS
    }

    /** Gecachten Wert lesen (auch wenn nicht mehr fresh — letzter-bekannter-Fallback). */
    fun get(): Placenames? = cached

    /** Geocode-Ergebnis unter der Position ablegen. */
    fun put(latitude: Double, longitude: Double, value: Placenames) {
        cached = value
        cachedAtMillis = now()
        cachedLatitude = latitude
        cachedLongitude = longitude
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        haversineMeters(lat1, lon1, lat2, lon2)

    companion object {
        /** Nach 10 Minuten wird neu geocodiert (auch ohne Bewegung — z. B. Stadtwechsel im Zug). */
        const val CACHE_TTL_MILLIS = 10L * 60 * 1_000

        /** Unter 500 m Bewegung bleibt der letzte Wert gültig (Innerorts in ~1 min kaum weiter). */
        const val MIN_DISTANCE_METERS = 500.0

        private const val EARTH_RADIUS_METERS = 6_371_000.0

        /** Grobe Großkreis-Distanz (Haversine) — für eine 500-m-Schwelle ausreichend genau. */
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
