package com.vivid.feature.streaming.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneLayer
import com.vivid.feature.streaming.compositor.SceneGeometry
import com.vivid.feature.streaming.scene.editor.*
import kotlin.math.hypot

/** FBO preview and editing share the exact canvas letterbox and normalized layer geometry. */
@androidx.compose.runtime.Composable
internal fun SceneEditorCanvas(
    state: SceneEditorState,
    description: String,
    color: Color,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestState by rememberUpdatedState(state)
    val edit by rememberUpdatedState(onEdit)
    Canvas(modifier.testTag("scene_editor_canvas").semantics { contentDescription = description }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val graph = latestState.composition
                val rect = canvasRect(graph, size.width.toFloat(), size.height.toFloat())
                fun normalized(position: Offset) = Offset((position.x - rect.left) / rect.width,
                    (position.y - rect.top) / rect.height)
                val point = normalized(down.position)
                val selected = latestState.selectedLayer
                val handles = selected?.let { handles(it, graph, rect, size.width.toFloat(), size.height.toFloat(), 12.dp.toPx()) }
                val radius = 24.dp.toPx()
                var mode = when {
                    handles != null && (down.position - handles.second).getDistance() <= radius -> 2
                    handles != null && (down.position - handles.first).getDistance() <= radius -> 1
                    else -> 0
                }
                val layer = if (mode != 0) selected else graph.layers.asReversed().firstOrNull {
                    rect.contains(down.position) && it.visible && it.opacity > 0f && SceneGeometry.contains(
                        it.transform, SceneGeometry.Point(point.x, point.y), graph.canvasWidth, graph.canvasHeight)
                }
                down.consume()
                edit { select(layer?.id) }
                if (layer == null) return@awaitEachGesture
                edit { beginGesture() }
                val start = layer.transform
                var transform = start
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.any { it.isConsumed }) break
                        val pan = event.calculatePan()
                        transform = if (pressed.size >= 2) {
                            mode = 0
                            SceneEditorGestureMath.transform(transform, pan.x / rect.width, pan.y / rect.height,
                                event.calculateZoom(), event.calculateRotation())
                        } else {
                            val current = normalized(pressed.first().position)
                            when (mode) {
                                1 -> SceneEditorGestureMath.resize(start, current.x, current.y, rect.width, rect.height)
                                2 -> SceneEditorGestureMath.rotation(start, point.x, point.y, current.x, current.y,
                                    rect.width, rect.height)
                                else -> SceneEditorGestureMath.transform(transform, pan.x / rect.width, pan.y / rect.height, 1f, 0f)
                            }
                        }
                        edit { transformGesture(transform, rect.width.toInt().coerceAtLeast(1), rect.height.toInt().coerceAtLeast(1)) }
                        event.changes.forEach { it.consume() }
                    }
                } finally { edit { endGesture() } }
            }
        }) {
        val graph = state.composition
        val rect = canvasRect(graph, size.width, size.height)
        drawRect(color.copy(alpha = 0.5f), Offset(rect.left, rect.top), rect.size, style = Stroke(1.dp.toPx()))
        state.verticalGuide?.let { x ->
            drawLine(color, Offset(rect.left + x * rect.width, rect.top),
                Offset(rect.left + x * rect.width, rect.bottom), 1.dp.toPx())
        }
        state.horizontalGuide?.let { y ->
            drawLine(color, Offset(rect.left, rect.top + y * rect.height),
                Offset(rect.right, rect.top + y * rect.height), 1.dp.toPx())
        }
        state.selectedLayer?.let { layer ->
            val corners = SceneGeometry.corners(layer.transform, graph.canvasWidth, graph.canvasHeight)
                .map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
            val path = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[3].x, corners[3].y)
                lineTo(corners[2].x, corners[2].y)
                close()
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            val (resize, rotate) = handles(layer, graph, rect, size.width, size.height, 12.dp.toPx())
            drawCircle(color, 8.dp.toPx(), resize)
            drawCircle(color, 8.dp.toPx(), rotate)
        }
    }
}

internal fun canvasRect(graph: SceneComposition, width: Float, height: Float): Rect {
    val scale = minOf(width / graph.canvasWidth, height / graph.canvasHeight)
    val w = graph.canvasWidth * scale
    val h = graph.canvasHeight * scale
    return Rect((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
}

private fun handles(layer: SceneLayer, graph: SceneComposition, rect: Rect,
    width: Float, height: Float, inset: Float): Pair<Offset, Offset> {
    val points = SceneGeometry.corners(layer.transform, graph.canvasWidth, graph.canvasHeight)
        .map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
    fun clamp(point: Offset) = Offset(point.x.coerceIn(0f, width), point.y.coerceIn(0f, height))
    val top = (points[2] + points[3]) / 2f
    val center = (points[0] + points[3]) / 2f
    val vector = top - center
    val length = hypot(vector.x, vector.y).coerceAtLeast(1f)
    return clamp(points[1]) to clamp(top + vector / length * inset)
}
