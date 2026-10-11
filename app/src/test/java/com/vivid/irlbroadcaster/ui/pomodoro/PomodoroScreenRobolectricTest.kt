package com.vivid.irlbroadcaster.ui.pomodoro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-Compose-Tests für [PomodoroScreen]: Phasen-Label, MM:SS-Countdown,
 * Session-Zähler, Aktions-Buttons und der deaktivierte Hinweis. Pinnt SDK 34 +
 * en-Qualifier für deterministische String-Assertionen; Plain-Application, da
 * der Screen die echte [com.vivid.irlbroadcaster.VividApplication] nicht braucht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class PomodoroScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `enabled work phase renders countdown and forwards buttons`() {
        var toggled = false
        var skipped = false
        var reset = false
        composeRule.setContent {
            PomodoroScreen(
                uiState = PomodoroUiState(
                    enabled = true,
                    phase = PomodoroPhase.WORK,
                    remainingSeconds = 65,
                    completedWorkSessions = 2,
                ),
                phaseComplete = emptyFlow(),
                onToggleRunning = { toggled = true },
                onSkip = { skipped = true },
                onReset = { reset = true },
                onOpenSettings = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Pomodoro timer").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Work session").assertIsDisplayed()
        composeRule.onNodeWithText("01:05").assertIsDisplayed()
        composeRule.onNodeWithText("Completed work sessions: 2").assertIsDisplayed()

        composeRule.onNodeWithText("Start").performClick()
        assertTrue(toggled)
        composeRule.onNodeWithText("Skip phase").performClick()
        assertTrue(skipped)
        composeRule.onNodeWithText("Reset").performClick()
        assertTrue(reset)
    }

    @Test
    fun `running timer shows the pause action`() {
        composeRule.setContent {
            PomodoroScreen(
                uiState = PomodoroUiState(enabled = true, running = true, remainingSeconds = 30),
                phaseComplete = emptyFlow(),
                onToggleRunning = {},
                onSkip = {},
                onReset = {},
                onOpenSettings = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun `disabled timer shows the hint and opens settings`() {
        var opened = false
        composeRule.setContent {
            PomodoroScreen(
                uiState = PomodoroUiState(enabled = false),
                phaseComplete = emptyFlow(),
                onToggleRunning = {},
                onSkip = {},
                onReset = {},
                onOpenSettings = { opened = true },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("The Pomodoro timer is disabled in settings.").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performClick()
        assertTrue(opened)
    }
}
