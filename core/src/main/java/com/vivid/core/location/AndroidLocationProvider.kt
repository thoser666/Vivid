package com.vivid.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Liefert Standort-Updates über den [LocationManager] (GPS-Provider). Läuft als
 * kalter Flow: erst beim Collect wird `requestLocationUpdates` registriert, bei
 * Cancellation wieder entfernt — kein explizites Start/Stop-Management nötig.
 */
@Singleton
class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Permission wird oben explizit geprüft (Guard erkennt das nicht).
    override fun locationUpdates(): Flow<WidgetLocation> = callbackFlow {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null || !hasPermission) {
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location -> trySend(location.toWidgetLocation()) }

        // Sofort die letzte bekannte Position liefern, falls vorhanden.
        runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
            .getOrNull()
            ?.let(listener::onLocationChanged)

        // Regelmäßige Updates (2 s) — GPS liefert dabei auch Geschwindigkeit.
        val registered = runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                MIN_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
        }.isSuccess

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun Location.toWidgetLocation(): WidgetLocation {
        val speed = if (hasSpeed()) speed else 0f
        val altitude = if (hasAltitude()) altitude else 0.0
        return WidgetLocation(
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speed,
            hasSpeed = hasSpeed(),
            altitudeMeters = altitude,
            hasAltitude = hasAltitude(),
            timestampMillis = time,
        )
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        const val MIN_DISTANCE_METERS = 0f
    }
}
