package com.vivid.feature.streaming

/** Capture-thread owned decoder buffers. Keep at most one frame waiting for GL and one submitted. */
internal class LatestDecodedFrame {
    private var index = -1
    private var ptsUs = 0L
    private var submittedPtsUs: Long? = null

    fun offer(buffer: Int, presentationTimeUs: Long, discard: (Int) -> Unit) {
        if (index >= 0) discard(index)
        index = buffer
        ptsUs = presentationTimeUs
    }

    fun renderIfConsumed(textureTimestampNs: Long, render: (Int) -> Unit): Boolean {
        if (index < 0 || submittedPtsUs?.let { textureTimestampNs / 1_000 != it } == true) return false
        render(index)
        submittedPtsUs = ptsUs
        index = -1
        return true
    }
}
