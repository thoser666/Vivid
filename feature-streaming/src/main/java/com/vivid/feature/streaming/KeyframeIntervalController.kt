package com.vivid.feature.streaming

/** Observe encoder output; request a sync frame only when the hardware misses its time deadline. */
internal class KeyframeIntervalController {
    companion object {
        const val INTERVAL_SECONDS = 2
        // Allow normal scheduling/encoder latency before overriding the hardware GOP.
        private const val GRACE_MS = 100L
    }

    var intervalSeconds: Int = INTERVAL_SECONDS
        set(value) { field = value.coerceIn(1, 10); reset() }
    private val intervalMs: Long get() = intervalSeconds * 1_000L

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
        if (nowMs - previous < intervalMs + GRACE_MS) return false
        if (lastRequestMs?.let { nowMs - it < intervalMs } == true) return false
        lastRequestMs = nowMs
        return true
    }
}
