package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val scenesFlow: Flow<List<StreamScene>> = dataStore.data.map { decodeScenes(it) }
    val widgetsFlow: Flow<List<StreamWidget>> = dataStore.data.map { decodeWidgets(it) }
    val savedLayoutFlow: Flow<SceneWidget?> = dataStore.data.map { prefs ->
        prefs[SAVED_LAYOUT]?.let { runCatching { json.decodeFromString<SceneWidget>(it) }.getOrNull() }
    }
    suspend fun saveLayout(layout: SceneWidget) { dataStore.edit { it[SAVED_LAYOUT] = json.encodeToString(layout.normalized()) } }
    val activeSceneIdFlow: Flow<String?> = dataStore.data.map { it[ACTIVE_SCENE_ID]?.takeIf(String::isNotBlank) }
    val libraryInitializedFlow: Flow<Boolean> = dataStore.data.map { it[WIDGETS_JSON] != null }

    suspend fun saveScene(scene: StreamScene) = dataStore.edit { prefs ->
        val scenes = decodeScenes(prefs)
        prefs[SCENES_JSON] = json.encodeToString(if (scenes.any { it.id == scene.id }) {
            scenes.map { if (it.id == scene.id) scene else it }
        } else scenes + scene)
    }.let { }

    suspend fun deleteScene(id: String) = dataStore.edit { prefs ->
        prefs[SCENES_JSON] = json.encodeToString(decodeScenes(prefs).filterNot { it.id == id })
        if (prefs[ACTIVE_SCENE_ID] == id) prefs.remove(ACTIVE_SCENE_ID)
    }.let { }

    suspend fun setActiveScene(id: String?) = dataStore.edit { prefs ->
        if (id == null) prefs.remove(ACTIVE_SCENE_ID) else prefs[ACTIVE_SCENE_ID] = id
    }.let { }

    suspend fun reorderScenes(ids: List<String>) = dataStore.edit { prefs ->
        prefs[SCENES_JSON] = json.encodeToString(order(decodeScenes(prefs), ids) { it.id })
    }.let { }

    suspend fun saveWidget(widget: StreamWidget) = saveWidgetWithSceneVisibility(widget, emptyMap())

    /** Global enable changes update every existing placement; content-only edits keep scene visibility. */
    suspend fun saveWidgetWithSceneVisibility(widget: StreamWidget, visibility: Map<String, Boolean>) {
        dataStore.edit { prefs ->
            val widgets = decodeWidgets(prefs)
            val previous = widgets.find { it.id == widget.id }
            val globalVisibility = previous?.takeIf { it.enabled != widget.enabled }?.let { widget.enabled }
            prefs[WIDGETS_JSON] = json.encodeToString(if (widgets.any { it.id == widget.id }) {
                widgets.map { if (it.id == widget.id) widget else it }
            } else widgets + widget)
            if (globalVisibility != null || visibility.isNotEmpty()) {
                prefs[SCENES_JSON] = json.encodeToString(decodeScenes(prefs).map { scene ->
                    val enabled = globalVisibility ?: visibility[scene.id] ?: return@map scene
                    val placements = scene.widgets.orEmpty()
                    if (placements.any { it.widgetId == widget.id }) {
                        scene.copy(widgets = placements.map { if (it.widgetId == widget.id) it.copy(enabled = enabled) else it })
                    } else if (visibility[scene.id] == true) {
                        // A global toggle changes existing placements without adding the widget to new scenes.
                        scene.copy(widgets = placements + SceneWidget(widget.id, enabled = enabled))
                    } else scene
                })
            }
        }
    }

    suspend fun reorderWidgets(ids: List<String>) = dataStore.edit { prefs ->
        prefs[WIDGETS_JSON] = json.encodeToString(order(decodeWidgets(prefs), ids) { it.id })
    }.let { }

    suspend fun deleteWidget(id: String) = dataStore.edit { prefs ->
        prefs[WIDGETS_JSON] = json.encodeToString(decodeWidgets(prefs).filterNot { it.id == id })
        prefs[SCENES_JSON] = json.encodeToString(decodeScenes(prefs).map { scene ->
            scene.copy(widgets = scene.widgets?.filterNot { it.widgetId == id })
        })
    }.let { }

    /** One transaction; old scene-specific text settings remain separate definitions. */
    suspend fun initializeLibrary(settings: AppSettings, defaultName: String, names: Map<WidgetType, String>) {
        dataStore.edit { prefs ->
            if (prefs[WIDGETS_JSON] != null) {
                migrateLegacyPomodoro(prefs, settings, names.getValue(WidgetType.POMODORO))
                return@edit
            }
            val config = WidgetConfiguration.from(settings)
            val types = WidgetType.entries.filterNot { it == WidgetType.BROWSER }
            val widgets = types.map { type ->
                StreamWidget("legacy-${type.name}", names.getValue(type), type, configuration = config)
            }.toMutableList()
            val visible = mapOf(
                WidgetType.TEXT to settings.widgetEnabled, WidgetType.CHAT to settings.chatOverlayEnabled,
                WidgetType.IMAGE to settings.imageWidgetEnabled, WidgetType.QR_CODE to settings.qrCodeWidgetEnabled,
                WidgetType.SLIDESHOW to settings.slideshowWidgetEnabled, WidgetType.GRID to settings.gridOverlayEnabled,
                WidgetType.BATTERY to settings.batteryEnabled, WidgetType.SUBTITLES to settings.subtitlesEnabled,
                WidgetType.POMODORO to settings.pomodoroEnabled,
            )
            widgets.replaceAll { it.copy(enabled = visible[it.type] == true) }
            fun placements() = widgets.take(types.size).filter { visible[it.type] == true }.mapIndexed { index, widget ->
                SceneWidget(widget.id, x = if (index % 2 == 0) 0f else 70f, y = (index / 2 * 20f).coerceAtMost(80f))
            }
            val old = decodeScenes(prefs)
            val scenes = if (old.isEmpty()) listOf(StreamScene("default", defaultName, widgets = placements())) else old.map { scene ->
                // Explicit placements belong to managed scenes, including deliberately empty new scenes.
                if (scene.widgets != null) return@map scene
                val text = StreamWidget("legacy-text-${scene.id}", "${names.getValue(WidgetType.TEXT)} · ${scene.name}", WidgetType.TEXT,
                    enabled = scene.widgetEnabled,
                    configuration = config.copy(widgetShowTime = scene.widgetShowTime,
                        widgetShowLocation = scene.widgetShowLocation, widgetShowSpeed = scene.widgetShowSpeed,
                        widgetShowAltitude = scene.widgetShowAltitude, widgetTemplate = scene.widgetTemplate))
                widgets.add(text)
                scene.copy(widgets = placements().filterNot { it.widgetId == "legacy-TEXT" } +
                    if (scene.widgetEnabled) listOf(SceneWidget(text.id, x = 70f, y = 70f)) else emptyList())
            }
            prefs[WIDGETS_JSON] = json.encodeToString(widgets)
            prefs[SCENES_JSON] = json.encodeToString(scenes)
            if (prefs[ACTIVE_SCENE_ID] == null) prefs[ACTIVE_SCENE_ID] = scenes.first().id
            prefs[POMODORO_MIGRATED] = true
        }
    }

    private fun migrateLegacyPomodoro(prefs: MutablePreferences, settings: AppSettings, name: String) {
        if (prefs[POMODORO_MIGRATED] == true) return
        val widgets = decodeWidgets(prefs)
        val configured = settings.pomodoroEnabled || settings.pomodoroWorkDurationMinutes != 25 || settings.pomodoroBreakDurationMinutes != 5
        if (configured && widgets.none { it.type == WidgetType.POMODORO }) {
            val widget = StreamWidget("legacy-POMODORO", name, WidgetType.POMODORO,
                enabled = settings.pomodoroEnabled, configuration = WidgetConfiguration.from(settings))
            prefs[WIDGETS_JSON] = json.encodeToString(widgets + widget)
            if (widget.enabled) {
                val scenes = decodeScenes(prefs)
                val target = scenes.find { it.id == prefs[ACTIVE_SCENE_ID] } ?: scenes.firstOrNull()
                prefs[SCENES_JSON] = json.encodeToString(scenes.map { scene ->
                    if (scene.id == target?.id && scene.widgets.orEmpty().none { it.widgetId == widget.id }) {
                        scene.copy(widgets = scene.widgets.orEmpty() + SceneWidget(widget.id))
                    } else scene
                })
            }
        }
        prefs[POMODORO_MIGRATED] = true
    }

    companion object {
        private val POMODORO_MIGRATED = booleanPreferencesKey("pomodoro_widget_migrated")
        private val SAVED_LAYOUT = stringPreferencesKey("scene_widget_saved_layout")
        internal val SCENES_JSON = stringPreferencesKey("scenes_json")
        internal val WIDGETS_JSON = stringPreferencesKey("scene_widgets_json")
        internal val ACTIVE_SCENE_ID = stringPreferencesKey("active_scene_id")
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        internal fun decodeScenes(prefs: Preferences): List<StreamScene> = decode(prefs[SCENES_JSON])
        internal fun decodeWidgets(prefs: Preferences): List<StreamWidget> = decode(prefs[WIDGETS_JSON])
        private inline fun <reified T> decode(raw: String?): List<T> =
            raw?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() }.orEmpty()
        private fun <T> order(items: List<T>, ids: List<String>, id: (T) -> String): List<T> =
            ids.distinct().mapNotNull { key -> items.find { id(it) == key } } + items.filterNot { id(it) in ids }
    }
}
