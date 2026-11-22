package com.vivid.feature.streaming.compositor

import com.vivid.core.data.SceneTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Shared geometry for GPU vertices and editor hit testing; rotation uses canvas pixels. */
object SceneGeometry {
    data class Point(val x: Float, val y: Float)
    data class SnappedTransform(
        val transform: SceneTransform,
        val verticalGuide: Float?,
        val horizontalGuide: Float?,
    )

    fun corners(transform: SceneTransform, canvasWidth: Int, canvasHeight: Int): List<Point> {
        require(canvasWidth > 0 && canvasHeight > 0)
        val angle = Math.toRadians(transform.rotationDegrees.toDouble())
        val cosine = cos(angle).toFloat()
        val sine = sin(angle).toFloat()
        val centerX = (transform.x + transform.width / 2f) * canvasWidth
        val centerY = (transform.y + transform.height / 2f) * canvasHeight
        return listOf(0f to 1f, 1f to 1f, 0f to 0f, 1f to 0f).map { (u, v) ->
            val dx = (u - 0.5f) * transform.width * canvasWidth
            val dy = (v - 0.5f) * transform.height * canvasHeight
            Point((centerX + dx * cosine - dy * sine) / canvasWidth,
                (centerY + dx * sine + dy * cosine) / canvasHeight)
        }
    }

    /** RootEncoder triangle-strip format: clip-space xyz followed by top-left-origin UV. */
    fun vertices(transform: SceneTransform, canvasWidth: Int, canvasHeight: Int): FloatArray {
        val crop = transform.crop
        val uv = listOf(crop.left to crop.bottom, crop.right to crop.bottom,
            crop.left to crop.top, crop.right to crop.top)
        return corners(transform, canvasWidth, canvasHeight).flatMapIndexed { index, point ->
            listOf(point.x * 2f - 1f, 1f - point.y * 2f, 0f, uv[index].first, uv[index].second)
        }.toFloatArray()
    }

    fun contains(transform: SceneTransform, point: Point, canvasWidth: Int, canvasHeight: Int): Boolean {
        require(canvasWidth > 0 && canvasHeight > 0)
        if (!point.x.isFinite() || !point.y.isFinite()) return false
        val angle = Math.toRadians(-transform.rotationDegrees.toDouble())
        val dx = (point.x - transform.x - transform.width / 2f) * canvasWidth
        val dy = (point.y - transform.y - transform.height / 2f) * canvasHeight
        val localX = dx * cos(angle) - dy * sin(angle)
        val localY = dx * sin(angle) + dy * cos(angle)
        return abs(localX) <= transform.width * canvasWidth / 2f + 0.001 &&
            abs(localY) <= transform.height * canvasHeight / 2f + 0.001
    }

    /** Snaps the rotated bounding box's edges/center to canvas edges/center. */
    fun snap(transform: SceneTransform, canvasWidth: Int, canvasHeight: Int,
        thresholdPixels: Float = 8f): SnappedTransform {
        require(thresholdPixels.isFinite() && thresholdPixels >= 0)
        val points = corners(transform, canvasWidth, canvasHeight)
        fun offset(values: List<Float>, threshold: Float): Pair<Float, Float?> {
            val min = values.min()
            val max = values.max()
            val candidates = listOf(min, (min + max) / 2f, max).flatMap { edge ->
                listOf(0f, 0.5f, 1f).map { anchor -> (anchor - edge) to anchor }
            }.filter { abs(it.first) <= threshold }
            return candidates.minByOrNull { abs(it.first) } ?: (0f to null)
        }
        val horizontal = offset(points.map { it.x }, thresholdPixels / canvasWidth)
        val vertical = offset(points.map { it.y }, thresholdPixels / canvasHeight)
        return SnappedTransform(transform.copy(x = transform.x + horizontal.first,
            y = transform.y + vertical.first), horizontal.second, vertical.second)
    }
}
