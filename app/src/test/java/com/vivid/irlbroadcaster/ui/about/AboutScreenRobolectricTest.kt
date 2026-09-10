package com.vivid.irlbroadcaster.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.vivid.BuildConfig
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-Compose-Tests für [AboutScreen]: Titel, Update-Check-Karte und
 * Versionszeile. Das ViewModel ist real (Konstruktor braucht nur einen
 * relaxed-mock [com.vivid.core.update.UpdateChecker]) — die Versionsangabe
 * kommt aus der BuildConfig des Moduls.
 *
 * Pinnt SDK 34 + en-Qualifier für deterministische String-Assertionen.
 * Plain-Application: Die echte VividApplication startet den Ktor-Remote-Control-
 * Server (fester Port) — unter Robolectric kollidieren die Ports über parallele
 * Test-Worker. Der Screen braucht die echte Application nicht.
 *
 * Regressionstest Crash #164 („Vertically scrollable component was measured with
 * an infinity maximum height constraints“): [AboutScreen] ist der einzige Screen
 * mit VERSCHACHTELTEN verticalScroll-Containern (äußere Column + innerer
 * Release-Notes-Bereich mit heightIn(max = 160.dp)). Der Test rendert Update-
 * verfügbar-Notes, deren Länge die 160.dp-Grenze weit überschreitet, und beweist,
 * dass kein unbegrenzter Height-Messungs-Fehler auftritt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AboutScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: AboutViewModel
    private lateinit var updateChecker: UpdateChecker

    private fun setContent() {
        viewModel = AboutViewModel(updateChecker = updateChecker)
        composeRule.setContent {
            AboutScreen(
                navController = mockk(relaxed = true),
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `about screen renders title back button and update check card`() {
        updateChecker = mockk(relaxed = true)
        setContent()

        composeRule.onNodeWithText("About Vivid").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Check for updates").assertIsDisplayed()
    }

    @Test
    fun `version line shows the installed version from buildconfig`() {
        updateChecker = mockk(relaxed = true)
        setContent()

        composeRule.onNodeWithText(
            "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `update available with very long release notes renders without nested-scroll crash`() {
        // Notes deutlich über 160.dp → der innere verticalScroll (heightIn max)
        // innerhalb der äußeren verticalScroll-Column muss den Inhalt kappen,
        // ohne gegen unbegrenzte Höhen-Messung zu laufen (Crash #164).
        updateChecker = mockk(relaxed = true)
        coEvery { updateChecker.check(any(), any()) } returns UpdateCheckResult.UpdateAvailable(
            latestVersion = "9.9.9-beta",
            releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/v9.9.9-beta",
            releaseNotes = (1..200).joinToString("\n\n") { section ->
                "## Bootstrap $section\n- bullet $section\n- **bold** $section\n- \u0060code\u0060 $section"
            },
        )
        setContent()

        composeRule.runOnIdle { viewModel.checkForUpdates() }
        composeRule.waitForIdle()

        // Versionszeile im Update-Available-Header (Substring, da der String
        // „Update available: 9.9.9-beta“ ist und der result-Text im Scroll
        // geschnitten sein KANN — das Header-Row-Fragment muss dennoch
        // gemessen/gerendert werden).
        composeRule.onNodeWithText("Update available", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("What's new").assertIsDisplayed()
    }
}
