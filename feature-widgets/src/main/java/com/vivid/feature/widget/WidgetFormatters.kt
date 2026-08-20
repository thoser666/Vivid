package com.vivid.feature.widget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Pure Formatierungs-Helfer des Text-/Info-Widgets — ohne Android-Typen, damit sie
 * unit-testbar sind. Bewusst `java.text.SimpleDateFormat` statt `java.time`:
 * minSdk 24 ohne Core-Library-Desugaring (java.time bräuchte API 26).
 */
object WidgetFormatters {

    /** Uhrzeit im 24-h-Format (z. B. `14:05:32`). */
    fun formatTime(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm:ss", Locale.GERMANY).apply { timeZone = zone }
            .format(Date(epochMillis))

    /** Datum (z. B. `17.08.2026`). */
    fun formatDate(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { timeZone = zone }
            .format(Date(epochMillis))

    /**
     * Koordinaten mit Himmelsrichtung und Punkt als Dezimaltrenner (GPS-Standard,
     * z. B. `52.5200° N, 13.4050° O`).
     */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "O" else "W"
        val lat = String.format(Locale.ROOT, "%.4f", abs(latitude))
        val lon = String.format(Locale.ROOT, "%.4f", abs(longitude))
        return "$lat° $latDir, $lon° $lonDir"
    }

    /** Geschwindigkeit in km/h (deutsche Dezimaltrennung); `null` oder negativ → `–`. */
    fun formatSpeed(metersPerSecond: Float?): String {
        if (metersPerSecond == null || metersPerSecond < 0f) return "–"
        return String.format(Locale.GERMANY, "%.1f", metersPerSecond * 3.6f) + " km/h"
    }

    /** Höhenmeter (ganzzahlig); `null` → `–`. */
    fun formatAltitude(altitudeMeters: Double?): String {
        if (altitudeMeters == null) return "–"
        return String.format(Locale.ROOT, "%.0f", altitudeMeters) + " m"
    }
}
