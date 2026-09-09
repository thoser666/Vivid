package com.vivid.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministische Tests für TTL + Distanz-Schwelle des Geocode-Caches (pure Kotlin). */
class PlacenamesCacheTest {

    private var currentTimeMillis = 1_000_000L
    private val cache = PlacenamesCache { currentTimeMillis }

    private val berlin = Placenames(road = "Kurfürstendamm", city = "Berlin", country = "Deutschland")

    @Test
    fun `empty cache is never fresh`() {
        assertFalse(cache.isFresh(52.52, 13.405))
        assertNull(cache.get())
    }

    @Test
    fun `same position within ttl is fresh`() {
        cache.put(52.52, 13.405, berlin)
        currentTimeMillis += 60_000 // 1 min später
        assertTrue(cache.isFresh(52.52, 13.405))
        assertEquals(berlin, cache.get())
    }

    @Test
    fun `same position after ttl is stale`() {
        cache.put(52.52, 13.405, berlin)
        currentTimeMillis += PlacenamesCache.CACHE_TTL_MILLIS + 1
        assertFalse(cache.isFresh(52.52, 13.405))
        // Letzter-bekannter-Fallback bleibt trotzdem lesbar:
        assertEquals(berlin, cache.get())
    }

    @Test
    fun `movement below threshold stays fresh`() {
        cache.put(52.52, 13.405, berlin)
        // ~50 m nach Norden (1 Breitengrad ≈ 111 km → 0.00045 ≈ 50 m).
        assertTrue(cache.isFresh(52.52045, 13.405))
    }

    @Test
    fun `movement above threshold is stale`() {
        cache.put(52.52, 13.405, berlin)
        // ~1.1 km nach Norden.
        assertFalse(cache.isFresh(52.53, 13.405))
    }

    @Test
    fun `last value overwrites earlier value`() {
        cache.put(52.52, 13.405, berlin)
        val hamburg = Placenames(road = "Reeperbahn", city = "Hamburg", country = "Deutschland")
        currentTimeMillis += 60_000
        cache.put(53.55, 9.99, hamburg)
        currentTimeMillis += 60_000

        assertTrue(cache.isFresh(53.55, 9.99))
        assertFalse(cache.isFresh(52.52, 13.405))
        assertEquals(hamburg, cache.get())
    }

    @Test
    fun `haversine distance is plausible for a known pair`() {
        // Berlin → Hamburg ≈ 255 km (Luftlinie).
        val d = PlacenamesCache.haversineMeters(52.52, 13.405, 53.55, 9.99)
        assertEquals(255_000.0, d, 10_000.0)
    }

    @Test
    fun `zero distance is always within threshold`() {
        cache.put(52.52, 13.405, berlin)
        assertTrue(cache.isFresh(52.52, 13.405))
    }
}
