package com.vivid.feature.streaming.compositor.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.vivid.feature.streaming.compositor.SceneProducerPermissionException
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns runtime-only grants. Rebinds an existing VirtualDisplay across GL rebuilds:
 * Android 14+ must never reuse a consent token to create a second display.
 */
class SceneProjectionRegistry(
    handlerFactory: () -> Handler = { Handler(Looper.getMainLooper()) },
) : Closeable {
    private val handler by lazy(handlerFactory)
    private class Grant(val projection: MediaProjection, val callback: MediaProjection.Callback) {
        var display: VirtualDisplay? = null
        var generation = 0L
        var leased = false
        var revoked: (() -> Unit)? = null
    }
    private val grants = mutableMapOf<String, Grant>()
    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()

    @Synchronized
    fun grant(sourceId: String, projection: MediaProjection) {
        require(sourceId.isNotBlank())
        remove(sourceId, publish = false)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() { revoked(sourceId, projection) }
        }
        try {
            projection.registerCallback(callback, handler)
            grants[sourceId] = Grant(projection, callback)
        } catch (failure: RuntimeException) {
            projection.stop()
            throw failure
        } finally { _active.value = grants.isNotEmpty() }
    }

    @Synchronized
    fun hasGrant(sourceId: String): Boolean = sourceId in grants

    @Synchronized
    fun acquire(sourceId: String, surface: Surface, width: Int, height: Int, densityDpi: Int,
        revoked: () -> Unit): Closeable {
        val grant = grants[sourceId] ?: throw SceneProducerPermissionException()
        check(!grant.leased) { "Screen source is already captured" }
        val generation = ++grant.generation
        grant.revoked = revoked
        try {
            val display = grant.display
            if (display == null) {
                grant.display = grant.projection.createVirtualDisplay("Vivid scene capture", width, height,
                    densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, handler)
                    ?: error("Unable to create screen source display")
            } else {
                display.resize(width, height, densityDpi)
                display.surface = surface
            }
            grant.leased = true
        } catch (failure: Exception) {
            remove(sourceId)
            throw failure
        }
        return Closeable { releaseLease(sourceId, grant, generation) }
    }

    @Synchronized
    private fun releaseLease(sourceId: String, expected: Grant, generation: Long) {
        val grant = grants[sourceId]?.takeIf { it === expected && it.generation == generation } ?: return
        grant.leased = false
        grant.revoked = null
        grant.display?.surface = null
        // Retain the display briefly for the canonical filter-chain rebuild, then
        // revoke idle capture instead of retaining an unused screen grant forever.
        handler.postDelayed({
            synchronized(this) {
                if (grants[sourceId] === grant && !grant.leased && grant.generation == generation) {
                    remove(sourceId)
                }
            }
        }, REBIND_WINDOW_MILLIS)
    }

    @Synchronized
    private fun revoked(sourceId: String, projection: MediaProjection) {
        val grant = grants[sourceId]?.takeIf { it.projection === projection } ?: return
        grants.remove(sourceId)
        _active.value = grants.isNotEmpty()
        grant.projection.unregisterCallback(grant.callback)
        grant.display?.release()
        grant.revoked?.invoke()
    }

    @Synchronized
    fun remove(sourceId: String) = remove(sourceId, publish = true)

    private fun remove(sourceId: String, publish: Boolean) {
        val grant = grants.remove(sourceId) ?: return
        if (publish) _active.value = grants.isNotEmpty()
        grant.projection.unregisterCallback(grant.callback)
        grant.display?.release()
        grant.projection.stop()
        grant.revoked?.invoke()
    }

    /** Unused consent handles are released; active outgoing scenes finish through their lease. */
    @Synchronized
    fun retainSources(sourceIds: Set<String>) {
        grants.entries.filter { (id, grant) -> id !in sourceIds && !grant.leased }
            .map { it.key }.forEach(::remove)
    }

    @Synchronized
    override fun close() { grants.keys.toList().forEach(::remove) }

    companion object { private const val REBIND_WINDOW_MILLIS = 2_000L }
}
