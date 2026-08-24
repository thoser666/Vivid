package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * Persistiert die umschaltbaren Szenen (Moblin: Basic Scenes) in derselben
 * Preferences-DataStore wie [SettingsRepository].
 *
 * Die Szenen-Liste liegt als JSON-String unter einem einzigen Key
 * (`scenes_json`), die zuletzt angewendete Szene als `active_scene_id`.
 * JSON (kotlinx.serialization) statt Einzel-Keys hält das Schema erweiterbar:
 * neue Felder in [StreamScene] sind abwärtskompatibel (decode mit
 * `ignoreUnknownKeys` analog zum Rest des Projekts).
 */
@Singleton
class SceneRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object PrefKeys {
        val SCENES_JSON = stringPreferencesKey("scenes_json")
        val ACTIVE_SCENE_ID = stringPreferencesKey("active_scene_id")
    }

    private val json = Json { encodeDefaults = true }

    /** Alle gespeicherten Szenen in Anlage-Reihenfolge. */
    val scenesFlow: Flow<List<StreamScene>> = dataStore.data.map { prefs ->
        decodeScenes(prefs[PrefKeys.SCENES_JSON] ?: "")
    }

    /** ID der zuletzt angewendeten Szene (null = keine aktiv). */
    val activeSceneIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[PrefKeys.ACTIVE_SCENE_ID]?.takeIf { it.isNotBlank() }
    }

    /** Legt eine Szene an oder ersetzt eine bestehende mit derselben [StreamScene.id]. */
    suspend fun saveScene(scene: StreamScene) {
        val scenes = scenesFlow.first()
        persistScenes(scenes.filter { it.id != scene.id } + scene)
    }

    /** Löscht eine Szene; war sie die aktive, wird auch die aktive ID entfernt. */
    suspend fun deleteScene(id: String) {
        val scenes = scenesFlow.first()
        persistScenes(scenes.filter { it.id != id })
        if (activeSceneIdFlow.first() == id) {
            setActiveScene(null)
        }
    }

    /** Markiert eine Szene als aktiv (null = keine aktiv). */
    suspend fun setActiveScene(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(PrefKeys.ACTIVE_SCENE_ID)
            } else {
                prefs[PrefKeys.ACTIVE_SCENE_ID] = id
            }
        }
    }

    private suspend fun persistScenes(scenes: List<StreamScene>) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.SCENES_JSON] = json.encodeToString(scenes)
        }
    }

    private fun decodeScenes(raw: String): List<StreamScene> {
        if (raw.isBlank()) return emptyList()
        // Defensiv: eine beschädigte JSON-Zelle (z. B. durch einen Abbruch beim
        // Schreiben) darf die App nicht am Start hindern — leer statt Crash.
        return runCatching {
            json.decodeFromString<List<StreamScene>>(raw)
        }.getOrNull() ?: emptyList()
    }
}