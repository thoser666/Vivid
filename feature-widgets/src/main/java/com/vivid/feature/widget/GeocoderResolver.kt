package com.vivid.feature.widget

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Umgekehrt-geocodierte Ortsangaben für die Text-Widget-Variablen `{road}`, `{city}`, `{country}`. */
data class Placenames(
    val road: String,
    val city: String,
    val country: String,
) {
    companion object {
        /** Platzhalter, solange kein Geocode-Ergebnis vorliegt (Moblin-Stil). */
        const val UNKNOWN = "–"
    }
}

/**
 * Quelle für Reverse-Geocoding (lat/lon → Straßenname/Stadt/Land). Abstrahiert, damit
 * Tests einen Fake oder Robolectric-Shadow injizieren können.
 */
interface GeocoderResolver {
    /**
     * Löst [latitude]/[longitude] zu Ortsangaben auf. Liefert `null`, wenn kein
     * Ergebnis vorliegt (kein Backend, kein Treffer, Fehler) — der Aufrufer behält
     * dann den letzten bekannten Wert.
     */
    suspend fun placenames(latitude: Double, longitude: Double): Placenames?
}

/**
 * Reverse-Geocoding über [android.location.Geocoder] (Android-Systemdienst, Offline-
 * Fallback in den meisten Regionen). Blockierende API → [Dispatchers.IO]; vor dem Aufruf
 * wird `Geocoder.isPresent()` geprüft (Geräte ohne Geocoder-Backend liefern sonst
 * IOExceptions statt sauberer Nullen).
 *
 * API ≥ 33 nutzt den asynchronen [Geocoder.getFromLocation]-Listener-Pfad (nicht
 * deprecated); API < 33 bleibt eine `@Suppress("DEPRECATION")`-Fallback-Variante, da die
 * async-API erst ab 33 existiert (minSdk ist 24). Der verbleibende deprecated-Call ist
 * dort dokumentiert: docs/security-suppressions.md, Dismissal #474.
 */
@Singleton
class AndroidGeocoderResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeocoderResolver {

    /**
     * Fabrik für Geocoder-Instanzen. Default: echter System-Geocoder. Tests überschreiben
     * sie, um eine einzelne (geteilte) Instanz zu liefern, deren Shadow sie steuern können.
     */
    internal var geocoderFactory: (Context) -> Geocoder = { Geocoder(it, Locale.getDefault()) }

    override suspend fun placenames(latitude: Double, longitude: Double): Placenames? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null

            val geocoder = geocoderFactory(context)
            val addresses = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resolveAsync(geocoder, latitude, longitude)
                } else {
                    @Suppress("DEPRECATION") // API < 33: keine async-Alternative — siehe security-suppressions.md #476.
                    geocoder.getFromLocation(latitude, longitude, MAX_RESULTS)
                }
            }.getOrNull()

            addresses
                ?.firstOrNull()
                ?.let { address ->
                    Placenames(
                        road = address.thoroughfare ?: address.subLocality ?: "",
                        city = address.locality ?: address.subAdminArea ?: address.adminArea ?: "",
                        country = address.countryName ?: "",
                    )
                }
                ?.takeIf { it.road.isNotEmpty() || it.city.isNotEmpty() || it.country.isNotEmpty() }
        }

    /**
     * API-33+-Pfad: [Geocoder.getFromLocation] mit [Geocoder.GeocodeListener] statt der
     * deprecated-Sync-Variante. Der Listener feuert genau einmal (Ergebnis oder Fehler);
     * Kotlin-Style: Koordinaten gehen direkt als lat/lon in den Call.
     * Robolectric fliest [ShadowGeocoder] ein, der die Listener-Variante mit den
     * gesetzten Adressen bedient.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun resolveAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            latitude,
            longitude,
            MAX_RESULTS,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: List<Address>) {
                    continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    continuation.resume(emptyList())
                }
            },
        )
    }

    private companion object {
        const val MAX_RESULTS = 1
    }
}

/** Wandelt ein Geocode-Ergebnis in anzeigbare Werte mit „–“-Platzhaltern um. */
internal fun Placenames.toDisplayValues(): Triple<String, String, String> =
    Triple(
        road.ifBlank { Placenames.UNKNOWN },
        city.ifBlank { Placenames.UNKNOWN },
        country.ifBlank { Placenames.UNKNOWN },
    )
