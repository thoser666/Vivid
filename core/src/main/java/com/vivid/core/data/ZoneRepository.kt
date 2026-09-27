package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistiert die manuellen Anonymisierungs-Zonen (Stufe 1 der Skizze
 * docs/architecture/privacy-anonymization.md) in derselben Preferences-
 * DataStore wie [SettingsRepository]/[SceneRepository].
 *
 * Die Zonen-Liste liegt als JSON-String unter einem einzigen Key
 * (`privacy_zones_json`), der Master-Toggle „Anonymisierung“ als
 * `privacy_enabled`. JSON (kotlinx.serialization) statt Einzel-Keys hält das
 * Schema erweiterbar: neue Felder in [PrivacyZone] sind abwärtskompatibel
 * (decode mit `ignoreUnknownKeys` analog zum Rest des Projekts).
 *
 * Die Liste ist auf [PrivacyZone.MAX_ZONES] Einträge begrenzt (der
 * Privacy-Shader trägt 8 Ellipsen-Slots: 4 Zonen + 4 Gesichter ab P2) —
 * ältere Einträge fallen beim Überschreiten hinten heraus.
 */
@Singleton
class ZoneRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object PrefKeys {
        val ZONES_JSON = stringPreferencesKey("privacy_zones_json")
        val PRIVACY_ENABLED = booleanPreferencesKey("privacy_enabled")
    }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Alle gespeicherten Zonen in Anlage-Reihenfolge (max. [PrivacyZone.MAX_ZONES]). */
    val zonesFlow: Flow<List<PrivacyZone>> = dataStore.data.map { prefs ->
        decodeZones(prefs[PrefKeys.ZONES_JSON] ?: "")
    }

    /** Master-Toggle „Anonymisierung“ (Default aus — der Streamer entscheidet). */
    val privacyEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PrefKeys.PRIVACY_ENABLED] ?: false
    }

    /**
     * Ersetzt die komplette Zonen-Liste (der Zonen-Editor editiert immer die
     * ganze Liste; das ist einfacher und race-freier als Einzel-CRUD).
     * Mehr als [PrivacyZone.MAX_ZONES] Einträge werden auf die ersten
     * [PrivacyZone.MAX_ZONES] beschnitten.
     */
    suspend fun setZones(zones: List<PrivacyZone>) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.ZONES_JSON] = json.encodeToString(zones.take(PrivacyZone.MAX_ZONES))
        }
    }

    /** Setzt den Master-Toggle „Anonymisierung“. */
    suspend fun setPrivacyEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.PRIVACY_ENABLED] = enabled
        }
    }

    private fun decodeZones(raw: String): List<PrivacyZone> {
        if (raw.isBlank()) return emptyList()
        // Defensiv: eine beschädigte JSON-Zelle (z. B. durch einen Abbruch beim
        // Schreiben) darf die App nicht am Start hindern — leer statt Crash.
        return runCatching {
            json.decodeFromString<List<PrivacyZone>>(raw)
        }.getOrNull().orEmpty().take(PrivacyZone.MAX_ZONES)
    }
}
