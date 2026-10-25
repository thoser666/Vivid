package com.vivid.feature.streaming.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.CameraControlSettings
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.ZoomRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w320dp-h480dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewSelectionRowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `nearby presets keep distinct labels and only one selection`() {
        compose.setContent { MaterialTheme {
            PreviewSelectionRows(emptyList(), null, CameraControlSettings(rearZoomPresets = listOf(2.333f, 2.334f)),
                ZoomPickerCameraState(range = ZoomRange(1f, 4f), zoom = 2.333f), {}, {})
        } }
        compose.onNodeWithText("2.333×").assertIsDisplayed()
        compose.onNodeWithText("2.334×").assertIsDisplayed()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithTag("preview_zoom_2.333").assertIsSelected()
        compose.onNodeWithTag("preview_zoom_2.334").assertIsNotSelected()
    }

    @Test fun `long scene rows scroll and activate enabled scenes in saved order`() {
        val scenes = (1..20).map { StreamScene("$it", "Scene $it", enabled = it != 10) }
        var chosen: StreamScene? = null
        compose.setContent { MaterialTheme {
            PreviewSelectionRows(scenes, "1", CameraControlSettings(), ZoomPickerCameraState(), { chosen = it }, {})
        } }
        compose.onNodeWithTag("preview_scene_10").assertDoesNotExist()
        compose.onNodeWithTag("preview_scene_20").performScrollTo().performTouchInput { click() }
        assertEquals(scenes.last(), chosen)
    }
}
