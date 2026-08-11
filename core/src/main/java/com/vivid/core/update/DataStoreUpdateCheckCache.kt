package com.vivid.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-Implementierung des [UpdateCheckCache]. Nutzt die vom
 * [com.vivid.core.di.DataStoreModule] bereitgestellte Preferences-DataStore
 * (gleiche Datei wie [com.vivid.core.data.SettingsRepository]).
 */
@Singleton
class DataStoreUpdateCheckCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UpdateCheckCache {

    private object PrefKeys {
        val VERSION = stringPreferencesKey("update_check_version")
        val LATEST_VERSION = stringPreferencesKey("update_check_latest_version")
        val RELEASE_URL = stringPreferencesKey("update_check_release_url")
        val RELEASE_NOTES = stringPreferencesKey("update_check_release_notes")
        val TIMESTAMP = longPreferencesKey("update_check_timestamp")
    }

    override suspend fun load(): UpdateCheckCache.CachedCheck? {
        val prefs = dataStore.data.first()
        val version = prefs[PrefKeys.VERSION] ?: return null
        val latestVersion = prefs[PrefKeys.LATEST_VERSION] ?: return null
        val result = prefs[PrefKeys.RELEASE_URL]?.let { url ->
            UpdateCheckResult.UpdateAvailable(
                latestVersion = latestVersion,
                releaseUrl = url,
                releaseNotes = prefs[PrefKeys.RELEASE_NOTES].orEmpty(),
            )
        } ?: UpdateCheckResult.UpToDate(latestVersion = latestVersion)
        return UpdateCheckCache.CachedCheck(
            installedVersion = version,
            result = result,
            timestampMillis = prefs[PrefKeys.TIMESTAMP] ?: 0L,
        )
    }

    override suspend fun save(
        installedVersion: String,
        result: UpdateCheckResult,
        timestampMillis: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.VERSION] = installedVersion
            prefs[PrefKeys.TIMESTAMP] = timestampMillis
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    prefs[PrefKeys.LATEST_VERSION] = result.latestVersion
                    prefs[PrefKeys.RELEASE_URL] = result.releaseUrl
                    prefs[PrefKeys.RELEASE_NOTES] = result.releaseNotes
                }
                is UpdateCheckResult.UpToDate -> {
                    prefs[PrefKeys.LATEST_VERSION] = result.latestVersion
                    prefs.remove(PrefKeys.RELEASE_URL) // UpToDate ⇒ kein Release-Link mehr
                    prefs.remove(PrefKeys.RELEASE_NOTES)
                }
                is UpdateCheckResult.Error -> Unit // Fehler nie cachen
            }
        }
    }
}
