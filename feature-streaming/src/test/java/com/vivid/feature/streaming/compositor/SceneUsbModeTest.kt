package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.capture.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneUsbModeTest {
    private fun mode(w: Int, h: Int, vararg rates: Int, format: SceneUsbFormat = SceneUsbFormat.MJPEG) =
        SceneUsbMode(w, h, format, rates.toSet())
    @Test fun `single output prefers matching dimensions and the requested supported rate`() {
        val hd = mode(1920, 1080, 15, 30, 60)
        val result = sceneUsbConfigurations(listOf(mode(640, 480, 30), hd), 1920, 1080, 60, false)
        assertEquals(SceneUsbConfiguration(hd, 60), result.first())
        assertTrue(result.contains(SceneUsbConfiguration(hd, 30)))
    }
    @Test fun `concurrent outputs retain a 720p and 30 fps preference`() {
        val small = mode(1280, 720, 15, 30, 60)
        val result = sceneUsbConfigurations(listOf(mode(1920, 1080, 30), small), 1920, 1080, 60, true)
        assertEquals(SceneUsbConfiguration(small, 30), result.first())
        assertTrue(result.all { it.frameRate <= 30 && it.mode.width <= 1280 })
    }
    @Test fun `MJPEG is preferred to uncompressed at the same dimensions and rate`() {
        val mjpeg = mode(640, 480, 30)
        val yuyv = mode(640, 480, 30, format = SceneUsbFormat.YUYV)
        assertEquals(listOf(SceneUsbConfiguration(mjpeg, 30), SceneUsbConfiguration(yuyv, 30)),
            sceneUsbConfigurations(listOf(yuyv, mjpeg), 640, 480, 30, false))
    }
    @Test fun `minimum supported size fallback ignores modes that cannot meet concurrent FPS`() {
        val valid = mode(1920, 1080, 30)
        val result = sceneUsbConfigurations(listOf(mode(1280, 960, 60), valid), 640, 480, 30, true)
        assertEquals(SceneUsbConfiguration(valid, 30), result.single())
    }
    @Test fun `fixed rate sources may exceed single output demand but not concurrent demand`() {
        val fixed = mode(640, 480, 60)
        assertEquals(60, sceneUsbConfigurations(listOf(fixed), 640, 480, 30, false).single().frameRate)
        assertTrue(sceneUsbConfigurations(listOf(fixed), 640, 480, 30, true).isEmpty())
    }
    @Test fun `fixed 30 fps camera remains usable with a 24 fps concurrent output`() {
        val fixed = mode(640, 480, 30)
        assertEquals(30, sceneUsbConfigurations(listOf(fixed), 640, 480, 24, true).single().frameRate)
        val slow = mode(640, 480, 15, 30)
        assertEquals(15, sceneUsbConfigurations(listOf(slow), 640, 480, 24, true).first().frameRate)
    }
    @Test fun `invalid oversized and unknown rate modes are excluded`() {
        val invalid = listOf(mode(0, 480, 30), mode(8192, 8192, 30), mode(5000, 100, 30), mode(640, 480, 0, 121))
        assertTrue(sceneUsbConfigurations(invalid, 1920, 1080, 30, false).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { sceneUsbConfigurations(emptyList(), 0, 1080, 30, false) }
    }
}
