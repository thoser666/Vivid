package com.vivid.irlbroadcaster.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.vivid.BuildConfig
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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AboutScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent() {
        composeRule.setContent {
            AboutScreen(
                navController = mockk(relaxed = true),
                viewModel = AboutViewModel(updateChecker = mockk(relaxed = true)),
            )
        }
    }

    @Test
    fun `about screen renders title back button and update check card`() {
        setContent()

        composeRule.onNodeWithText("About Vivid").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Check for updates").assertIsDisplayed()
    }

    @Test
    fun `version line shows the installed version from buildconfig`() {
        setContent()

        composeRule.onNodeWithText(
            "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            substring = true,
        ).assertIsDisplayed()
    }
}
