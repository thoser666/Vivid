package com.vivid.core.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet das Zugriffs-Token für die Web-Remote-Control.
 *
 * Das Token wird einmalig erzeugt und über App-Starts hinweg in der
 * Preferences-DataStore persistiert, damit die URL / der Token für den
 * Nutzer stabil bleibt (z. B. für den Browser-Lesezeichen oder Obtainium).
 */
@Singleton
class RemoteControlTokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object PrefKeys {
        val REMOTE_CONTROL_TOKEN = stringPreferencesKey("remote_control_token")
    }

    /** Das gespeicherte Token als Flow (leer, wenn noch nie erzeugt). */
    val tokenFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[PrefKeys.REMOTE_CONTROL_TOKEN] ?: ""
    }

    /**
     * Liefert das persistierte Token oder erzeugt ein neues, falls keines existiert.
     */
    suspend fun getOrCreateToken(): String {
        val existing = dataStore.data.first()[PrefKeys.REMOTE_CONTROL_TOKEN]
        if (!existing.isNullOrBlank()) return existing
        val token = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[PrefKeys.REMOTE_CONTROL_TOKEN] = token
        }
        return token
    }
}
