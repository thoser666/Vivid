package com.vivid.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Delete
import com.vivid.feature.settings.R
import kotlinx.coroutines.isActive

/** The fixed list container owns the drag so moving a row cannot move the gesture coordinates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SceneItemsList(
    items: List<T>,
    itemId: (T) -> String,
    itemName: (T) -> String,
    itemEnabled: (T) -> Boolean,
    onDelete: (String) -> Unit,
    scrollState: ScrollState,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    if (items.isEmpty()) return
    val savedIds = items.map(itemId)
    var order by remember(savedIds) { mutableStateOf(savedIds) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var listTop by remember { mutableFloatStateOf(0f) }
    val centers = remember { mutableStateMapOf<String, Float>() }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val density = LocalDensity.current
    val screenBottom = LocalWindowInfo.current.containerSize.height.toFloat()
    val edge = with(density) { 80.dp.toPx() }
    val scrollStep = with(density) { 12.dp.toPx() }
    val moveUp = stringResource(R.string.stream_move_up)
    val moveDown = stringResource(R.string.stream_move_down)
    val currentReorder by rememberUpdatedState(onReorder)

    fun moveDraggedRow() {
        val id = dragging ?: return
        val remaining = order.filter { it != id }
        val target = remaining.count { (centers[it] ?: Float.MAX_VALUE) < dragCenter }
        val reordered = remaining.toMutableList().apply { add(target, id) }
        if (reordered != order) order = reordered
    }

    fun finishDrag(save: Boolean) {
        if (save && order != savedIds) currentReorder(order) else if (!save) order = savedIds
        dragging = null
    }

    LaunchedEffect(dragging) {
        while (isActive && dragging != null) {
            withFrameNanos { }
            val delta = when {
                pointerY > screenBottom - edge -> scrollStep
                pointerY < edge + with(density) { 24.dp.toPx() } -> -scrollStep
                else -> 0f
            }
            if (delta != 0f && scrollState.scrollBy(delta) != 0f) moveDraggedRow()
        }
    }

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().testTag("scene_item_profiles_list")
                .onGloballyPositioned { listTop = it.positionInRoot().y }
                .pointerInput(savedIds) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            pointerY = listTop + offset.y
                            dragging = order.firstOrNull { id ->
                                val center = centers[id] ?: return@firstOrNull false
                                val halfHeight = (heights[id] ?: 0) / 2f
                                pointerY in (center - halfHeight)..(center + halfHeight)
                            }
                            dragging?.let { dragCenter = centers.getValue(it) }
                        },
                        onDrag = { change, _ ->
                            if (dragging != null) {
                                change.consume()
                                val nextPointerY = listTop + change.position.y
                                dragCenter += nextPointerY - pointerY
                                pointerY = nextPointerY
                                moveDraggedRow()
                            }
                        },
                        onDragEnd = { finishDrag(true) },
                        onDragCancel = { finishDrag(false) },
                    )
                },
        ) {
            val byId = items.associateBy(itemId)
            order.forEachIndexed { index, id ->
                val item = byId.getValue(id)
                key(id) {
                    Box(
                        Modifier.zIndex(if (dragging == id) 1f else 0f)
                            .onGloballyPositioned {
                                centers[id] = it.positionInRoot().y + it.size.height / 2f
                                heights[id] = it.size.height
                            },
                    ) {
                        val dismissState = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete(id)
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = dragging == null,
                            backgroundContent = {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.scene_widget_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            },
                        ) {
                        ListItem(
                            headlineContent = { Text(itemName(item)) },

                            leadingContent = {
                                val description = stringResource(R.string.scene_widget_reorder, itemName(item))
                                Box(Modifier.size(48.dp).testTag("scene_item_drag_$id").semantics {
                                    contentDescription = description
                                    customActions = buildList {
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
                            },
                            trailingContent = {
                                StreamSwitchAction("scene_item_switch_divider_$id") {
                                    Switch(checked = itemEnabled(item), onCheckedChange = { onToggle(id, it) },
                                        modifier = Modifier.testTag("scene_item_toggle_$id").semantics { contentDescription = itemName(item) })
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth().testTag("scene_item_row_$id")
                                .graphicsLayer {
                                    translationY = if (dragging == id) dragCenter - (centers[id] ?: dragCenter) else 0f
                                    shadowElevation = if (dragging == id) 8.dp.toPx() else 0f
                                }
                                .clickable { onOpen(id) },
                        )
                        }
                    }
                    if (index < order.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
