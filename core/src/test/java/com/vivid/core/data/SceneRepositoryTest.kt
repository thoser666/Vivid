package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneRepositoryTest {

    /**
     * In-Memory-Fake der Preferences-DataStore.
     *
     * Die echte FileStorage-DataStore kann auf Windows dieselbe Datei nicht
     * per ATOMIC_MOVE überschreiben (zweiter Write schlägt fehl — siehe
     * DataStoreUpdateCheckCacheTest). Für die CRUD-Tests der Szenen reicht
     * ein reiner In-Memory-Store: Das Repository nutzt nur `data` und `edit`,
     * die Persistenz-Details sind hier nicht Gegenstand des Tests.
     */
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(preferencesOf())
        var writes = 0
            private set

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            writes++
            return updated
        }
    }

    private fun repository(): SceneRepository = SceneRepository(FakeDataStore())

    private fun scene(id: String, name: String = "Szene $id") = StreamScene(
        id = id,
        name = name,
        videoSource = SceneVideoSource.CAMERA,
        widgetEnabled = true,
        widgetShowTime = false,
        widgetShowLocation = true,
        widgetShowSpeed = true,
        widgetShowAltitude = true,
        widgetTemplate = "{time}",
        streamUrl = "rtmp://live.example/app",
        streamKey = "key-$id",
        streamUseTls = true,
    )

    @Test
    fun `scenesFlow returns empty list when nothing was saved`() = runTest {
        val repository = repository()
        assertTrue(repository.scenesFlow.first().isEmpty())
    }

    @Test
    fun `saveScene persists the complete scene and can be read back`() = runTest {
        val repository = repository()
        val scene = scene("1")

        repository.saveScene(scene)
        val loaded = repository.scenesFlow.first()

        assertEquals(1, loaded.size)
        assertEquals(scene, loaded.first())
    }

    @Test
    fun `saveScene keeps the order of creation`() = runTest {
        val repository = repository()
        repository.saveScene(scene("a"))
        repository.saveScene(scene("b"))
        repository.saveScene(scene("c"))

        assertEquals(listOf("a", "b", "c"), repository.scenesFlow.first().map { it.id })
    }

    @Test
    fun `saveScene with an existing id replaces the scene instead of duplicating it`() = runTest {
        val repository = repository()
        repository.saveScene(scene("1", name = "Alt"))
        repository.saveScene(scene("1", name = "Neu"))

        val scenes = repository.scenesFlow.first()
        assertEquals(1, scenes.size)
        assertEquals("Neu", scenes.first().name)
    }

    @Test
    fun `deleteScene removes only the requested scene`() = runTest {
        val repository = repository()
        repository.saveScene(scene("a"))
        repository.saveScene(scene("b"))

        repository.deleteScene("a")

        assertEquals(listOf("b"), repository.scenesFlow.first().map { it.id })
    }

    @Test
    fun `activeSceneIdFlow is null by default`() = runTest {
        val repository = repository()
        assertNull(repository.activeSceneIdFlow.first())
    }

    @Test
    fun `setActiveScene persists the active scene id`() = runTest {
        val repository = repository()
        repository.setActiveScene("1")

        assertEquals("1", repository.activeSceneIdFlow.first())
    }

    @Test
    fun `setActiveScene null clears the active scene`() = runTest {
        val repository = repository()
        repository.setActiveScene("1")
        repository.setActiveScene(null)

        assertNull(repository.activeSceneIdFlow.first())
    }

    @Test
    fun `deleteScene clears the active id when the active scene is deleted`() = runTest {
        val repository = repository()
        repository.saveScene(scene("1"))
        repository.setActiveScene("1")

        repository.deleteScene("1")

        assertNull(repository.activeSceneIdFlow.first())
    }

    @Test
    fun `deleteScene keeps the active id when another scene is deleted`() = runTest {
        val repository = repository()
        repository.saveScene(scene("1"))
        repository.saveScene(scene("2"))
        repository.setActiveScene("1")

        repository.deleteScene("2")

        assertEquals("1", repository.activeSceneIdFlow.first())
    }

    @Test
    fun `a blank active scene id is treated as no active scene`() = runTest {
        val repository = repository()
        repository.setActiveScene("")

        assertNull(repository.activeSceneIdFlow.first())
    }

    @Test
    fun `editing and reordering scenes preserves all fields and stable order`() = runTest {
        val repo = repository()
        repo.saveScene(scene("a"))
        repo.saveScene(scene("b"))
        repo.saveScene(scene("c"))
        repo.reorderScenes(listOf("c", "a", "b"))
        repo.saveScene(scene("a").copy(name = "Changed", enabled = false, cameraId = "front"))
        assertEquals(listOf("c", "a", "b"), repo.scenesFlow.first().map { it.id })
        assertEquals("front", repo.scenesFlow.first()[1].cameraId)
        assertEquals(false, repo.scenesFlow.first()[1].enabled)
    }

    @Test
    fun `shared widget has independent placement and visibility per scene`() = runTest {
        val store = FakeDataStore()
        val repo = SceneRepository(store)
        val settings = SettingsRepository(store)
        repo.saveWidget(StreamWidget("w", "Caption", WidgetType.TEXT,
            configuration = WidgetConfiguration(widgetTemplate = "First")))
        repo.saveScene(scene("a").copy(widgets = listOf(SceneWidget("w", x = 10f, y = 20f))))
        repo.saveScene(scene("b").copy(widgets = listOf(SceneWidget("w", enabled = false, x = 70f, y = 80f))))
        repo.setActiveScene("a")
        assertEquals(true, settings.widgetSettingsFlow("w").first().widgetEnabled)
        assertEquals("First", settings.widgetSettingsFlow("w").first().widgetTemplate)
        repo.setActiveScene("b")
        assertEquals(false, settings.widgetSettingsFlow("w").first().widgetEnabled)
        repo.saveWidget(repo.widgetsFlow.first().single().copy(configuration = WidgetConfiguration(widgetTemplate = "Updated")))
        assertEquals(listOf(10f, 70f), repo.scenesFlow.first().map { it.widgets!!.single().x })
        repo.setActiveScene("a")
        assertEquals("Updated", settings.widgetSettingsFlow("w").first().widgetTemplate)
        repo.saveWidget(repo.widgetsFlow.first().single().copy(enabled = false))
        assertEquals(false, settings.widgetSettingsFlow("w").first().widgetEnabled)
    }

    @Test
    fun `global widget toggle updates every existing placement atomically and preserves layouts`() = runTest {
        val store = FakeDataStore()
        val repo = SceneRepository(store)
        val widget = StreamWidget("w", "Caption", WidgetType.TEXT)
        repo.saveWidget(widget)
        val scenes = listOf(
            scene("a").copy(widgets = listOf(SceneWidget("other"), SceneWidget("w", x = 10f, y = 20f, scale = 0.5f))),
            scene("b").copy(enabled = false, widgets = listOf(SceneWidget("w", enabled = false, x = 70f, y = 80f))),
            scene("empty").copy(widgets = emptyList()),
            scene("legacy"),
        )
        scenes.forEach { repo.saveScene(it) }
        for (enabled in listOf(false, true)) {
            val before = store.writes
            repo.saveWidget(widget.copy(enabled = enabled))
            assertEquals(before + 1, store.writes)
            assertEquals(enabled, repo.widgetsFlow.first().single().enabled)
            assertEquals(scenes.map { scene -> scene.copy(widgets = scene.widgets?.map {
                if (it.widgetId == widget.id) it.copy(enabled = enabled) else it
            }) }, SceneRepository(store).scenesFlow.first())
        }
        val beforeEdit = repo.scenesFlow.first()
        repo.saveWidget(widget.copy(name = "Renamed"))
        assertEquals(beforeEdit, repo.scenesFlow.first())
    }

    @Test
    fun `deleting library widget cleans references in every scene and keeps other widgets`() = runTest {
        val store = FakeDataStore()
        val repo = SceneRepository(store)
        repo.saveWidget(StreamWidget("w", "One", WidgetType.TEXT))
        repo.saveWidget(StreamWidget("other", "Two", WidgetType.IMAGE))
        val other = SceneWidget("other", x = 33f, y = 66f, scale = 1.5f)
        val scenes = listOf(
            scene("a").copy(widgets = listOf(SceneWidget("w"), other)),
            scene("b").copy(widgets = listOf(SceneWidget("w"))),
            scene("disabled").copy(enabled = false, widgets = listOf(other, SceneWidget("w", enabled = false))),
            scene("empty").copy(widgets = emptyList()),
            scene("legacy"),
        )
        scenes.forEach { repo.saveScene(it) }
        repo.setActiveScene("a")
        val before = store.writes
        repo.deleteWidget("w")

        assertEquals(before + 1, store.writes)
        assertEquals(listOf("other"), repo.widgetsFlow.first().map { it.id })
        assertEquals(scenes.map { it.copy(widgets = it.widgets?.filterNot { placement -> placement.widgetId == "w" }) },
            SceneRepository(store).scenesFlow.first())
        assertEquals("a", repo.activeSceneIdFlow.first())
    }

    @Test
    fun `removing a placement from one scene keeps its definition and other scenes`() = runTest {
        val repo = repository()
        val widget = StreamWidget("w", "Caption", WidgetType.TEXT)
        val a = scene("a").copy(widgets = listOf(SceneWidget("w")))
        val b = scene("b").copy(widgets = listOf(SceneWidget("w", x = 70f)))
        repo.saveWidget(widget)
        repo.saveScene(a)
        repo.saveScene(b)
        repo.saveScene(a.copy(widgets = emptyList()))
        assertEquals(listOf(widget), repo.widgetsFlow.first())
        assertEquals(listOf(a.copy(widgets = emptyList()), b), repo.scenesFlow.first())
    }

    @Test
    fun `migration preserves legacy scene text configurations and runs only once`() = runTest {
        val repo = repository()
        repo.saveScene(scene("a").copy(widgetTemplate = "A"))
        repo.saveScene(scene("b").copy(widgetTemplate = "B"))
        val names = WidgetType.entries.associateWith { it.name }
        repo.initializeLibrary(AppSettings(imageWidgetEnabled = true, imageWidgetUri = "content://logo"), "Default", names)
        val migrated = repo.scenesFlow.first()
        assertEquals(listOf("legacy-IMAGE", "legacy-text-a"), migrated[0].widgets!!.map { it.widgetId })
        assertEquals(listOf("legacy-IMAGE", "legacy-text-b"), migrated[1].widgets!!.map { it.widgetId })
        assertEquals("A", repo.widgetsFlow.first().find { it.id == "legacy-text-a" }!!.configuration.widgetTemplate)
        val before = repo.widgetsFlow.first()
        repo.initializeLibrary(AppSettings(), "Ignored", names)
        assertEquals(before, repo.widgetsFlow.first())
    }

    @Test
    fun `library migration preserves explicitly empty new scenes alongside legacy scenes`() = runTest {
        val repo = repository()
        val empty = scene("new").copy(widgets = emptyList())
        repo.saveScene(empty)
        repo.saveScene(scene("legacy"))
        repo.initializeLibrary(AppSettings(widgetEnabled = true, batteryEnabled = true), "Default",
            WidgetType.entries.associateWith { it.name })

        assertEquals(empty, repo.scenesFlow.first().first())
        assertTrue(repo.scenesFlow.first().last().widgets!!.isNotEmpty())
        assertTrue(repo.widgetsFlow.first().none { it.id == "legacy-text-new" })
    }

    @Test
    fun `saved layout survives reopening repository and can be loaded for another widget`() = runTest {
        val store = FakeDataStore()
        val repo = SceneRepository(store)
        repo.saveLayout(SceneWidget("a", x = 45f, y = 5f, scale = 0.37f))
        val loaded = SceneRepository(store).savedLayoutFlow.first()!!.copy(widgetId = "b")
        assertEquals(SceneWidget("b", x = 45f, y = 5f, scale = 0.37f), loaded)
    }

    @Test
    fun `browser viewport and widget order survive persistence`() = runTest {
        val repo = repository()
        repo.saveWidget(StreamWidget("a", "Page", WidgetType.BROWSER,
            configuration = WidgetConfiguration(browserUrl = "https://example.com/overlay", browserWidth = 800, browserHeight = 600)))
        repo.saveWidget(StreamWidget("b", "Logo", WidgetType.IMAGE))
        repo.reorderWidgets(listOf("b", "a"))
        assertEquals(listOf("b", "a"), repo.widgetsFlow.first().map { it.id })
        assertEquals(800, repo.widgetsFlow.first()[1].configuration.browserWidth)
        assertEquals(600, repo.widgetsFlow.first()[1].configuration.browserHeight)
        assertTrue(isBrowserWidgetUrl("https://example.com/overlay"))
        assertEquals(false, isBrowserWidgetUrl("http://example.com/overlay"))
        assertEquals(false, isBrowserWidgetUrl("file:///etc/passwd"))
        assertEquals(false, isBrowserWidgetUrl("javascript:alert(1)"))
    }

    @Test
    fun `widget editor saves scene visibility atomically while retaining placement and layer order`() = runTest {
        val store = FakeDataStore()
        val repo = SceneRepository(store)
        val widget = StreamWidget("w", "Caption", WidgetType.TEXT)
        val placement = SceneWidget("w", x = 45f, y = 5f, scale = 0.37f)
        repo.saveWidget(widget)
        repo.saveScene(scene("a").copy(widgets = listOf(SceneWidget("other"), placement, SceneWidget("top"))))
        repo.saveScene(scene("b").copy(widgets = listOf(SceneWidget("other"))))
        val untouched = scene("c").copy(widgets = listOf(placement.copy(x = 70f)))
        repo.saveScene(untouched)
        repo.setActiveScene("a")
        val writesBefore = store.writes

        repo.saveWidgetWithSceneVisibility(widget.copy(name = "Updated"), mapOf("a" to false, "b" to true, "deleted" to true))

        assertEquals(writesBefore + 1, store.writes)
        assertEquals("Updated", repo.widgetsFlow.first().single().name)
        val saved = repo.scenesFlow.first()
        assertEquals(listOf(SceneWidget("other"), placement.copy(enabled = false), SceneWidget("top")), saved[0].widgets)
        assertEquals(listOf(SceneWidget("other"), SceneWidget("w")), saved[1].widgets)
        assertEquals(untouched, saved[2])
        assertEquals(false, SettingsRepository(store).widgetSettingsFlow("w").first().widgetEnabled)

        repo.saveWidgetWithSceneVisibility(widget, mapOf("a" to true, "b" to true))
        assertEquals(placement, repo.scenesFlow.first()[0].widgets!![1])
        assertEquals(1, repo.scenesFlow.first()[1].widgets!!.count { it.widgetId == "w" })
        assertEquals(true, SettingsRepository(store).widgetSettingsFlow("w").first().widgetEnabled)
    }

    @Test
    fun `unchecked missing widget does not create a placement and content-only save keeps latest scenes`() = runTest {
        val repo = repository()
        val widget = StreamWidget("w", "Caption", WidgetType.TEXT)
        val original = scene("a").copy(widgets = emptyList())
        repo.saveScene(original)
        repo.saveWidgetWithSceneVisibility(widget, mapOf("a" to false))
        assertEquals(original, repo.scenesFlow.first().single())
        val latest = original.copy(name = "Renamed elsewhere", widgets = listOf(SceneWidget("other")))
        repo.saveScene(latest)
        repo.saveWidgetWithSceneVisibility(widget.copy(name = "New caption"), emptyMap())
        assertEquals(latest, repo.scenesFlow.first().single())
    }

    @Test
    fun `legacy enabled pomodoro migrates once into existing library and active scene`() = runTest {
        val repo = repository()
        repo.saveWidget(StreamWidget("text", "Caption", WidgetType.TEXT))
        repo.saveScene(scene("a").copy(widgets = listOf(SceneWidget("text", x = 30f))))
        repo.setActiveScene("a")
        val names = WidgetType.entries.associateWith { it.name }
        val settings = AppSettings(pomodoroEnabled = true, pomodoroWorkDurationMinutes = 40, pomodoroBreakDurationMinutes = 8)
        repo.initializeLibrary(settings, "Default", names)
        val timer = repo.widgetsFlow.first().single { it.type == WidgetType.POMODORO }
        assertEquals(40, timer.configuration.pomodoroWorkDurationMinutes)
        assertEquals(8, timer.configuration.pomodoroBreakDurationMinutes)
        assertTrue(timer.enabled)
        assertEquals(listOf(SceneWidget("text", x = 30f), SceneWidget(timer.id)), repo.scenesFlow.first().single().widgets)
        repo.initializeLibrary(settings, "Default", names)
        assertEquals(1, repo.widgetsFlow.first().count { it.type == WidgetType.POMODORO })
        repo.deleteWidget(timer.id)
        repo.initializeLibrary(settings, "Default", names)
        assertTrue(repo.widgetsFlow.first().none { it.type == WidgetType.POMODORO })
    }

    @Test
    fun `custom legacy pomodoro durations are preserved without enabling a disabled timer`() = runTest {
        val repo = repository()
        repo.saveWidget(StreamWidget("image", "Logo", WidgetType.IMAGE))
        repo.saveScene(scene("a").copy(widgets = emptyList()))
        repo.initializeLibrary(AppSettings(pomodoroWorkDurationMinutes = 50), "Default", WidgetType.entries.associateWith { it.name })
        val timer = repo.widgetsFlow.first().single { it.type == WidgetType.POMODORO }
        assertEquals(false, timer.enabled)
        assertEquals(50, timer.configuration.pomodoroWorkDurationMinutes)
        assertTrue(repo.scenesFlow.first().single().widgets!!.isEmpty())
    }
}