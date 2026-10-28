package com.vivid.feature.streaming.compositor

import com.pedro.library.view.ForceRenderer
import com.pedro.library.view.GlStreamInterface
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a legacy GL stream drawing when a scene is added after GL has started.
 * RootEncoder starts its own ForceRenderer only in GlStreamInterface.start().
 * Native NoVideoSource sessions use that existing clock instead of this one.
 */
internal class SceneRenderClock(private val renderer: ForceRenderer = ForceRenderer()) {
    @Volatile private var owner: GlStreamInterface? = null
    @Volatile private var generation = 0L
    private var fps = 0
    private val requestedFrame = AtomicBoolean(false)

    @Synchronized
    fun update(gl: GlStreamInterface?, framesPerSecond: Int = 30) {
        require(framesPerSecond in 1..120)
        if (owner === gl && (gl == null || fps == framesPerSecond)) return
        owner = null
        val currentGeneration = ++generation
        renderer.stop()
        requestedFrame.set(false)
        if (gl == null) return
        fps = framesPerSecond
        owner = gl
        renderer.setEnabled(true, framesPerSecond)
        renderer.start {
            if (generation == currentGeneration && owner === gl) {
                if (!gl.isRunning) {
                    retire(gl, currentGeneration)
                    return@start
                }
                try {
                    requestedFrame.set(true)
                    gl.onFrameAvailable(null)
                } catch (_: RejectedExecutionException) {
                    // GL can shut down between isRunning and executor submission.
                    requestedFrame.set(false)
                }
            }
        }
    }

    @Synchronized
    private fun retire(gl: GlStreamInterface, expectedGeneration: Long) {
        if (owner === gl && generation == expectedGeneration) update(null)
    }

    /** Source-driven draws suppress fallback; our own draws keep the configured cadence. */
    @Synchronized
    fun frameRendered(gl: GlStreamInterface) {
        if (owner === gl && !requestedFrame.getAndSet(false)) renderer.frameAvailable()
    }
}
