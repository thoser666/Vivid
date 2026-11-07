package com.vivid.feature.settings.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.CameraControlSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoomPresetReorderingTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(CameraControlsUiState(loaded = true,
        settings = CameraControlSettings(rearZoomPresets = listOf(1f, 1.5f, 2f), frontZoomPresets = listOf(1f, 2f))))
    private var saves = 0

    private fun show() {
        compose.setContent {
            CameraZoomScreen(state.value, {}, { update ->
                saves++
                state.value = state.value.copy(settings = update(state.value.settings))
            })
        }
    }

    private fun drag(prefix: String, first: Float, last: Float, cancelGesture: Boolean = false) {
        val from = compose.onNodeWithTag("${prefix}_zoom_drag_$first", useUnmergedTree = true)
        val start = from.fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithTag("${prefix}_zoom_drag_$last", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        from.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, end.y - start.y + if (end.y > start.y) 8f else -8f), delayMillis = 300)
            if (cancelGesture) cancel() else up()
        }
        compose.waitForIdle()
    }

    @Test fun `rear presets save new order on drop without editing or changing front presets`() {
        show()
        drag("rear", 1f, 2f)
        assertEquals(listOf(1.5f, 2f, 1f), state.value.settings.rearZoomPresets)
        assertEquals(listOf(1f, 2f), state.value.settings.frontZoomPresets)
        assertEquals(1, saves)
        compose.onNodeWithText("Zoom ratio").assertDoesNotExist()
        val first = compose.onNodeWithTag("rear_zoom_row_1.5").fetchSemanticsNode().boundsInRoot.top
        val last = compose.onNodeWithTag("rear_zoom_row_1.0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(first < last)
    }

    @Test fun `front presets can be dragged upward and rear order remains intact`() {
        show()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("front_zoom_drag_2.0"))
        drag("front", 2f, 1f)
        assertEquals(listOf(2f, 1f), state.value.settings.frontZoomPresets)
        assertEquals(listOf(1f, 1.5f, 2f), state.value.settings.rearZoomPresets)
        assertEquals(1, saves)
    }

    @Test fun `cancelled drag restores the visual order without persisting`() {
        show()
        drag("rear", 1f, 2f, cancelGesture = true)
        assertEquals(0, saves)
        assertEquals(listOf(1f, 1.5f, 2f), state.value.settings.rearZoomPresets)
        val first = compose.onNodeWithTag("rear_zoom_row_1.0").fetchSemanticsNode().boundsInRoot.top
        val last = compose.onNodeWithTag("rear_zoom_row_2.0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(first < last)
    }

    @Test fun `drag near the bottom scrolls a long list and persists only on release`() {
        state.value = state.value.copy(settings = state.value.settings.copy(rearZoomPresets = (1..15).map { it.toFloat() }))
        show()
        val handle = compose.onNodeWithTag("rear_zoom_drag_1.0", useUnmergedTree = true)
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val bottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        compose.mainClock.autoAdvance = false
        try {
            handle.performTouchInput {
                down(center)
                advanceEventTime(700)
                moveBy(Offset(0f, bottom - start.y - 12f), delayMillis = 300)
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            assertEquals(0, saves)
            val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("The list must scroll while the held row reaches the bottom edge", scroll > 0f)
            compose.onRoot().performTouchInput { up() }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTrue("Order: ${state.value.settings.rearZoomPresets}", state.value.settings.rearZoomPresets.indexOf(1f) > 5)
        assertEquals(1, saves)
        assertEquals((1..15).map { it.toFloat() }.toSet(), state.value.settings.rearZoomPresets.toSet())
    }

    @Test fun `accessibility move actions reorder the same saved presets`() {
        show()
        val actions = compose.onNodeWithTag("rear_zoom_drag_2.0", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == "Move up" }.action()) }
        assertEquals(listOf(1f, 2f, 1.5f), state.value.settings.rearZoomPresets)
        assertEquals(1, saves)
    }
}
