package com.vivid.feature.widget

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vivid.core.data.PomodoroPhase
import com.vivid.core.data.PomodoroUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PomodoroWidgetRobolectricTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `widget shows countdown and forwards start skip reset`() {
        val actions = mutableListOf<String>()
        compose.setContent { PomodoroWidgetContent("Focus", PomodoroUiState(enabled = true),
            { actions += "start" }, { actions += "skip" }, { actions += "reset" }) }
        compose.onNodeWithText("Focus").assertIsDisplayed()
        compose.onNodeWithText("25:00").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithContentDescription("Skip phase").performClick()
        compose.onNodeWithContentDescription("Reset").performClick()
        assertEquals(listOf("start", "skip", "reset"), actions)
    }

    @Test fun `running break widget shows pause and completed count`() {
        compose.setContent { PomodoroWidgetContent("Focus", PomodoroUiState(enabled = true, running = true,
            phase = PomodoroPhase.BREAK, remainingSeconds = 65, completedWorkSessions = 2), {}, {}, {}) }
        compose.onNodeWithText("Break").assertIsDisplayed()
        compose.onNodeWithText("01:05").assertIsDisplayed()
        compose.onNodeWithText("Completed work sessions: 2").assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsEnabled()
    }

    @Test fun `disabled library widget cannot start`() {
        compose.setContent { PomodoroWidgetContent("Focus", PomodoroUiState(enabled = false), {}, {}, {}) }
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }
}
