package com.vivid.feature.streaming.scene.editor

import com.vivid.core.data.SceneTransform
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Shared by one/two-finger manipulation; all positions remain in canvas coordinates. */
object SceneEditorGestureMath {
    fun transform(start: SceneTransform, panX: Float, panY: Float, zoom: Float, rotation: Float): SceneTransform {
        if (!listOf(panX, panY, zoom, rotation).all { it.isFinite() } || zoom <= 0f) return start
        val width = (start.width * zoom).coerceIn(MIN_SIZE, SceneTransform.MAX_EXTENT)
        val height = (start.height * zoom).coerceIn(MIN_SIZE, SceneTransform.MAX_EXTENT)
        return start.copy(x = position(start.x + start.width / 2f + panX - width / 2f),
            y = position(start.y + start.height / 2f + panY - height / 2f), width = width, height = height,
            rotationDegrees = angle(start.rotationDegrees.toDouble() + rotation))
    }

    fun resize(start: SceneTransform, pointerX: Float, pointerY: Float, width: Float, height: Float): SceneTransform {
        if (width <= 0f || height <= 0f || !listOf(width, height, pointerX, pointerY).all { it.isFinite() }) return start
        val dx = (pointerX.toDouble() - start.x - start.width / 2f) * width
        val dy = (pointerY.toDouble() - start.y - start.height / 2f) * height
        val radians = Math.toRadians(-start.rotationDegrees.toDouble())
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        val w = (2 * abs(localX) / width).toFloat().coerceIn(MIN_SIZE, SceneTransform.MAX_EXTENT)
        val h = (2 * abs(localY) / height).toFloat().coerceIn(MIN_SIZE, SceneTransform.MAX_EXTENT)
        return start.copy(x = position(start.x + (start.width - w) / 2f),
            y = position(start.y + (start.height - h) / 2f), width = w, height = h)
    }

    fun rotation(start: SceneTransform, fromX: Float, fromY: Float, toX: Float, toY: Float,
        width: Float, height: Float): SceneTransform {
        val cx = start.x + start.width / 2f
        val cy = start.y + start.height / 2f
        val before = atan2((fromY - cy) * height, (fromX - cx) * width)
        val after = atan2((toY - cy) * height, (toX - cx) * width)
        return transform(start, 0f, 0f, 1f, Math.toDegrees((after - before).toDouble()).toFloat())
    }

    private fun position(value: Float) = value.coerceIn(-SceneTransform.MAX_EXTENT, SceneTransform.MAX_EXTENT)
    private fun angle(value: Double) = (((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0).toFloat()
    private const val MIN_SIZE = 0.02f
}
