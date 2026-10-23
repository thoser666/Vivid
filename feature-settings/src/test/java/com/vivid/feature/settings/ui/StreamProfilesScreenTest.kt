package com.vivid.feature.settings.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.*
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamProfilesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val first = StreamProfile("a", "My Twitch", StreamPlatformType.Twitch,
        streamUrl = "rtmp://live.twitch.tv/app", encoderPreset = EncoderPreset.S_4K60)
    private val second = StreamProfile("b", "My Custom", streamUrl = "rtmp://other.example/live")
    private val settings = MutableStateFlow(AppSettings())
    private val draft = MutableStateFlow<StreamProfile?>(null)
    private val vm = mockk<SettingsViewModel>(relaxed = true) {
        every { streamDraft } returns draft
        every { streamSaving } returns MutableStateFlow(false)
        every { streamSaveFailed } returns MutableStateFlow(false)
    }
    private val twitch = mockk<TwitchChannelViewModel>(relaxed = true)

    private fun show(profiles: List<StreamProfile> = emptyList(), active: String? = null) {
        settings.value = AppSettings(streamProfiles = profiles, activeStreamId = active)
        every { vm.editStream(any()) } answers { draft.value = profiles.first { it.id == firstArg<String>() } }
        every { vm.createStream(any()) } answers {
            val platform = firstArg<StreamPlatform>()
            draft.value = StreamProfile("new", "", StreamPlatformType.valueOf(platform.name),
                streamUrl = platform.ingestUrl, streamUseTls = platform != StreamPlatform.Custom)
        }
        every { vm.updateStreamDraft(any()) } answers {
            draft.value = firstArg<(StreamProfile) -> StreamProfile>()(draft.value!!)
        }
        every { vm.discardStreamDraft() } answers { draft.value = null }
        every { vm.setStreamEnabled(any(), any()) } answers {
            settings.value = settings.value.copy(activeStreamId = if (secondArg<Boolean>()) firstArg() else null)
        }
        every { vm.reorderStreams(any()) } answers {
            val byId = settings.value.streamProfiles.associateBy { it.id }
            settings.value = settings.value.copy(streamProfiles = firstArg<List<String>>().map { byId.getValue(it) })
        }
        every { vm.saveStreamDraft(any()) } answers {
            settings.value = settings.value.copy(streamProfiles = profiles + draft.value!!)
            draft.value = null
            firstArg<() -> Unit>()()
        }
        compose.setContent {
            SettingsStreamingObsScreen(settings.collectAsState().value, vm, twitch, TwitchChannelUiState()) {}
        }
    }

    private fun screenshot(name: String) {
        val file = java.io.File("build/reports/stream-menu/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scrollClick(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    private fun details(id: String = "a") = compose.onNodeWithTag("stream_row_$id").performClick()

    @Test fun `empty list starts at create then type then form and save returns to list`() {
        show()
        compose.onNodeWithText("Stream URL").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Create stream").performClick()
        listOf("Twitch", "YouTube", "Kick", "Custom").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("YouTube").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Stream name").performTextInput("My YouTube")
        scrollClick("Save")
        compose.onNodeWithText("My YouTube").assertExists()
        compose.onNodeWithText("Stream URL").assertDoesNotExist()
        verify { vm.saveStreamDraft(any()) }
    }

    @Test fun `switches do not navigate and selecting another turns first off`() {
        show(listOf(first, second), "a")
        screenshot("streams")
        compose.onNodeWithTag("stream_toggle_a").assertIsOn()
        compose.onNodeWithTag("stream_toggle_b").performClick().assertIsOn()
        compose.onNodeWithTag("stream_toggle_a").assertIsOff()
        compose.onNodeWithText("Stream URL").assertDoesNotExist()
        verify(exactly = 0) { vm.editStream(any()) }
        compose.onNodeWithTag("stream_toggle_b").performClick().assertIsOff()
    }

    @Test fun `row opens details without toggling and Twitch control is conditional`() {
        show(listOf(first, second), "a")
        details("b")
        compose.onNodeWithText("Stream URL").assertExists()
        compose.onNodeWithText("Twitch channel control").assertDoesNotExist()
        verify(exactly = 0) { vm.setStreamEnabled(any(), any()) }
        compose.onNodeWithContentDescription("Back").performClick()
        details()
        scrollClick("Twitch channel control")
        compose.onNodeWithText("Stream title").assertExists()
    }

    @Test fun `all video settings share a single scrollable page`() {
        show(listOf(first))
        details()
        scrollClick("Video")
        screenshot("video")
        compose.onNodeWithText("Stream URL").assertDoesNotExist()
        compose.onNode(hasText("2160p60").and(hasStateDescription("Selected"))).assertExists()
        compose.onNode(hasText("H.265 (HEVC)").and(hasStateDescription("Not selected"))).assertExists()
        compose.onNodeWithText("1080p30").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("60 FPS"))
        compose.onNodeWithText("60 FPS").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Low-light boost (LLB)"))
        compose.onNodeWithText("Low-light boost (LLB)").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Bitrate"))
        compose.onNodeWithContentDescription("Bitrate").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(15f) }
        compose.onNodeWithText("15 Mbps").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Keyframe interval"))
        compose.onNodeWithContentDescription("Keyframe interval").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithText("4 s").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Stream URL").assertExists()
        org.junit.Assert.assertEquals(60, draft.value!!.videoFps)
        org.junit.Assert.assertEquals(15_000, draft.value!!.videoBitrateKbps)
        org.junit.Assert.assertEquals(4, draft.value!!.keyframeIntervalSeconds)
    }

    @Test fun `dragging reorders streams without toggling or opening them`() {
        val third = second.copy(id = "c", name = "Third")
        show(listOf(first, second, third), "b")
        val start = compose.onNodeWithTag("stream_drag_a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithTag("stream_drag_c", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("stream_drag_a", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, end.y - start.y + 8f), delayMillis = 300)
            up()
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(listOf("b", "c", "a"), settings.value.streamProfiles.map { it.id })
        compose.onNodeWithTag("stream_toggle_b").assertIsOn()
        verify(exactly = 0) { vm.editStream(any()) }
        verify(exactly = 0) { vm.setStreamEnabled(any(), any()) }
        verify { vm.reorderStreams(listOf("b", "c", "a")) }
    }

    @Test fun `menu has separators and OBS switch is independent of navigation`() {
        show(listOf(first), "a")
        compose.onNodeWithTag("stream_switch_divider_a", useUnmergedTree = true).assertExists()
        details()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("obs_profile_toggle"))
        screenshot("stream-details")
        compose.onNodeWithTag("stream_settings_menu").assertExists()
        compose.onNodeWithTag("obs_switch_divider", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("obs_profile_toggle").performClick().assertIsOn()
        compose.onNodeWithText("OBS host").assertDoesNotExist()
        scrollClick("OBS remote control")
        compose.onNodeWithText("OBS host").assertExists()
    }

    @Test fun `multi recording and OBS pages retain existing fields`() {
        show(listOf(first))
        details()
        scrollClick("Multi-streaming (optional)")
        compose.onNodeWithText("Second stream URL").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        scrollClick("Replay recording")
        compose.onNodeWithText("Record audio in replays").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        scrollClick("OBS remote control")
        compose.onNodeWithText("OBS host").assertExists()
    }
}
