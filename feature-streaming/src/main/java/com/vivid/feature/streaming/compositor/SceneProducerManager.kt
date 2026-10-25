package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneSource

/** A producer owns capture/decoding only. The compositor owns textures and the encoder. */
interface SceneProducer {
    fun start()
    fun stop()
    fun release()
}

fun interface SceneProducerFactory {
    fun create(source: SceneSource): SceneProducer
}

enum class SceneProducerStatus { ACTIVE, OFFLINE, PERMISSION_REQUIRED, SUSPENDED }

class SceneProducerPermissionException : Exception()

data class SceneProducerState(
    val source: SceneSource,
    val references: Int,
    val generation: Long,
    val status: SceneProducerStatus,
    val failures: Int,
    val retryAtMillis: Long,
)

/**
 * Reconciles source identities across the current and outgoing transition scene.
 * All methods must run on the owning compositor thread. Failed sources do not
 * block healthy layers; renderers use OFFLINE/PERMISSION_REQUIRED placeholders.
 */
class SceneProducerManager(private val factory: SceneProducerFactory) {
    private data class Entry(
        val source: SceneSource,
        var references: Int,
        var generation: Long = 0,
        var producer: SceneProducer? = null,
        var status: SceneProducerStatus = SceneProducerStatus.OFFLINE,
        var permissionRequired: Boolean = false,
        var failures: Int = 0,
        var retryAtMillis: Long = 0,
    )

    private val entries = linkedMapOf<String, Entry>()
    private var suspended = false
    private var nextGeneration = 0L

    val states: Map<String, SceneProducerState>
        get() = entries.mapValues { (_, entry) ->
            SceneProducerState(entry.source, entry.references, entry.generation, entry.status,
                entry.failures, entry.retryAtMillis)
        }

    /** Validation happens before changing any running producer. */
    fun reconcile(compositions: List<SceneComposition>, nowMillis: Long) {
        require(nowMillis >= 0)
        val desired = linkedMapOf<String, Pair<SceneSource, Int>>()
        for (composition in compositions) {
            val sources = composition.sources.associateBy { it.id }
            for (layer in composition.layers.filter { it.visible && it.opacity > 0f }) {
                val source = sources.getValue(layer.sourceId)
                val previous = desired[source.id]
                require(previous == null || previous.first == source) {
                    "Conflicting descriptions for source ${source.id}"
                }
                desired[source.id] = source to (previous?.second?.plus(1) ?: 1)
            }
        }
        // A transition may temporarily retain two scenes, but never unlimited producers.
        require(desired.size <= SceneComposition.MAX_SOURCES)
        entries.values.filter { desired[it.source.id]?.first != it.source }.forEach { entry ->
            dispose(entry)
            entries.remove(entry.source.id)
        }
        for ((id, request) in desired) {
            val entry = entries.getOrPut(id) { Entry(request.first, request.second) }
            entry.references = request.second
            if (suspended) {
                entry.status = SceneProducerStatus.SUSPENDED
            } else if (entry.permissionRequired) {
                entry.status = SceneProducerStatus.PERMISSION_REQUIRED
            } else if (entry.producer == null &&
                nowMillis >= entry.retryAtMillis) {
                open(entry, nowMillis)
            }
        }
    }

    /** Runtime disconnects release resources before scheduling a bounded retry. */
    fun disconnected(sourceId: String, nowMillis: Long, generation: Long, permissionRequired: Boolean = false) {
        require(nowMillis >= 0)
        val entry = entries[sourceId] ?: return
        if (entry.generation != generation || entry.producer == null) return
        dispose(entry)
        failed(entry, nowMillis, permissionRequired)
    }

    /** Permission retries are explicit: a denied dialog must never become a retry loop. */
    fun retry(sourceId: String) {
        entries[sourceId]?.let {
            if (it.producer == null) {
                it.status = if (suspended) SceneProducerStatus.SUSPENDED else SceneProducerStatus.OFFLINE
                it.permissionRequired = false
                it.retryAtMillis = 0
            }
        }
    }

    /** Used when capture really stops, not when only the preview Surface disappears. */
    fun suspendSources() {
        suspended = true
        entries.values.forEach { dispose(it); it.status = SceneProducerStatus.SUSPENDED }
    }

    fun resumeSources() { suspended = false }

    fun release() {
        entries.values.forEach(::dispose)
        entries.clear()
    }

    private fun open(entry: Entry, nowMillis: Long) {
        var candidate: SceneProducer? = null
        entry.generation = ++nextGeneration
        try {
            candidate = factory.create(entry.source)
            candidate.start()
            entry.producer = candidate
            entry.status = SceneProducerStatus.ACTIVE
            entry.failures = 0
            entry.retryAtMillis = 0
        } catch (_: SceneProducerPermissionException) {
            candidate?.let(::disposeProducer)
            failed(entry, nowMillis, permissionRequired = true)
        } catch (_: SecurityException) {
            candidate?.let(::disposeProducer)
            failed(entry, nowMillis, permissionRequired = true)
        } catch (_: Exception) {
            candidate?.let(::disposeProducer)
            failed(entry, nowMillis, permissionRequired = false)
        }
    }

    private fun failed(entry: Entry, nowMillis: Long, permissionRequired: Boolean) {
        entry.permissionRequired = permissionRequired
        entry.failures = (entry.failures + 1).coerceAtMost(MAX_FAILURE_EXPONENT + 1)
        entry.status = if (permissionRequired) SceneProducerStatus.PERMISSION_REQUIRED
            else SceneProducerStatus.OFFLINE
        val delay = (FIRST_RETRY_MILLIS shl (entry.failures - 1)).coerceAtMost(MAX_RETRY_MILLIS)
        entry.retryAtMillis = if (nowMillis > Long.MAX_VALUE - delay) Long.MAX_VALUE else nowMillis + delay
    }

    private fun dispose(entry: Entry) {
        entry.producer?.let(::disposeProducer)
        entry.producer = null
    }

    private fun disposeProducer(producer: SceneProducer) {
        // A failing stop must not prevent release or cleanup of other sources.
        try { producer.stop() } catch (_: Exception) { }
        try { producer.release() } catch (_: Exception) { }
    }

    companion object {
        const val FIRST_RETRY_MILLIS = 500L
        const val MAX_RETRY_MILLIS = 30_000L
        private const val MAX_FAILURE_EXPONENT = 6
    }
}
