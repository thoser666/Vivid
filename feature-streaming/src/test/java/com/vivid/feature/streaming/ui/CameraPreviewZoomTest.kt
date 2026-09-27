package com.vivid.feature.streaming.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CameraPreviewZoomTest {

    @Test
    fun `portrait preview expands to cover the side gaps`() {
        assertEquals(8f / 7f, cameraPreviewZoom(1080f, 1680f, false), 0.0001f)
    }

    @Test
    fun `landscape preview expands to cover the side gaps`() {
        assertEquals(2.7f, cameraPreviewZoom(2400f, 500f, true), 0.0001f)
    }

    @Test
    fun `matching aspect leaves preview unscaled`() {
        assertEquals(1f, cameraPreviewZoom(1080f, 1920f, false), 0.0001f)
        assertEquals(1f, cameraPreviewZoom(1920f, 1080f, true), 0.0001f)
    }
}
