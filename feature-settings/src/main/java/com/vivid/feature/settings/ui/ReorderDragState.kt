package com.vivid.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vivid.feature.settings.R
import kotlinx.coroutines.isActive

/** Shared drag geometry; the stationary parent owns gestures while keyed rows move. */
internal class ReorderDragState<T>(private val savedOrder: List<T>) {
    var order by mutableStateOf(savedOrder)
    var dragging by mutableStateOf<T?>(null)
    var dragCenter by mutableFloatStateOf(0f)
    var pointerY by mutableFloatStateOf(0f)
    var moved by mutableStateOf(false)
    var listPosition by mutableStateOf(Offset.Zero)
    val centers = mutableStateMapOf<T, Float>()
    val heights = mutableStateMapOf<T, Int>()
    val handles = mutableStateMapOf<T, Rect>()

    fun start(position: Offset, handlesOnly: Boolean) {
        val point = position + listPosition
        pointerY = point.y
        dragging = order.firstOrNull { hit(it, point, handlesOnly) }
        moved = false
        dragging?.let { dragCenter = centers.getValue(it) }
    }

    private fun hit(value: T, point: Offset, handlesOnly: Boolean): Boolean {
        if (handlesOnly) return handles[value]?.contains(point) == true
        val center = centers[value] ?: return false
        val halfHeight = (heights[value] ?: 0) / 2f
        return point.y in (center - halfHeight)..(center + halfHeight)
    }

    fun drag(localY: Float): Boolean {
        if (dragging == null) return false
        moved = true
        val nextY = listPosition.y + localY
        dragCenter += nextY - pointerY
        pointerY = nextY
        moveDraggedRow()
        return true
    }

    fun moveDraggedRow() {
        val value = dragging ?: return
        val remaining = order.filter { it != value }
        val target = remaining.count { (centers[it] ?: Float.MAX_VALUE) < dragCenter }
        order = remaining.toMutableList().apply { add(target, value) }
    }

    fun finish(save: Boolean, onReorder: (List<T>) -> Unit) {
        if (save && order != savedOrder) onReorder(order)
        if (!save) order = savedOrder
        dragging = null
        moved = false
    }

    fun move(index: Int, target: Int, onReorder: (List<T>) -> Unit): Boolean {
        order = order.toMutableList().apply { add(target, removeAt(index)) }
        onReorder(order)
        return true
    }
}

@Composable
internal fun <T> rememberReorderDragState(order: List<T>): ReorderDragState<T> =
    remember(order) { ReorderDragState(order) }

@Composable
internal fun <T> ReorderAutoScroll(state: ReorderDragState<T>, scroll: ScrollState, requireMove: Boolean = false) {
    val density = LocalDensity.current
    val bottom = LocalWindowInfo.current.containerSize.height.toFloat()
    val edge = with(density) { 80.dp.toPx() }
    val top = with(density) { 104.dp.toPx() }
    val step = with(density) { 12.dp.toPx() }
    LaunchedEffect(state.dragging, if (requireMove) state.moved else false) {
        while (isActive && state.dragging != null && (!requireMove || state.moved)) {
            withFrameNanos { }
            val delta = reorderScrollDelta(state.pointerY, top, bottom - edge, step)
            if (delta != 0f && scroll.scrollBy(delta) != 0f) state.moveDraggedRow()
        }
    }
}

private fun reorderScrollDelta(pointer: Float, top: Float, bottom: Float, step: Float): Float = when {
    pointer > bottom -> step
    pointer < top -> -step
    else -> 0f
}

internal fun <T> Modifier.reorderGestures(
    state: ReorderDragState<T>, enabled: Boolean = true, handlesOnly: Boolean = false,
    onReorder: (List<T>) -> Unit,
): Modifier = onGloballyPositioned { state.listPosition = it.positionInRoot() }
    .pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(it, handlesOnly) },
            onDrag = { change, _ -> if (state.drag(change.position.y)) change.consume() },
            onDragEnd = { state.finish(true, onReorder) },
            onDragCancel = { state.finish(false, onReorder) },
        )
    }

internal fun <T> Modifier.reorderVisuals(state: ReorderDragState<T>, value: T): Modifier = graphicsLayer {
    translationY = if (state.dragging == value) state.dragCenter - (state.centers[value] ?: state.dragCenter) else 0f
    shadowElevation = if (state.dragging == value) 8.dp.toPx() else 0f
}

@Composable
internal fun <T> ReorderHandle(
    state: ReorderDragState<T>, index: Int, value: T, description: String, tag: String,
    enabled: Boolean = true, onReorder: (List<T>) -> Unit,
) {
    val moveUp = stringResource(R.string.stream_move_up)
    val moveDown = stringResource(R.string.stream_move_down)
    Box(Modifier.size(48.dp).testTag(tag)
        .onGloballyPositioned { state.handles[value] = it.boundsInRoot() }
        .semantics {
            contentDescription = description
            customActions = if (!enabled) emptyList() else buildList {
                if (index > 0) add(CustomAccessibilityAction(moveUp) { state.move(index, index - 1, onReorder) })
                if (index < state.order.lastIndex) add(CustomAccessibilityAction(moveDown) { state.move(index, index + 1, onReorder) })
            }
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.DragHandle, contentDescription = null)
    }
}
