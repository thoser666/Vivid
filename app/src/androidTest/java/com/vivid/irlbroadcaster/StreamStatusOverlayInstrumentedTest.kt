package com.vivid.irlbroadcaster

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.R as StreamingR
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real preview and stop-confirmation test; never uses saved stream destinations. */
@RunWith(AndroidJUnit4::class)
class StreamStatusOverlayInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun liveStatsAndStopConfirmationKeepTheStreamRunningUntilConfirmed() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runStreamHud") == "true")
        val url = requireNotNull(args.getString("localRtmpUrl"))
        require(url.startsWith("rtmp://127.0.0.1:"))
        lateinit var engine: StreamingEngine
        composeRule.activityRule.scenario.onActivity {
            engine = ViewModelProvider(it)[StreamingViewModel::class.java].streamingEngine
            engine.configureCameraMode(CameraMode.STANDARD)
            engine.configureEncoder(ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, false), true)
            engine.startStream(url)
        }
        try {
            composeRule.waitUntil(20_000) { engine.streamingState.value is StreamingState.Streaming }
            composeRule.waitUntil(10_000) { engine.targetStates.value.any { (it.bitrateKbps ?: 0) > 0 } }
            composeRule.waitUntil(10_000) { engine.measuredEncoderFps.value != null }
            composeRule.onNodeWithTag("stream_mode_fps").assertIsDisplayed()
            composeRule.onNodeWithTag("stream_current_bitrate").assertIsDisplayed()
            composeRule.onNodeWithTag("applied_camera_profile").assertDoesNotExist()
            Thread.sleep(2_000)
            // Compose's instrumentation clock drives effect delays independently of wall time.
            composeRule.mainClock.advanceTimeBy(2_100)
            val uptime = composeRule.onNodeWithTag("stream_uptime").fetchSemanticsNode()
                .config[SemanticsProperties.Text].single().text
            assertNotEquals("00:00:00", uptime)
            screenshot("stream-hud.png")
            click(StreamingR.string.streaming_stop)
            assertTrue(engine.streamingState.value is StreamingState.Streaming)
            composeRule.onNodeWithText(composeRule.activity.getString(StreamingR.string.streaming_stop_confirm_title))
                .assertIsDisplayed()
            screenshot("stream-stop-confirmation.png")
            click(StreamingR.string.streaming_keep_live)
            assertTrue(engine.streamingState.value is StreamingState.Streaming)
            click(StreamingR.string.streaming_stop)
            click(StreamingR.string.streaming_stop_confirm)
            composeRule.waitUntil(10_000) { engine.streamingState.value is StreamingState.Idle }
            composeRule.onNodeWithTag("stream_status_overlay").assertDoesNotExist()
        } finally {
            composeRule.activityRule.scenario.onActivity { engine.stopStream() }
        }
    }

    private fun click(resId: Int) {
        composeRule.onNodeWithText(composeRule.activity.getString(resId))
            .performSemanticsAction(SemanticsActions.OnClick) { it.invoke() }
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(composeRule.activity.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
