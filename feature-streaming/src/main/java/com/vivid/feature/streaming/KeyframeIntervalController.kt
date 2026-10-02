package com.vivid.feature.streaming

/** Observe encoder output; request a sync frame only when the hardware misses its time deadline. */
internal class KeyframeIntervalController {
    companion object {
        const val INTERVAL_SECONDS = 2
        private const val INTERVAL_MS = INTERVAL_SECONDS * 1_000L
        // Allow normal scheduling/encoder latency before overriding the hardware GOP.
        private const val GRACE_MS = 100L
    }

    private var lastKeyframeMs: Long? = null
    private var lastKeyframePtsUs: Long? = null
    private var lastRequestMs: Long? = null
    var measuredIntervalMs: Long? = null
        private set

    @Synchronized
    fun reset() {
        lastKeyframeMs = null
        lastKeyframePtsUs = null
        lastRequestMs = null
        measuredIntervalMs = null
    }

    /** Called for real encoded frames, not codec configuration or EOS buffers. */
    @Synchronized
    fun onFrame(presentationTimeUs: Long, keyframe: Boolean, nowMs: Long): Boolean {
        if (keyframe) {
            lastKeyframePtsUs?.let { previous ->
                if (presentationTimeUs >= previous) measuredIntervalMs = (presentationTimeUs - previous) / 1_000L
            }
            lastKeyframePtsUs = presentationTimeUs
            lastKeyframeMs = nowMs
            lastRequestMs = null
            return false
        }
        val previous = lastKeyframeMs ?: nowMs.also { lastKeyframeMs = it }
        if (nowMs - previous < INTERVAL_MS + GRACE_MS) return false
        if (lastRequestMs?.let { nowMs - it < INTERVAL_MS } == true) return false
        lastRequestMs = nowMs
        return true
    }
}
