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

private const val STREAM_URL_LABEL = "Stream URL"
private const val SAVE_LABEL = "Save"
private const val MULTI_STREAM_LABEL = "Multi-streaming (optional)"
private const val SECOND_STREAM_TOGGLE_TAG = "stream_toggle_b"
private const val BACK_LABEL = "Back"
private const val VIDEO_LABEL = "Video"
private const val OBS_HOST_LABEL = "OBS host"
private const val OBS_CONTROL_LABEL = "OBS remote control"
private const val REPLAY_LABEL = "Replay recording"
private const val TEST_OBS_HOST = "192.0.2.11"

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
        every { vm.editStream(any()) } answers { draft.value = settings.value.streamProfiles.first { it.id == firstArg<String>() } }
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
            val saved = requireNotNull(draft.value)
            val latest = settings.value.streamProfiles
            settings.value = settings.value.copy(streamProfiles = if (latest.any { it.id == saved.id }) {
                latest.map { if (it.id == saved.id) saved else it }
            } else latest + saved)
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
        compose.onNodeWithText(STREAM_URL_LABEL).assertDoesNotExist()
        compose.onNodeWithText(SAVE_LABEL).assertDoesNotExist()
        compose.onNodeWithText("Create stream").performClick()
        listOf("Twitch", "YouTube", "Kick", "Custom").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("YouTube").performClick()
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled()
        compose.onNodeWithText("Stream name").performTextInput("My YouTube")
        scrollClick(SAVE_LABEL)
        compose.onNodeWithText("My YouTube").assertExists()
        compose.onNodeWithText(STREAM_URL_LABEL).assertDoesNotExist()
        verify { vm.saveStreamDraft(any()) }
    }

    @Test fun `primary URL validates while typing and blocks save until corrected`() {
        show(listOf(first))
        details()
        val field = compose.onNodeWithTag("stream_primary_url")
        field.performTextReplacement("djfj")
        compose.onNodeWithText("Unsupported protocol \"djfj\". Allowed are rtmp, rtmps and srt.").assertIsDisplayed()
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled().performClick()
        verify(exactly = 0) { vm.saveStreamDraft(any()) }
        field.performTextReplacement("rtmp:///app")
        compose.onNodeWithText("The stream URL contains no valid server host (e.g. rtmp://live.twitch.tv/app).").assertExists()
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled()
        field.performTextReplacement("rtmp://srt/app")
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled()
        // Missing separate keys remain warnings: a key can already be included in the URL.
        field.performTextReplacement("rtmps://localhost:1936/app/included-key")
        compose.onNodeWithText(SAVE_LABEL).assertIsEnabled()
        scrollClick(SAVE_LABEL)
        org.junit.Assert.assertEquals("rtmps://localhost:1936/app/included-key", settings.value.streamProfiles.single().streamUrl)
    }

    @Test fun `optional second URL rejects malformed input but can be cleared or use SRT`() {
        show(listOf(first))
        details()
        scrollClick(MULTI_STREAM_LABEL)
        val field = compose.onNodeWithTag("stream_secondary_url")
        compose.onNodeWithText(SAVE_LABEL).assertIsEnabled()
        field.performTextReplacement("https://server.example/live")
        compose.onNodeWithText("Unsupported protocol \"https\". Allowed are rtmp, rtmps and srt.").assertIsDisplayed()
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled()
        field.performTextReplacement("srt://")
        compose.onNodeWithText(SAVE_LABEL).assertIsNotEnabled()
        field.performTextReplacement("")
        compose.onNodeWithText(SAVE_LABEL).assertIsEnabled()
        field.performTextReplacement("srt://server.example:9000?streamid=live")
        compose.onNodeWithText(SAVE_LABEL).assertIsEnabled()
        scrollClick(SAVE_LABEL)
        org.junit.Assert.assertEquals("srt://server.example:9000?streamid=live", settings.value.streamProfiles.single().secondaryStreamUrl)
    }

    @Test fun `switches do not navigate and selecting another turns first off`() {
        show(listOf(first, second), "a")
        screenshot("streams")
        compose.onNodeWithTag("stream_toggle_a").assertIsOn()
        compose.onNodeWithTag(SECOND_STREAM_TOGGLE_TAG).performClick().assertIsOn()
        compose.onNodeWithTag("stream_toggle_a").assertIsOff()
        compose.onNodeWithText(STREAM_URL_LABEL).assertDoesNotExist()
        verify(exactly = 0) { vm.editStream(any()) }
        compose.onNodeWithTag(SECOND_STREAM_TOGGLE_TAG).performClick().assertIsOff()
    }

    @Test fun `row opens details without toggling and Twitch control is conditional`() {
        show(listOf(first, second), "a")
        details("b")
        compose.onNodeWithText(STREAM_URL_LABEL).assertExists()
        compose.onNodeWithText("Twitch channel control").assertDoesNotExist()
        verify(exactly = 0) { vm.setStreamEnabled(any(), any()) }
        compose.onNodeWithContentDescription(BACK_LABEL).performClick()
        details()
        scrollClick("Twitch channel control")
        compose.onNodeWithText("Stream title").assertExists()
    }

    @Test fun `all video settings share a single scrollable page`() {
        show(listOf(first))
        details()
        scrollClick(VIDEO_LABEL)
        screenshot("video")
        compose.onNodeWithText(STREAM_URL_LABEL).assertDoesNotExist()
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
        compose.onNodeWithContentDescription(BACK_LABEL).performClick()
        compose.onNodeWithText(STREAM_URL_LABEL).assertExists()
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
        compose.onNodeWithTag(SECOND_STREAM_TOGGLE_TAG).assertIsOn()
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
        compose.onNodeWithText(OBS_HOST_LABEL).assertDoesNotExist()
        scrollClick(OBS_CONTROL_LABEL)
        compose.onNodeWithText(OBS_HOST_LABEL).assertExists()
    }

    @Test fun `each source subpage saves the edited profile without another parent save`() {
        show(listOf(first), "a")
        val pages = listOf(VIDEO_LABEL, REPLAY_LABEL, MULTI_STREAM_LABEL, OBS_CONTROL_LABEL)
        pages.forEachIndexed { index, page ->
            details()
            scrollClick(page)
            when (page) {
                VIDEO_LABEL -> compose.onNodeWithText("1080p30").performClick()
                REPLAY_LABEL -> compose.onNode(isToggleable()).performClick()
                MULTI_STREAM_LABEL -> compose.onNodeWithText("Second stream URL")
                    .performTextReplacement("rtmp://second.example/live")
                OBS_CONTROL_LABEL -> compose.onNodeWithText(OBS_HOST_LABEL).performTextReplacement("192.0.2.10")
            }
            val edited = requireNotNull(draft.value)
            scrollClick(SAVE_LABEL)
            compose.onNodeWithTag("stream_row_a").assertExists()
            org.junit.Assert.assertNull(draft.value)
            org.junit.Assert.assertEquals(listOf(edited), settings.value.streamProfiles)
            org.junit.Assert.assertEquals("a", settings.value.activeStreamId)
            verify(exactly = index + 1) { vm.saveStreamDraft(any()) }
        }
        org.junit.Assert.assertEquals(EncoderPreset.FHD30, settings.value.streamProfiles.single().encoderPreset)
        org.junit.Assert.assertEquals("rtmp://second.example/live", settings.value.streamProfiles.single().secondaryStreamUrl)
        org.junit.Assert.assertEquals("192.0.2.10", settings.value.streamProfiles.single().obsHost)
    }

    @Test fun `failed subpage save keeps edits and shows the error for retry`() {
        val failure = MutableStateFlow(false)
        every { vm.streamSaveFailed } returns failure
        show(listOf(first))
        every { vm.saveStreamDraft(any()) } answers { failure.value = true }
        details()
        scrollClick(OBS_CONTROL_LABEL)
        compose.onNodeWithText(OBS_HOST_LABEL).performTextReplacement(TEST_OBS_HOST)
        scrollClick(SAVE_LABEL)
        org.junit.Assert.assertEquals(TEST_OBS_HOST, draft.value!!.obsHost)
        org.junit.Assert.assertEquals(first, settings.value.streamProfiles.single())
        compose.onNodeWithText(OBS_HOST_LABEL).assertIsDisplayed()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(com.vivid.feature.settings.R.string.stream_save_failed)).assertIsDisplayed()
        every { vm.saveStreamDraft(any()) } answers {
            settings.value = settings.value.copy(streamProfiles = listOf(requireNotNull(draft.value)))
            draft.value = null
            firstArg<() -> Unit>()()
        }
        scrollClick(SAVE_LABEL)
        compose.onNodeWithTag("stream_row_a").assertExists()
        org.junit.Assert.assertEquals(TEST_OBS_HOST, settings.value.streamProfiles.single().obsHost)
    }

    @Test fun `multi recording and OBS pages retain existing fields`() {
        show(listOf(first))
        details()
        scrollClick(MULTI_STREAM_LABEL)
        compose.onNodeWithText("Second stream URL").assertExists()
        compose.onNodeWithContentDescription(BACK_LABEL).performClick()
        scrollClick(REPLAY_LABEL)
        compose.onNodeWithText("Record audio in replays").assertExists()
        compose.onNodeWithContentDescription(BACK_LABEL).performClick()
        scrollClick(OBS_CONTROL_LABEL)
        compose.onNodeWithText(OBS_HOST_LABEL).assertExists()
    }
}
