package com.vivid.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ThemeMode
import com.vivid.feature.chat.bot.ChatBotUsage
import com.vivid.core.data.ChatBotMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für die Settings-Haupt-Screens (Hub, About,
 * Appearance, Overlays, ChatBot).
 *
 * Alle Screens nehmen ihre ViewModels als Parameter (hiltViewModel nur als
 * Default) — Tests arbeiten mit MockK-Mocks ohne Hilt-Container. Pinnt SDK 34
 * + en-Qualifier, damit String-Assertionen deterministisch sind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreensRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun settingsViewModel(
        updateState: SettingsUpdateState = SettingsUpdateState(),
        llmSource: OwnerLlmSource = OwnerLlmSource.DETERMINISTIC,
    ): SettingsViewModel =
        mockk(relaxed = true) {
            every { this@mockk.updateState } returns MutableStateFlow(updateState)
            every { saveEvent } returns MutableSharedFlow()
            // Robolectric-Compose: der Screen leitet daraus stringResource(...)-IDs
            // ab — ein ungestubbter relaxed-Mock-Wert liefert sonst Ressourcen-ID 0.
            // (Parameter heißt bewusst llmSource: der Name ownerLlmSource würde
            // im every-Block die Mock-Property verdecken → "Missing mocked calls".)
            every { ownerLlmSource } returns llmSource
        }

    // --- Settings-Hub -------------------------------------------------------

    @Test
    fun `hub shows title, version and category cards`() {
        composeRule.setContent {
            SettingsScreen(
                navController = mockk(relaxed = true),
                installedVersionName = "1.2.3",
                viewModel = settingsViewModel(),
            )
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        // Version sitzt unter den Kategorie-Karten (below the fold) — Existenz prüfen.
        composeRule.onAllNodesWithText("Version 1.2.3")[0].assertExists()
        composeRule.onAllNodesWithText("Appearance")[0].assertExists()
        composeRule.onAllNodesWithText("Logs & Diagnostics")[0].assertExists()
    }

    @Test
    fun `hub category card navigates to its route`() {
        val navController = mockk<NavHostController>(relaxed = true)
        composeRule.setContent {
            SettingsScreen(
                navController = navController,
                installedVersionName = "1.2.3",
                viewModel = settingsViewModel(),
            )
        }
        composeRule.onNodeWithText("Appearance").performClick()
        verify { navController.navigate(any<String>()) }
    }

    @Test
    fun `hub triggers update check once with installed version`() {
        val viewModel = settingsViewModel()
        composeRule.setContent {
            SettingsScreen(
                navController = mockk(relaxed = true),
                installedVersionName = "9.9.9",
                viewModel = viewModel,
            )
        }
        verify(exactly = 1) { viewModel.checkForUpdates("9.9.9") }
    }

    // --- About & Updates ----------------------------------------------------

    @Test
    fun `about shows version and opens the about detail via callback`() {
        var opened = false
        composeRule.setContent {
            SettingsAboutScreen(
                installedVersionName = "1.2.3",
                updateState = SettingsUpdateState(),
                onOpenAbout = { opened = true },
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        composeRule.onNodeWithText("About Vivid & Updates").performClick()
        org.junit.Assert.assertTrue(opened)
    }

    // --- Appearance ---------------------------------------------------------

    @Test
    fun `appearance renders theme options and forwards mode change`() {
        val viewModel = settingsViewModel()
        composeRule.setContent {
            SettingsAppearanceScreen(
                uiState = AppSettings(),
                viewModel = viewModel,
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").performClick()
        verify { viewModel.onThemeModeChange(ThemeMode.DARK) }
    }

    // --- Overlays & Widgets -------------------------------------------------

    @Test
    fun `overlays renders chat and widget sections`() {
        composeRule.setContent {
            SettingsOverlaysScreen(
                uiState = AppSettings(),
                viewModel = settingsViewModel(),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Overlays & Widgets").assertIsDisplayed()
        composeRule.onNodeWithText("Chat overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Text/info widget").assertIsDisplayed()
    }

    // --- Chat-Bot & KI ------------------------------------------------------

    @Test
    fun `chatbot renders live usage with budget and top viewers`() {
        composeRule.setContent {
            SettingsChatBotScreen(
                uiState = AppSettings(),
                viewModel = settingsViewModel(),
                botUsage = ChatBotUsage(
                    repliesThisHour = 3,
                    hourlyBudget = 10,
                    totalRepliesThisStream = 42,
                    topViewers = listOf(ChatBotUsage.ViewerUsage("Alice", 2)),
                ),
                onBack = {},
            )
        }
        // Usage-Sektion liegt weit unten auf dem langen Screen (below the fold) —
        // Existenzprüfung; Werte sind in Ressourcen-Strings EINGEBETTET
        // ("Replies per hour: 3 / 10") → Teilstring-Match.
        composeRule.onAllNodesWithText("Live usage")[0].assertExists()
        composeRule.onAllNodesWithText("3 / 10", substring = true)[0].assertExists()
        composeRule.onAllNodesWithText("Replies this stream: 42", substring = true)[0].assertExists()
        composeRule.onAllNodesWithText("Alice (2)", substring = true)[0].assertExists()
    }
}
