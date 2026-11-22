package com.vivid.feature.streaming.compositor

import com.vivid.feature.streaming.compositor.capture.SceneCameraGeometry
import com.vivid.feature.streaming.compositor.capture.SceneCameraSampling
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneCameraGeometryTest {
    @Test fun `buffer axes account for sensor and display quarter turns`() {
        val displays = listOf(0, 90, 180, 270)
        assertEquals(listOf(true, false, true, false), displays.map { SceneCameraGeometry.swapsBufferAxes(90, it) })
        assertEquals(listOf(true, false, true, false), displays.map { SceneCameraGeometry.swapsBufferAxes(270, it) })
        assertEquals(listOf(false, true, false, true), displays.map { SceneCameraGeometry.swapsBufferAxes(0, it) })
        assertEquals(listOf(0, 270, 180, 90), displays.map(SceneCameraGeometry::displayCorrection))
        assertThrows(IllegalArgumentException::class.java) { SceneCameraGeometry.swapsBufferAxes(45, 0) }
    }

    @Test fun `additional matrices rotate image coordinates before the GL origin flip`() {
        fun uv(rotation: Int, x: Float, y: Float): List<Float> {
            val m = SceneCameraGeometry.samplingMatrix(rotation)
            return listOf(m[0] * x + m[4] * y + m[12], m[1] * x + m[5] * y + m[13])
        }
        assertEquals(listOf(.2f, .7f), uv(0, .2f, .3f))
        assertEquals(listOf(.3f, .2f), uv(90, .2f, .3f))
        assertEquals(listOf(.8f, .3f), uv(180, .2f, .3f))
        assertEquals(listOf(.7f, .8f), uv(270, .2f, .3f))
    }

    @Test fun `automatic HAL rotation waits for a confirmed raw request and never guesses another frames mode`() {
        val sampling = SceneCameraSampling({ 0 }, true)
        assertNull(sampling.rotation(100))
        sampling.record(99, 0, false)
        sampling.record(100, 90, false)
        assertNull(sampling.rotation(100))
        sampling.record(101, 0, true)
        assertNull(sampling.rotation(100))
        assertEquals(0, sampling.rotation(101))
        assertEquals(0, sampling.rotation(102))
    }

    @Test fun `raw requests support live display changes without reapplying sensor orientation`() {
        var display = 0
        val sampling = SceneCameraSampling({ display }, true)
        sampling.record(100, 0, true)
        assertEquals(0, sampling.rotation(101))
        display = 90
        assertEquals(270, sampling.rotation(101))
        assertNull(sampling.rotation(99))
        sampling.record(102, 90, false)
        assertNull(sampling.rotation(103))
    }

    @Test fun `implicit AUTO none is insufficient while unsupported devices need no raw confirmation`() {
        val sampling = SceneCameraSampling({ 0 }, true)
        repeat(20) { sampling.record(it + 1L, 0, false) }
        assertNull(sampling.rotation(21))
        assertEquals(0, SceneCameraSampling({ 0 }, false).rotation(1))
    }

    @Test fun `late metadata cannot revoke a newer raw confirmation or restore an obsolete one`() {
        val sampling = SceneCameraSampling({ 0 }, true)
        sampling.record(200, 0, true)
        sampling.record(199, 90, false)
        assertEquals(0, sampling.rotation(201))
        sampling.record(210, 90, false)
        sampling.record(205, 0, true)
        assertNull(sampling.rotation(211))
    }

    @Test fun `embedded sensor rotation mirroring and crop are retained exactly once`() {
        fun apply(m: FloatArray, uv: FloatArray) = floatArrayOf(
            m[0] * uv[0] + m[4] * uv[1] + m[12],
            m[1] * uv[0] + m[5] * uv[1] + m[13])
        // A rear-camera producer transform: sensor rotation plus the platform's GL flip.
        val rear = SceneCameraGeometry.samplingMatrix(270)
        rear[0] *= .5f; rear[4] *= .5f; rear[12] = .25f + .5f * rear[12]
        val natural = apply(rear, apply(SceneCameraGeometry.samplingMatrix(0), floatArrayOf(.2f, .3f)))
        assertArrayEquals(floatArrayOf(.4f, .8f), natural, .00001f)
        val landscape = apply(rear, apply(SceneCameraGeometry.samplingMatrix(270), floatArrayOf(.2f, .3f)))
        assertArrayEquals(floatArrayOf(.35f, .3f), landscape, .00001f)
        // A mirrored producer keeps its existing mirror; the scene does not introduce a second one.
        val mirrored = rear.copyOf().apply { this[0] = -this[0]; this[1] = -this[1]; this[12] += rear[0]; this[13] += rear[1] }
        val front = apply(mirrored, apply(SceneCameraGeometry.samplingMatrix(0), floatArrayOf(.2f, .3f)))
        assertArrayEquals(floatArrayOf(.4f, .2f), front, .00001f)
    }
}
