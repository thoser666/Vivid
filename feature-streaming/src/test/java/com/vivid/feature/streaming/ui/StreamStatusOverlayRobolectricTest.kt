package com.vivid.feature.streaming.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
