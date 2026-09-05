package com.vivid.feature.chat.twitch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * DataStore-basierte [TwitchTokenStore]: Access- und Refresh-Token werden
 * einzeln über den injizierten [TokenCipher] verschlüsselt abgelegt; nur die
 * Ablaufzeit (`Expiry`, kein Zugangsdaten) liegt unverschlüsselt.
 */
@Singleton
class DataStoreTwitchTokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : TwitchTokenStore {

    private object PrefKeys {
        val ACCESS_TOKEN = stringPreferencesKey("twitch_token_access")
        val REFRESH_TOKEN = stringPreferencesKey("twitch_token_refresh")
        val EXPIRES_AT = longPreferencesKey("twitch_token_expires_at")
        val SCOPES = stringPreferencesKey("twitch_token_scopes")
    }

    override suspend fun loadSession(): TwitchTokenSession? {
        val prefs = dataStore.data.first()
        val access = prefs[PrefKeys.ACCESS_TOKEN]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs[PrefKeys.REFRESH_TOKEN]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: return null
        return TwitchTokenSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = prefs[PrefKeys.EXPIRES_AT] ?: 0L,
            scopes = prefs[PrefKeys.SCOPES].orEmpty().split(',').filter { it.isNotBlank() },
        )
    }

    override suspend fun saveSession(session: TwitchTokenSession) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.ACCESS_TOKEN] = cipher.encrypt(session.accessToken)
            prefs[PrefKeys.REFRESH_TOKEN] = cipher.encrypt(session.refreshToken)
            prefs[PrefKeys.EXPIRES_AT] = session.expiresAtMillis
            prefs[PrefKeys.SCOPES] = session.scopes.joinToString(",")
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(PrefKeys.ACCESS_TOKEN)
            prefs.remove(PrefKeys.REFRESH_TOKEN)
            prefs.remove(PrefKeys.EXPIRES_AT)
            prefs.remove(PrefKeys.SCOPES)
        }
    }
}