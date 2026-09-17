package com.vivid.feature.widget

/**
 * Ersetzt Variablen-Platzhalter in einem Widget-Template durch aktuelle Werte.
 *
 * Unterstützte Variablen: `{time}`, `{date}`, `{speed}`, `{altitude}`, `{lat}`, `{lon}`,
 * `{road}`, `{city}`, `{country}`, `{timer}`, `{distance}`, `{gforce}`.
 * Unbekannte Variablen bleiben unverändert (für Erweiterbarkeit).
 */
object WidgetVariableResolver {

    private val VARIABLE_PATTERN = Regex("\\{(\\w+)}")

    /**
     * Löst ein Template auf, indem `{var}`-Platzhalter durch die zugehörigen
     * Werte aus [values] ersetzt werden. Unbekannte Variablen bleiben unverändert.
     *
     * Beispiel:
     * ```
     * resolve("{time} | {speed}", mapOf("time" to "14:05:32", "speed" to "52.3 km/h"))
     * // → "14:05:32 | 52.3 km/h"
     * ```
     */
    fun resolve(template: String, values: Map<String, String>): String =
        VARIABLE_PATTERN.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }

    /**
     * Erzeugt eine Map aller unterstützten Variablen aus den aktuell formatierten Werten.
     *
     * Die Geocoding-Werte (`road`/`city`/`country`) und die Trip-Werte (`timer`/`distance`/
     * `gforce`) kommen aus dem ViewModel — der Resolver selbst ist rein synchron und kennt
     * weder Android-Geocoder noch Sensoren.
     */
    fun currentValues(
        time: String,
        date: String,
        speed: String,
        altitude: String,
        latitude: Double,
        longitude: Double,
        road: String = Placenames.UNKNOWN,
        city: String = Placenames.UNKNOWN,
        country: String = Placenames.UNKNOWN,
        timer: String = "–",
        distance: String = "–",
        gforce: String = "–",
    ): Map<String, String> = mapOf(
        "time" to time,
        "date" to date,
        "speed" to speed,
        "altitude" to altitude,
        "lat" to latitude.toString(),
        "lon" to longitude.toString(),
        "road" to road,
        "city" to city,
        "country" to country,
        "timer" to timer,
        "distance" to distance,
        "gforce" to gforce,
    )
}
