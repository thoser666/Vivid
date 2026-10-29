package com.vivid.feature.streaming.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamStatusOverlayRobolectricTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `hidden duration resumes from session time and fps visibility is independent`() {
        val showDuration = mutableStateOf(false)
        val showFps = mutableStateOf(true)
        var now = 10_000L
        composeRule.setContent { MaterialTheme {
            StreamStatusOverlay(null, null, 1_000, nowMs = { now }, showFps = showFps.value, showDuration = showDuration.value)
        } }
        composeRule.onNodeWithText("— FPS").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_uptime").assertDoesNotExist()
        composeRule.runOnIdle { now = 60_000; showDuration.value = true; showFps.value = false }
        composeRule.onNodeWithTag("stream_mode_fps").assertDoesNotExist()
        composeRule.onNodeWithText("00:00:59").assertIsDisplayed()
    }

    @Test fun `bitrate toggle hides only bitrate and restores its latest measurement`() {
        val showBitrate = mutableStateOf(true)
        val bitrate = mutableStateOf<Int?>(800)
        composeRule.setContent { MaterialTheme {
            StreamStatusOverlay(30, bitrate.value, 1_000, nowMs = { 10_000 }, showBitrate = showBitrate.value)
        } }
        composeRule.onNodeWithText("800 kbps").assertIsDisplayed()
        composeRule.runOnIdle { showBitrate.value = false; bitrate.value = 900 }
        composeRule.onNodeWithTag("stream_current_bitrate").assertDoesNotExist()
        composeRule.onNodeWithText("30 FPS").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:09").assertIsDisplayed()
        composeRule.runOnIdle { showBitrate.value = true }
        composeRule.onNodeWithText("900 kbps").assertIsDisplayed()
    }

    @Test fun `hiding duration cancels its ticker and showing it uses current session time`() {
        val visible = mutableStateOf(true)
        var reads = 0
        var now = 10_000L
        composeRule.setContent { MaterialTheme {
            StreamStatusOverlay(30, null, 1_000, nowMs = { reads++; now }, showDuration = visible.value)
        } }
        composeRule.onNodeWithText("00:00:09").assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.onNodeWithTag("stream_uptime").assertDoesNotExist()
        val readsWhenHidden = reads
        composeRule.mainClock.advanceTimeBy(2_100)
        composeRule.runOnIdle { org.junit.Assert.assertEquals(readsWhenHidden, reads) }
        composeRule.runOnIdle { now = 60_000; visible.value = true }
        composeRule.onNodeWithText("00:00:59").assertIsDisplayed()
    }

    @Test
    fun `returning to an existing session shows elapsed hours and keeps ticking`() {
        var now = 3_662_000L
        composeRule.setContent {
            MaterialTheme {
                StreamStatusOverlay(30, null, 1_000, nowMs = { now })
            }
        }
        composeRule.onNodeWithText("01:01:01").assertIsDisplayed()
        composeRule.onNodeWithText("— kbps").assertIsDisplayed()
        composeRule.runOnIdle { now += 1_000 }
        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.onNodeWithText("01:01:02").assertIsDisplayed()
    }
}
