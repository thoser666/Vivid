package com.vivid.feature.streaming.ui

import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StreamStatusOverlayTest {
    @Test
    fun `bitrate uses one measured live target and never sums mirrored destinations`() {
        val targets = listOf(
            StreamTargetState("failed", StreamTargetStatus.FAILED, bitrateKbps = 9_000),
            StreamTargetState("live", StreamTargetStatus.STREAMING, bitrateKbps = 3_100),
            StreamTargetState("mirror", StreamTargetStatus.STREAMING, bitrateKbps = 3_200),
        )
        assertEquals(3_100, currentStreamBitrateKbps(targets))
        assertEquals(3_200, currentStreamBitrateKbps(targets.drop(2)))
        assertNull(currentStreamBitrateKbps(targets.take(1)))
        assertNull(currentStreamBitrateKbps(listOf(StreamTargetState("pending", StreamTargetStatus.STREAMING))))
        assertEquals(0, currentStreamBitrateKbps(listOf(StreamTargetState("zero", StreamTargetStatus.STREAMING, bitrateKbps = 0))))
    }

    @Test
    fun `elapsed time uses the session start and clamps unavailable or future timestamps`() {
        assertEquals(3_661L, elapsedStreamSeconds(1_000, 3_662_000))
        assertEquals(0L, elapsedStreamSeconds(null, 3_662_000))
        assertEquals(0L, elapsedStreamSeconds(1_000, 999))
    }
}
