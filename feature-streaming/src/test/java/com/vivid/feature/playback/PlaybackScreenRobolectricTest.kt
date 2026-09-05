package com.vivid.feature.playback

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für [PlaybackScreen] (leerer Zustand + Controls).
 * Mit gesetzter URL würde der ExoPlayer-Instanz-Pfad laufen — bewusst nur der
 * leere Zustand, um Media3-Initialisierung im JVM-Test zu vermeiden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `controls render in the empty state`() {
        val viewModel = mockk<PlaybackViewModel>(relaxed = true)
        every { viewModel.currentStreamUrl } returns MutableStateFlow<String?>(null)

        composeRule.setContent {
            PlaybackScreen(navController = mockk(relaxed = true), viewModel = viewModel)
        }

        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithText("Play/Pause").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun `play-pause toggles playback in the viewmodel`() {
        val viewModel = mockk<PlaybackViewModel>(relaxed = true)
        every { viewModel.currentStreamUrl } returns MutableStateFlow<String?>(null)

        composeRule.setContent {
            PlaybackScreen(navController = mockk(relaxed = true), viewModel = viewModel)
        }

        composeRule.onNodeWithText("Play/Pause").performClick()
        verify(exactly = 1) { viewModel.togglePlayback() }
    }
}
