package com.vivid.feature.streaming

import com.vivid.core.data.EncoderPreset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExperimentalCameraProfileTest {
    @Test fun `selects lowest supported fixed high speed multiple without inventing 60`() {
        val sizes = listOf(HighSpeedCameraSize(1920, 1080, listOf(240, 120)))
        assertEquals(120, selectHighSpeedFps(sizes, 1920, 1080))
        assertNull(selectHighSpeedFps(sizes, 3840, 2160))
        assertNull(selectHighSpeedFps(listOf(HighSpeedCameraSize(1920, 1080, listOf(60, 90))), 1920, 1080))
    }
    @Test fun `mode requests 60 at the same resolution and rejects absent output presets`() {
        assertEquals(EncoderPreset.FHD60, experimentalOutputPreset(EncoderPreset.FHD30))
        assertEquals(EncoderPreset.S_4K60, experimentalOutputPreset(EncoderPreset.S_4K60))
        assertNull(experimentalOutputPreset(EncoderPreset.HD30))
    }
}
