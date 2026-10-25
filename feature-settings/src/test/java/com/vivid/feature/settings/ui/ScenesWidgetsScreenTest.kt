package com.vivid.feature.settings.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.*
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FIRST_SCENE_NAME = "Scene A"
private const val TEXT_WIDGET_ID = "text"
private const val TEXT_WIDGET_NAME = "My text"
private const val IMAGE_WIDGET_NAME = "My logo"
private const val TEXT_WIDGET_ROW_TAG = "scene_item_row_text"
private const val DELETE_DIALOG_TAG = "library_widget_delete_dialog"
private const val SAVE_LABEL = "Save"
private const val ADD_WIDGET_LABEL = "Add widget"
private const val FIRST_SCENE_TOGGLE_TAG = "widget_scene_toggle_a"
private const val SHARED_SETTINGS_LABEL = "Shared widget settings"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScenesWidgetsScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val scenes = MutableStateFlow(listOf(StreamScene("a", FIRST_SCENE_NAME, widgets = listOf(SceneWidget(TEXT_WIDGET_ID))),
        StreamScene("b", "Scene B", widgets = emptyList())))
    private val widgets = MutableStateFlow(listOf(StreamWidget(TEXT_WIDGET_ID, TEXT_WIDGET_NAME, WidgetType.TEXT),
        StreamWidget("image", IMAGE_WIDGET_NAME, WidgetType.IMAGE)))
    private val privacy = MutableStateFlow(false)
    private val vm = mockk<StreamingViewModel>(relaxed = true) {
        every { this@mockk.scenes } returns this@ScenesWidgetsScreenTest.scenes
        every { this@mockk.widgets } returns this@ScenesWidgetsScreenTest.widgets
        every { activeSceneId } returns MutableStateFlow<String?>("a")
        every { libraryInitialized } returns MutableStateFlow(true)
        every { savedWidgetLayout } returns MutableStateFlow<SceneWidget?>(null)
        every { autoSwitchEnabled } returns MutableStateFlow(false)
        every { autoSwitchIntervalSeconds } returns MutableStateFlow(60L)
        every { privacyEnabled } returns privacy
        every { fullFrameBlurEnabled } returns MutableStateFlow(false)
        every { privacyZones } returns MutableStateFlow(emptyList())
        every { streamingEngine } returns mockk<StreamingEngine>(relaxed = true) {
            every { streamingState } returns MutableStateFlow<StreamingState>(StreamingState.Idle)
        }
        every { updateScene(any()) } answers {
            val value = firstArg<StreamScene>()
            this@ScenesWidgetsScreenTest.scenes.value = this@ScenesWidgetsScreenTest.scenes.value.map { if (it.id == value.id) value else it }
        }
        every { updateWidget(any()) } answers {
            val value = firstArg<StreamWidget>()
            this@ScenesWidgetsScreenTest.widgets.value = if (this@ScenesWidgetsScreenTest.widgets.value.any { it.id == value.id }) this@ScenesWidgetsScreenTest.widgets.value.map { if (it.id == value.id) value else it } else this@ScenesWidgetsScreenTest.widgets.value + value
        }
    }
    private fun open(timer: com.vivid.feature.widget.PomodoroWidgetViewModel? = null) {
        composeRule.setContent { SettingsOverlaysScreen(onBack = {}, scenesViewModel = vm, pomodoroViewModel = timer) }
    }
    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag("settings_section_content").performScrollToNode(hasText(text))
    }

    private fun screenshot(name: String) {
        val file = java.io.File("build/reports/scenes-widgets/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { composeRule.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `dragging reorders scenes without opening or toggling them`() {
        open()
        val start = composeRule.onNodeWithTag("scene_item_drag_a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val end = composeRule.onNodeWithTag("scene_item_drag_b", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        composeRule.onNodeWithTag("scene_item_drag_a", useUnmergedTree = true).performTouchInput {
            down(center); advanceEventTime(700)
            moveBy(Offset(0f, end.y - start.y + 12f), delayMillis = 300)
            up()
        }
        composeRule.waitForIdle()
        verify { vm.reorderScenes(listOf("b", "a")) }
        verify(exactly = 0) { vm.updateScene(any()) }
    }

    @Test fun `scene widget has its own enable switch and swipe removal`() {
        open()
        composeRule.onNodeWithText(FIRST_SCENE_NAME).performClick()
        composeRule.onNodeWithText("Tap a widget to adjust its position in this scene.").assertIsDisplayed()
        composeRule.onNodeWithTag("scene_item_toggle_text").performScrollTo().performClick()
        verify { vm.updateScene(match { it.id == "a" && !it.widgets!!.single().enabled }) }
        composeRule.onNodeWithTag(TEXT_WIDGET_ROW_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        verify { vm.updateScene(match { it.id == "a" && it.widgets!!.isEmpty() }) }
        composeRule.onNodeWithTag(DELETE_DIALOG_TAG).assertDoesNotExist()
        verify(exactly = 0) { vm.deleteWidget(any()) }
        org.junit.Assert.assertTrue(widgets.value.any { it.id == TEXT_WIDGET_ID })
    }

    @Test fun `library warns about all scenes and deletion can be cancelled and retried`() {
        open()
        composeRule.onNodeWithTag("widget_library_hint").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Enabling, disabling or deleting a widget here affects every scene where it is used.")
            .assertIsDisplayed()
        scrollTo(TEXT_WIDGET_NAME)
        composeRule.onNodeWithTag(TEXT_WIDGET_ROW_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(DELETE_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Remove “My text” from the widget library and every scene where it is used?")
            .assertIsDisplayed()
        verify(exactly = 0) { vm.deleteWidget(any()) }
        composeRule.onNodeWithTag("library_widget_delete_cancel").performClick()
        composeRule.onNodeWithTag(DELETE_DIALOG_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TEXT_WIDGET_ROW_TAG).assertIsDisplayed()
        verify(exactly = 0) { vm.deleteWidget(any()) }

        composeRule.onNodeWithTag(TEXT_WIDGET_ROW_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(DELETE_DIALOG_TAG).assertIsDisplayed()
        verify(exactly = 0) { vm.deleteWidget(any()) }
        composeRule.onNodeWithTag("library_widget_delete_confirm").performClick()
        composeRule.onNodeWithTag(DELETE_DIALOG_TAG).assertDoesNotExist()
        verify(exactly = 1) { vm.deleteWidget(TEXT_WIDGET_ID) }
        verify(exactly = 0) { vm.updateScene(any()) }
    }

    @Test fun `placement drag saves scene coordinates and keeps shared content untouched`() {
        open()
        composeRule.onNodeWithText(FIRST_SCENE_NAME).performClick()
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        screenshot("layout")
        scrollTo("Numeric values")
        composeRule.onNodeWithTag("scene_widget_canvas").performTouchInput {
            swipe(center, center + Offset(40f, 20f), durationMillis = 300)
        }
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        verify { vm.updateScene(match { it.id == "a" && it.widgets!!.single().x > 0f && it.widgets!!.single().y > 0f }) }
        verify(exactly = 0) { vm.updateWidget(any()) }
    }

    @Test fun `section lists scenes and shared widgets and adds a scene`() {
        open()
        composeRule.onNodeWithText("Scenes & widgets").assertIsDisplayed()
        screenshot("scenes-and-widgets")
        composeRule.onNodeWithText(FIRST_SCENE_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("Add scene").performClick()
        verify { vm.saveScene("Scene 3") }
        scrollTo(IMAGE_WIDGET_NAME)
        composeRule.onNodeWithText(IMAGE_WIDGET_NAME).assertIsDisplayed()
    }

    @Test fun `scene dropdown attaches an existing widget without duplicating the library`() {
        open()
        composeRule.onNodeWithText("Scene B").performClick()
        composeRule.onNodeWithText("Add from widgets").performClick()
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        verify { vm.updateScene(match { it.id == "b" && it.widgets == listOf(SceneWidget(TEXT_WIDGET_ID)) }) }
        verify(exactly = 0) { vm.updateWidget(any()) }
    }

    @Test fun `library toggle affects only the selected definition`() {
        open()
        scrollTo(IMAGE_WIDGET_NAME)
        composeRule.onNodeWithTag("scene_item_toggle_image").performClick()
        verify { vm.updateWidget(match { it.id == "image" && !it.enabled }) }
    }

    @Test fun `swiping a scene deletes it`() {
        open()
        composeRule.onNodeWithTag("scene_item_row_b").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        verify { vm.deleteScene("b") }
    }

    @Test fun `browser editor saves a named page with its viewport`() {
        open()
        scrollTo(ADD_WIDGET_LABEL)
        composeRule.onNodeWithText(ADD_WIDGET_LABEL).performClick()
        composeRule.onNodeWithText("Browser").performClick()
        scrollTo("Page URL (HTTPS)")
        composeRule.onNodeWithText("Page URL (HTTPS)").assertIsDisplayed()
        composeRule.onNodeWithText("Page width (px)").assertExists()
        composeRule.onNodeWithText("Page height (px)").assertExists()
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        verify { vm.updateWidget(match { it.type == WidgetType.BROWSER && it.configuration.browserWidth == 1280 }) }
    }

    @Test fun `widget editor shows scene selection and saves only edited switches`() {
        open()
        scrollTo(TEXT_WIDGET_NAME)
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        composeRule.onNodeWithText("Show in scenes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(FIRST_SCENE_TOGGLE_TAG).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("widget_scene_toggle_b").performScrollTo().assertIsOff().performClick().assertIsOn()
        verify(exactly = 0) { vm.updateWidgetScenes(any(), any()) }
        screenshot("widget-scenes")
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        verify { vm.updateWidgetScenes(match { it.id == TEXT_WIDGET_ID }, mapOf("b" to true)) }
    }

    @Test fun `widget editor can hide an existing placement and cancel without saving`() {
        open()
        scrollTo(TEXT_WIDGET_NAME)
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        composeRule.onNodeWithTag(FIRST_SCENE_TOGGLE_TAG).performScrollTo().performClick().assertIsOff()
        composeRule.onNodeWithContentDescription("Back").performClick()
        verify(exactly = 0) { vm.updateWidgetScenes(any(), any()) }
        scrollTo(TEXT_WIDGET_NAME)
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        composeRule.onNodeWithTag(FIRST_SCENE_TOGGLE_TAG).performScrollTo().assertIsOn().performClick()
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        verify { vm.updateWidgetScenes(match { it.id == TEXT_WIDGET_ID }, mapOf("a" to false)) }
    }

    @Test fun `scene placement toggle is reflected when opening shared widget settings`() {
        open()
        composeRule.onNodeWithText(FIRST_SCENE_NAME).performClick()
        composeRule.onNodeWithTag("scene_item_toggle_text").performClick()
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        scrollTo(SHARED_SETTINGS_LABEL)
        composeRule.onNodeWithText(SHARED_SETTINGS_LABEL).performClick()
        composeRule.onNodeWithTag(FIRST_SCENE_TOGGLE_TAG).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("widget_scene_toggle_b").performScrollTo().assertIsOff()
    }

    @Test fun `saving a layout after changing shared scene visibility keeps the new visibility`() {
        every { vm.updateWidgetScenes(any(), any()) } answers {
            scenes.value = scenes.value.map { scene ->
                if (scene.id == "a") scene.copy(widgets = listOf(SceneWidget(TEXT_WIDGET_ID, enabled = false))) else scene
            }
        }
        open()
        composeRule.onNodeWithText(FIRST_SCENE_NAME).performClick()
        composeRule.onNodeWithText(TEXT_WIDGET_NAME).performClick()
        scrollTo(SHARED_SETTINGS_LABEL)
        composeRule.onNodeWithText(SHARED_SETTINGS_LABEL).performClick()
        composeRule.onNodeWithTag(FIRST_SCENE_TOGGLE_TAG).performScrollTo().performClick()
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        composeRule.onNodeWithText("Layout").assertExists()
        scrollTo(SAVE_LABEL)
        composeRule.onNodeWithText(SAVE_LABEL).performClick()
        verify { vm.updateScene(match { it.id == "a" && !it.widgets!!.single().enabled }) }
    }

    @Test fun `pomodoro is a widget with editable durations and a save-and-start action`() {
        val timer = mockk<com.vivid.feature.widget.PomodoroWidgetViewModel>(relaxed = true) {
            every { states } returns MutableStateFlow<Map<String, PomodoroUiState>>(emptyMap())
        }
        open(timer)
        scrollTo(ADD_WIDGET_LABEL)
        composeRule.onNodeWithText(ADD_WIDGET_LABEL).performClick()
        composeRule.onNodeWithText("Pomodoro timer").performClick()
        scrollTo("Work session: 25 min")
        composeRule.onNodeWithTag("pomodoro_work_duration").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        scrollTo("Save and start")
        screenshot("pomodoro-widget-editor")
        composeRule.onNodeWithText("Save and start").performClick()
        verify { timer.saveAndStart(match { it.type == WidgetType.POMODORO && it.configuration.pomodoroWorkDurationMinutes == 1 }, emptyMap()) }
        composeRule.onNodeWithTag("widget_scene_selection").assertExists()
    }

    @Test fun `privacy and full frame controls are available in this section`() {
        privacy.value = true
        open()
        scrollTo("Full-frame pixelation")
        composeRule.onNodeWithTag("full_frame_toggle").performClick()
        verify { vm.setFullFrameBlurEnabled(true) }
        scrollTo("Privacy")
        composeRule.onNodeWithTag("privacy_toggle").performClick()
        verify { vm.setPrivacyEnabled(false) }
    }
}
