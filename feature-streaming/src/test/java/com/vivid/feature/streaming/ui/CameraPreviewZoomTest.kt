package com.vivid.feature.streaming.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import androidx.compose.ui.unit.IntSize

class CameraPreviewZoomTest {

    @Test
    fun `portrait preview expands to cover the side gaps`() {
        assertEquals(8f / 7f, cameraPreviewZoom(1080f, 1680f, 9f / 16f), 0.0001f)
    }

    @Test
    fun `landscape preview expands to cover the side gaps`() {
        assertEquals(2.7f, cameraPreviewZoom(2400f, 500f, 16f / 9f), 0.0001f)
    }

    @Test
    fun `matching aspect leaves preview unscaled`() {
        assertEquals(1f, cameraPreviewZoom(1080f, 1920f, 9f / 16f), 0.0001f)
        assertEquals(1f, cameraPreviewZoom(1920f, 1080f, 16f / 9f), 0.0001f)
    }

    @Test
    fun `portrait uses a supported square buffer instead of stretching it to nine by sixteen`() {
        val selected = selectPortraitIdlePreviewSize(
            listOf(IntSize(1920, 1080), IntSize(1088, 1088), IntSize(1280, 720)),
        )
        assertEquals(IntSize(1088, 1088), selected)
        assertEquals(1680f / 1080f, cameraPreviewZoom(1080f, 1680f, 1f), 0.0001f)
    }

    @Test
    fun `portrait picks a supported four by three buffer when square is unavailable`() {
        assertEquals(
            IntSize(1440, 1080),
            selectPortraitIdlePreviewSize(listOf(IntSize(1920, 1080), IntSize(1440, 1080))),
        )
    }
}
