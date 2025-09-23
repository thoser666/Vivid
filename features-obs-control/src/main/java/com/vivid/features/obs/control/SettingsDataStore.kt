import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore(private val context: Context) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
        val OBS_HOST = stringPreferencesKey("obs_host")
        val OBS_PORT = stringPreferencesKey("obs_port")
        val OBS_PASSWORD = stringPreferencesKey("obs_password")
    }

    val obsHostFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[OBS_HOST]
        }

    val obsPortFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[OBS_PORT]
        }

    val obsPasswordFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[OBS_PASSWORD]
        }

    suspend fun saveObsSettings(host: String, port: String, password: String) {
        context.dataStore.edit { settings ->
            settings[OBS_HOST] = host
            settings[OBS_PORT] = port
            settings[OBS_PASSWORD] = password
        }
    }
}