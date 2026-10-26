package com.vivid.feature.streaming.compositor

import com.vivid.core.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneProducerManagerTest {
    private val source = SceneSource("camera", SceneSourceType.CAMERA)
    private val graph = SceneComposition(sources = listOf(source),
        layers = listOf(SceneLayer("one", source.id), SceneLayer("two", source.id)))

    private class Producer : SceneProducer {
        var starts = 0
        var stops = 0
        var releases = 0
        var denied = false
        var brokenStop = false
        override fun start() { starts++; if (denied) throw SecurityException() }
        override fun stop() { stops++; if (brokenStop) error("Disconnected") }
        override fun release() { releases++ }
    }

    @Test fun `duplicates and transition scenes share one capture session`() {
        val created = mutableListOf<Producer>()
        val manager = SceneProducerManager { Producer().also(created::add) }
        manager.reconcile(listOf(graph, graph), 0)
        assertEquals(1, created.size)
        assertEquals(4, manager.states.getValue(source.id).references)
        manager.reconcile(listOf(graph), 1)
        assertEquals(0, created.single().stops)
        manager.reconcile(emptyList(), 2)
        assertEquals(1, created.single().stops)
        assertEquals(1, created.single().releases)
        assertTrue(manager.states.isEmpty())
    }

    @Test fun `invisible layers do not keep capture alive`() {
        val producer = Producer()
        val manager = SceneProducerManager { producer }
        manager.reconcile(listOf(graph), 0)
        manager.reconcile(listOf(graph.copy(layers = graph.layers.map { it.copy(visible = false) })), 1)
        assertEquals(1, producer.releases)
        assertTrue(manager.states.isEmpty())
    }

    @Test fun `permission denial waits for explicit retry and cleans partial start`() {
        val denied = Producer().apply { denied = true }
        var attempts = 0
        val manager = SceneProducerManager { attempts++; denied }
        manager.reconcile(listOf(graph), 0)
        assertEquals(SceneProducerStatus.PERMISSION_REQUIRED, manager.states.getValue(source.id).status)
        assertEquals(1, denied.releases)
        manager.reconcile(listOf(graph), 100_000)
        assertEquals(1, attempts)
        manager.suspendSources()
        manager.resumeSources()
        manager.reconcile(listOf(graph), 100_000)
        assertEquals(1, attempts)
        denied.denied = false
        manager.retry(source.id)
        manager.reconcile(listOf(graph), 100_001)
        assertEquals(2, attempts)
        assertEquals(SceneProducerStatus.ACTIVE, manager.states.getValue(source.id).status)
    }

    @Test fun `offline producers retry with capped backoff and healthy sources survive`() {
        var attempts = 0
        val manager = SceneProducerManager { attempts++; error("Offline") }
        manager.reconcile(listOf(graph), 0)
        manager.reconcile(listOf(graph), 499)
        assertEquals(1, attempts)
        manager.reconcile(listOf(graph), 500)
        assertEquals(2, attempts)
        assertEquals(1500, manager.states.getValue(source.id).retryAtMillis)
        var now = 1500L
        repeat(20) {
            manager.reconcile(listOf(graph), now)
            val next = manager.states.getValue(source.id).retryAtMillis
            assertTrue(next - now <= SceneProducerManager.MAX_RETRY_MILLIS)
            now = next
        }
    }

    @Test fun `conflicting transition IDs are rejected before stopping a healthy source`() {
        val producer = Producer()
        val manager = SceneProducerManager { producer }
        manager.reconcile(listOf(graph), 0)
        val conflicting = graph.copy(sources = listOf(source.copy(locator = "another-camera")))
        assertThrows(IllegalArgumentException::class.java) {
            manager.reconcile(listOf(graph, conflicting), 1)
        }
        assertEquals(0, producer.stops)
        assertEquals(SceneProducerStatus.ACTIVE, manager.states.getValue(source.id).status)
    }

    @Test fun `suspend resume and disconnect release resources even after stop fails`() {
        val created = mutableListOf<Producer>()
        val manager = SceneProducerManager { Producer().also(created::add) }
        manager.reconcile(listOf(graph), 0)
        created.single().brokenStop = true
        manager.suspendSources()
        assertEquals(1, created.single().releases)
        manager.reconcile(listOf(graph), 10)
        assertEquals(1, created.size)
        manager.resumeSources()
        manager.reconcile(listOf(graph), 11)
        assertEquals(2, created.size)
        manager.disconnected(source.id, 12, manager.states.getValue(source.id).generation)
        assertEquals(SceneProducerStatus.OFFLINE, manager.states.getValue(source.id).status)
        manager.release()
        manager.release()
        assertEquals(1, created.last().releases)
    }

    @Test fun `late disconnect from a released generation cannot stop its replacement`() {
        val created = mutableListOf<Producer>()
        val manager = SceneProducerManager { Producer().also(created::add) }
        manager.reconcile(listOf(graph), 0)
        val staleGeneration = manager.states.getValue(source.id).generation
        manager.reconcile(emptyList(), 1)
        manager.reconcile(listOf(graph), 2)
        manager.disconnected(source.id, 3, staleGeneration)
        assertEquals(SceneProducerStatus.ACTIVE, manager.states.getValue(source.id).status)
        assertEquals(0, created.last().stops)
    }

    @Test fun `failure of one source does not prevent another from streaming`() {
        val healthy = SceneSource("image", SceneSourceType.IMAGE, "content://logo")
        val manager = SceneProducerManager {
            if (it == source) error("Disconnected") else Producer()
        }
        val combined = graph.copy(sources = graph.sources + healthy,
            layers = graph.layers + SceneLayer("logo", healthy.id))
        manager.reconcile(listOf(combined), 0)
        assertEquals(SceneProducerStatus.OFFLINE, manager.states.getValue(source.id).status)
        assertEquals(SceneProducerStatus.ACTIVE, manager.states.getValue(healthy.id).status)
    }

    @Test fun `async opens without frames retain backoff until a real frame confirms recovery`() {
        val manager = SceneProducerManager { Producer() }
        manager.reconcile(listOf(graph), 0)
        val first = manager.states.getValue(source.id).generation
        manager.disconnected(source.id, 10, first)
        manager.reconcile(listOf(graph), 510)
        val second = manager.states.getValue(source.id).generation
        manager.disconnected(source.id, 520, second)
        assertEquals(1520L, manager.states.getValue(source.id).retryAtMillis)
        manager.reconcile(listOf(graph), 1520)
        val third = manager.states.getValue(source.id).generation
        manager.markHealthy(source.id, first)
        assertEquals(2, manager.states.getValue(source.id).failures)
        manager.markHealthy(source.id, third)
        assertEquals(0, manager.states.getValue(source.id).failures)
    }

    @Test fun `renaming a source across scenes reuses its capture and updates its display metadata`() {
        val created = mutableListOf<Producer>()
        val manager = SceneProducerManager { Producer().also(created::add) }
        manager.reconcile(listOf(graph), 0)
        val renamed = graph.copy(sources = listOf(source.copy(name = "Renamed camera")))
        manager.reconcile(listOf(graph, renamed), 1)
        assertEquals(1, created.size)
        assertEquals(0, created.single().stops)
        assertEquals("Renamed camera", manager.states.getValue(source.id).source.name)
        assertEquals(4, manager.states.getValue(source.id).references)
    }
    @Test fun `file mode changes replace only the producer while label changes and shared views reuse it`() {
        val file = SceneSource("file", SceneSourceType.VIDEO, "content://clip", videoLoop = false)
        val input = SceneComposition(sources = listOf(file),
            layers = listOf(SceneLayer("one", file.id), SceneLayer("two", file.id)))
        val created = mutableListOf<Producer>()
        val manager = SceneProducerManager { Producer().also(created::add) }
        manager.reconcile(listOf(input), 0)
        manager.reconcile(listOf(input.copy(sources = listOf(file.copy(name = "Renamed")))), 1)
        assertEquals(1, created.size)
        manager.reconcile(listOf(input.copy(sources = listOf(file.copy(videoLoop = true)))), 2)
        assertEquals(2, created.size)
        assertEquals(1, created.first().stops)
        assertEquals(1, created.first().releases)
        assertEquals(2, manager.states.getValue(file.id).references)
        manager.release()
    }

}
