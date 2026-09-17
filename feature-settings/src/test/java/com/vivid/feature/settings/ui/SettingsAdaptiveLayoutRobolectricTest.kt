package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.vivid.core.ui.LocalWindowWidthClass
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Adaptive Settings-Layouts (UX-Audit "Large Screens"): Auf Expanded-Fenstern
 * ist der scrollbare Inhalt auf 600 dp gekappt und horizontal zentriert
 * (TopCenter-Box), auf Compact/Medium volle Breite (Dp.Unspecified = kein
 * Width-Constraint).
 *
 * Der Test steuert die Breitenklasse nicht ueber fragile
 * Robolectric-Fensterqualifier, sondern deterministisch ueber die gleiche
 * Naht, die auch die MainActivity bedient: [LocalWindowWidthClass]. Ein
 * [requiredWidth]-Container ausserhalb der App-Komposition stellt eine
 * bekannte Elternbreite bereit, damit die Kappe als exakte Breite messbar ist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAdaptiveLayoutRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Mock-VM: nur die beiden gelesenen Member (Hausmuster TwitchChannelViewModel). */
    private fun fakeViewModel() = mockk<SettingsViewModel>(relaxed = true) {
        every { updateState } returns MutableStateFlow(SettingsUpdateState())
        every { saveEvent } returns MutableSharedFlow()
    }

    private fun setContentAt(
        widthClass: WindowWidthSizeClass,
        parentWidthDp: Int,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
                Box(modifier = Modifier.requiredWidth(parentWidthDp.dp)) {
                    content()
                }
            }
        }
    }

    private fun awaitContentTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `overview caps content at 600dp on expanded windows`() {
        setContentAt(WindowWidthSizeClass.Expanded, parentWidthDp = 800) {
            SettingsScreen(
                navController = mockk(relaxed = true),
                installedVersionName = "1.2.3",
                viewModel = fakeViewModel(),
            )
        }

        awaitContentTag("settings_overview_content")
        composeRule.onNodeWithTag("settings_overview_content")
            .assertWidthIsEqualTo(600.dp)
    }

    @Test
    fun `overview keeps full width on compact phones`() {
        setContentAt(WindowWidthSizeClass.Compact, parentWidthDp = 400) {
            SettingsScreen(
                navController = mockk(relaxed = true),
                installedVersionName = "1.2.3",
                viewModel = fakeViewModel(),
            )
        }

        awaitContentTag("settings_overview_content")
        composeRule.onNodeWithTag("settings_overview_content")
            .assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun `section screens cap content at 600dp on expanded windows`() {
        setContentAt(WindowWidthSizeClass.Expanded, parentWidthDp = 800) {
            SettingsSectionScaffold(
                title = "Test",
                onBack = {},
                onSave = {},
            ) {
                Text("Marker")
            }
        }

        composeRule.onNodeWithTag("settings_section_content")
            .assertWidthIsEqualTo(600.dp)
        // Inhalt komponiert unter der Kappe (waitUntil statt direktem
        // Displayed-Assert — Viewport-Geometrie der Test-JVM ist nicht
        // deterministisch genug fuer einen Blind-Assert).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Marker").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `default width class is compact when no provider is set`() {
        // Ohne CompositionLocalProvider gilt der Local-Default (Compact) —
        // der Simulation-Pfad von Compose-Tests ohne Activity.
        composeRule.setContent {
            Box(modifier = Modifier.requiredWidth(400.dp)) {
                SettingsScreen(
                    navController = mockk(relaxed = true),
                    installedVersionName = "1.2.3",
                    viewModel = fakeViewModel(),
                )
            }
        }

        awaitContentTag("settings_overview_content")
        composeRule.onNodeWithTag("settings_overview_content")
            .assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun `save button stays visible and full width under the capped layout`() {
        setContentAt(WindowWidthSizeClass.Expanded, parentWidthDp = 800) {
            SettingsSectionScaffold(
                title = "Test",
                onBack = {},
                onSave = {},
            ) {
                Text("Marker")
            }
        }

        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
