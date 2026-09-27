package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoneRepositoryTest {

    /**
     * In-Memory-Fake der Preferences-DataStore (Muster SceneRepositoryTest —
     * die echte FileStorage-DataStore kann auf Windows dieselbe Datei nicht
     * per ATOMIC_MOVE überschreiben; Gegenstand der Tests sind nur die
     * JSON-Roundtrips, nicht die Persistenz-Details).
     */
    private class FakeDataStore(
        initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private fun repository(): ZoneRepository = ZoneRepository(FakeDataStore())

    private fun zone(x: Float, y: Float, rx: Float = 0.1f, ry: Float = 0.1f) =
        PrivacyZone(centerX = x, centerY = y, radiusX = rx, radiusY = ry)

    @Test
    fun `zonesFlow returns an empty list when nothing was saved`() = runTest {
        assertTrue(repository().zonesFlow.first().isEmpty())
    }

    @Test
    fun `privacyEnabledFlow defaults to false`() = runTest {
        assertFalse(repository().privacyEnabledFlow.first())
    }

    @Test
    fun `setZones persists the complete list and can be read back`() = runTest {
        val repository = repository()
        val zones = listOf(zone(0.25f, 0.25f), zone(0.75f, 0.75f, 0.2f, 0.15f))

        repository.setZones(zones)

        assertEquals(zones, repository.zonesFlow.first())
    }

    @Test
    fun `setZones replaces the previous list`() = runTest {
        val repository = repository()
        repository.setZones(listOf(zone(0.25f, 0.25f)))

        val updated = listOf(zone(0.5f, 0.5f))
        repository.setZones(updated)

        assertEquals(updated, repository.zonesFlow.first())
    }

    @Test
    fun `setZones caps the list at max zones`() = runTest {
        val repository = repository()
        val tooMany = List(PrivacyZone.MAX_ZONES + 2) { i -> zone(0.1f * (i + 1), 0.5f) }

        repository.setZones(tooMany)

        val loaded = repository.zonesFlow.first()
        assertEquals(PrivacyZone.MAX_ZONES, loaded.size)
        assertEquals(tooMany.take(PrivacyZone.MAX_ZONES), loaded)
    }

    @Test
    fun `setPrivacyEnabled persists the toggle`() = runTest {
        val repository = repository()

        repository.setPrivacyEnabled(true)
        assertTrue(repository.privacyEnabledFlow.first())

        repository.setPrivacyEnabled(false)
        assertFalse(repository.privacyEnabledFlow.first())
    }

    @Test
    fun `a corrupted zones cell decodes to an empty list instead of crashing`() = runTest {
        // Direct-Write, wie es ein Abbruch beim Schreiben hinterlassen könnte:
        // kein valides JSON unter dem Zonen-Key.
        val corrupt = ZoneRepository(
            FakeDataStore(
                preferencesOf(
                    androidx.datastore.preferences.core.stringPreferencesKey("privacy_zones_json")
                        to "{invalid",
                ),
            ),
        )

        assertTrue(corrupt.zonesFlow.first().isEmpty())
    }

    @Test
    fun `zones beyond the cap are truncated on read`() = runTest {
        // Direkt gescribtes JSON mit mehr Zonen als erlaubt (z. B. aus einer
        // älteren Version) wird beim Lesen gekappt.
        val overflow = List(PrivacyZone.MAX_ZONES + 3) { i ->
            "{\"centerX\":${0.1f * (i + 1)},\"centerY\":0.5,\"radiusX\":0.1,\"radiusY\":0.1}"
        }.joinToString(",", "[", "]")
        val repository = ZoneRepository(
            FakeDataStore(
                preferencesOf(
                    androidx.datastore.preferences.core.stringPreferencesKey("privacy_zones_json")
                        to overflow,
                ),
            ),
        )

        assertEquals(PrivacyZone.MAX_ZONES, repository.zonesFlow.first().size)
    }

    @Test
    fun `zone model validates coordinates and radii`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PrivacyZone(centerX = 1.5f, centerY = 0.5f, radiusX = 0.1f, radiusY = 0.1f)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PrivacyZone(centerX = 0.5f, centerY = 0.5f, radiusX = -0.1f, radiusY = 0.1f)
        }
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            PrivacyZone(centerX = 0.5f, centerY = 1.2f, radiusX = 0.1f, radiusY = 0.1f)
        }
    }

    @Test
    fun `zone shift clamps to the normalized range`() {
        assertEquals(0.6f, PrivacyZone.shift(0.5f, 0.1f))
        assertEquals(0f, PrivacyZone.shift(0.05f, -0.1f))
        assertEquals(1f, PrivacyZone.shift(0.95f, 0.1f))
    }
}
