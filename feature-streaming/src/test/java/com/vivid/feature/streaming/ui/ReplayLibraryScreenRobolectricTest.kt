package com.vivid.feature.streaming.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vivid.feature.streaming.ReplayItem
import com.vivid.feature.streaming.ReplayLibraryUiState
import com.vivid.feature.streaming.ReplayLibraryViewModel
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
import java.io.File

/**
 * Robolectric-Compose-Tests für [ReplayLibraryScreen]: Leerliste, Karten-Rendering,
 * Interaktionen (Abspielen/Löschen) und der Lösch-Bestätigungsdialog.
 *
 * Pinnt SDK 34 + en-Qualifier für deterministische String-Assertionen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplayLibraryScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: ReplayLibraryViewModel
    private val uiState = MutableStateFlow(ReplayLibraryUiState())

    private fun item(name: String, sizeBytes: Long = 1024L): ReplayItem {
        val file = File.createTempFile(name, ".mp4")
        file.writeText("x")
        return ReplayItem(file = file, name = name, sizeBytes = sizeBytes, lastModified = 1_700_000_000_000L)
    }

    private fun setContent() {
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns uiState
        composeRule.setContent {
            ReplayLibraryScreen(navController = mockk(relaxed = true), viewModel = viewModel)
        }
    }

    @Test
    fun `empty library shows empty hint and no delete-all`() {
        setContent()

        composeRule.onNodeWithText("Replays").assertIsDisplayed()
        composeRule.onNodeWithText("No replays yet", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete all").assertDoesNotExist()
    }

    @Test
    fun `items render name with play share and delete actions`() {
        uiState.value = ReplayLibraryUiState(items = listOf(item("clip-one")))
        setContent()

        composeRule.onNodeWithText("clip-one").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete all").assertIsDisplayed()
    }

    @Test
    fun `play button opens the replay in the viewmodel`() {
        val replay = item("clip-two")
        uiState.value = ReplayLibraryUiState(items = listOf(replay))
        setContent()

        composeRule.onNodeWithText("Play").performClick()
        verify(exactly = 1) { viewModel.open(replay) }
    }

    @Test
    fun `delete button requests deletion in the viewmodel`() {
        val replay = item("clip-three")
        uiState.value = ReplayLibraryUiState(items = listOf(replay))
        setContent()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        verify(exactly = 1) { viewModel.requestDelete(replay) }
    }

    @Test
    fun `delete candidate shows confirmation dialog with confirm action`() {
        val replay = item("clip-four")
        uiState.value = ReplayLibraryUiState(
            items = listOf(replay),
            deleteCandidate = replay,
        )
        setContent()

        composeRule.onNodeWithText("Delete replay?").assertIsDisplayed()
        // Bestätigen-Button (Text) — Karten-Aktionen sind ContentDescriptions.
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `refresh is triggered on composition`() {
        setContent()

        verify(exactly = 1) { viewModel.refresh() }
    }
}
