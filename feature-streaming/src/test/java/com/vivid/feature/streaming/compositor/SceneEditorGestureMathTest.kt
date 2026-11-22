package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneTransform
import com.vivid.feature.streaming.scene.editor.SceneEditorGestureMath as Gestures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneEditorGestureMathTest {
    @Test fun `pinch maintains center while pan and rotation accumulate`() {
        val start = SceneTransform(.2f, .3f, .4f, .2f, 170f)
        val result = Gestures.transform(start, .1f, -.1f, 2f, 30f)
        assertEquals(.5f, result.x + result.width / 2, .0001f)
        assertEquals(.3f, result.y + result.height / 2, .0001f)
        assertEquals(.8f, result.width, .0001f)
        assertEquals(-160f, result.rotationDegrees)
    }

    @Test fun `rotated resize uses physical canvas aspect and keeps center`() {
        val start = SceneTransform(.25f, .25f, .5f, .5f, 90f)
        // Rotated bottom-right corner on a 2:1 canvas.
        val result = Gestures.resize(start, .375f, 1f, 200f, 100f)
        assertEquals(start.x, result.x, .0001f)
        assertEquals(start.y, result.y, .0001f)
        assertEquals(start.width, result.width, .0001f)
        assertEquals(start.height, result.height, .0001f)
    }

    @Test fun `rotation handle uses pixel distances on rectangular canvas`() {
        val start = SceneTransform(.25f, .25f, .5f, .5f)
        val result = Gestures.rotation(start, .5f, 0f, .75f, .5f, 200f, 100f)
        assertEquals(90f, result.rotationDegrees, .001f)
    }

    @Test fun `invalid gestures are ignored and extreme finite values remain valid`() {
        val start = SceneTransform()
        assertEquals(start, Gestures.transform(start, Float.NaN, 0f, 1f, 0f))
        assertEquals(start, Gestures.resize(start, .5f, .5f, Float.NaN, 1f))
        assertEquals(start, Gestures.transform(start, 0f, 0f, 0f, 0f))
        val result = Gestures.transform(start.copy(rotationDegrees = Float.MAX_VALUE),
            Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        assertTrue(result.rotationDegrees.isFinite())
        assertEquals(SceneTransform.MAX_EXTENT, result.width)
    }
}
