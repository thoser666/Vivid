package com.vivid.feature.settings.ui

import androidx.compose.ui.graphics.asAndroidBitmap
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

    @Test fun `video opens separate encoder page and wraps selected codec and preset chips`() {
        show(listOf(first))
        details()
        scrollClick("Video")
        screenshot("video")
        compose.onNodeWithText("Stream URL").assertDoesNotExist()
        scrollClick("Encoder (resolution & codec)")
        screenshot("encoder")
        compose.onNode(hasText("H.265 (HEVC)").and(hasStateDescription("Not selected"))).assertExists()
        compose.onNode(hasStateDescription("On")).assertExists()
        compose.onNode(hasText("2160p60").and(hasStateDescription("Selected"))).assertExists()
        compose.onNodeWithText("1080p30").performClick()
        verify { vm.updateStreamDraft(any()) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("H.265 (HEVC)"))
        compose.onNodeWithText("H.265 (HEVC)").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Automatically adapt bitrate to the connection"))
        compose.onNodeWithText("Automatically adapt bitrate to the connection").assertExists()
    }

    @Test fun `nested pages keep draft and return through video to details`() {
        show(listOf(first))
        details()
        scrollClick("Video")
        scrollClick("Bitrate")
        compose.onNodeWithText("6 Mbps").assertExists()
        compose.onNodeWithContentDescription("Bitrate").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(15f) }
        compose.onNodeWithText("15 Mbps").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Frame rate").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Stream URL").assertExists()
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
