package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneCrop
import com.vivid.core.data.SceneTransform
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneGeometryTest {
    @Test fun `quarter rotation on widescreen respects pixel aspect rather than normalized aspect`() {
        val transform = SceneTransform(x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f,
            rotationDegrees = 90f)
        val points = SceneGeometry.corners(transform, 200, 100)
        assertEquals(0.375f, points.minOf { it.x }, 0.0001f)
        assertEquals(0.625f, points.maxOf { it.x }, 0.0001f)
        assertEquals(0f, points.minOf { it.y }, 0.0001f)
        assertEquals(1f, points.maxOf { it.y }, 0.0001f)
        assertTrue(SceneGeometry.contains(transform, SceneGeometry.Point(0.5f, 0.05f), 200, 100))
        assertFalse(SceneGeometry.contains(transform, SceneGeometry.Point(0.3f, 0.5f), 200, 100))
    }

    @Test fun `crop changes UV without moving layer geometry`() {
        val plain = SceneTransform()
        val cropped = plain.copy(crop = SceneCrop(0.2f, 0.3f, 0.8f, 0.9f))
        assertEquals(SceneGeometry.corners(plain, 1920, 1080), SceneGeometry.corners(cropped, 1920, 1080))
        val vertices = SceneGeometry.vertices(cropped, 1920, 1080)
        assertArrayEquals(floatArrayOf(-1f, -1f, 0f, 0.2f, 0.9f), vertices.take(5).toFloatArray(), 0.0001f)
        assertArrayEquals(floatArrayOf(1f, 1f, 0f, 0.8f, 0.3f), vertices.takeLast(5).toFloatArray(), 0.0001f)
    }

    @Test fun `snap operates in pixels and reports exact guide`() {
        val transform = SceneTransform(x = 0.006f, y = 0.23f, width = 0.2f, height = 0.2f)
        val snapped = SceneGeometry.snap(transform, 1000, 1000)
        assertEquals(0f, snapped.transform.x, 0.00001f)
        assertEquals(0f, snapped.verticalGuide)
        assertNull(snapped.horizontalGuide)
        val outside = SceneGeometry.snap(transform.copy(x = 0.02f), 1000, 1000)
        assertEquals(0.02f, outside.transform.x)
        assertNull(outside.verticalGuide)
    }
}
