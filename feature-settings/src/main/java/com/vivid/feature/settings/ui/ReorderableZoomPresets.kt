package com.vivid.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vivid.feature.settings.R
import kotlinx.coroutines.isActive

/** The stationary column owns the gesture while keyed rows move below the held handle. */
@Composable
internal fun ReorderableZoomPresets(
    presets: List<Float>,
    prefix: String,
    enabled: Boolean,
    scrollState: ScrollState,
    onReorder: (List<Float>) -> Unit,
    rowContent: @Composable RowScope.(Int, Float) -> Unit,
) {
    var order by remember(presets) { mutableStateOf(presets) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var moved by remember { mutableStateOf(false) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var listPosition by remember { mutableStateOf(Offset.Zero) }
    val centers = remember { mutableStateMapOf<Float, Float>() }
    val handles = remember { mutableStateMapOf<Float, Rect>() }
    val currentReorder by rememberUpdatedState(onReorder)
    val density = LocalDensity.current
    val screenBottom = LocalWindowInfo.current.containerSize.height.toFloat()
    val edge = with(density) { 80.dp.toPx() }
    val scrollStep = with(density) { 12.dp.toPx() }
    val topEdge = with(density) { 104.dp.toPx() }
    val moveUp = stringResource(R.string.stream_move_up)
    val moveDown = stringResource(R.string.stream_move_down)

    fun moveDraggedRow() {
        val value = dragging ?: return
        val remaining = order.filter { it != value }
        val target = remaining.count { (centers[it] ?: Float.MAX_VALUE) < dragCenter }
        order = remaining.toMutableList().apply { add(target, value) }
    }

    fun finishDrag(save: Boolean) {
        if (save && order != presets) currentReorder(order)
        if (!save) order = presets
        dragging = null
        moved = false
    }

    LaunchedEffect(dragging, moved) {
        while (isActive && dragging != null && moved) {
            withFrameNanos { }
            val delta = when {
                pointerY > screenBottom - edge -> scrollStep
                pointerY < topEdge -> -scrollStep
                else -> 0f
            }
            if (delta != 0f && scrollState.scrollBy(delta) != 0f) moveDraggedRow()
        }
    }

    Column(Modifier.fillMaxWidth().testTag("${prefix}_zoom_presets")
        .onGloballyPositioned { listPosition = it.positionInRoot() }
        .pointerInput(presets, enabled) {
            if (!enabled) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    val point = position + listPosition
                    dragging = order.firstOrNull { handles[it]?.contains(point) == true }
                    moved = false
                    pointerY = point.y
                    dragging?.let { dragCenter = centers.getValue(it) }
                },
                onDrag = { change, _ ->
                    if (dragging != null) {
                        change.consume()
                        moved = true
                        val nextY = listPosition.y + change.position.y
                        dragCenter += nextY - pointerY
                        pointerY = nextY
                        moveDraggedRow()
                    }
                },
                onDragEnd = { finishDrag(true) },
                onDragCancel = { finishDrag(false) },
            )
        }) {
        order.forEachIndexed { index, value ->
            key(value) {
                Row(Modifier.fillMaxWidth().zIndex(if (dragging == value) 1f else 0f)
                    .onGloballyPositioned { centers[value] = it.positionInRoot().y + it.size.height / 2f }
                    .graphicsLayer {
                        translationY = if (dragging == value) dragCenter - (centers[value] ?: dragCenter) else 0f
                        shadowElevation = if (dragging == value) 8.dp.toPx() else 0f
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp).testTag("${prefix}_zoom_row_$value"),
                    verticalAlignment = Alignment.CenterVertically) {
                    val description = stringResource(R.string.camera_zoom_reorder,
                        stringResource(R.string.camera_zoom_ratio, value.toString()))
                    Box(Modifier.size(48.dp).testTag("${prefix}_zoom_drag_$value")
                        .onGloballyPositioned { handles[value] = it.boundsInRoot() }
                        .semantics {
                            contentDescription = description
                            customActions = if (!enabled) emptyList() else buildList {
                                fun move(to: Int): Boolean {
                                    val updated = order.toMutableList().apply { add(to, removeAt(index)) }
                                    order = updated
                                    currentReorder(updated)
                                    return true
                                }
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { move(index - 1) })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) { move(index + 1) })
                            }
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DragHandle, contentDescription = null)
                    }
                    rowContent(index, value)
                }
            }
            if (index < order.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}
