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

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
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
}