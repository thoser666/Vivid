package com.vivid.irlbroadcaster.ui.help

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.vivid.feature.chat.bot.BotCommandsCatalog
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für [HelpScreen]: Titel, Quick-Tips und
 * Bot-Command-Referenz (kein ViewModel nötig — rein statischer Screen).
 *
 * Die Sektionen liegen in einem normalen verticalScroll-Column — alle Kinder
 * sind komponiert, daher genügt [assertExists] für Inhalte unterhalb des Folds
 * ([assertIsDisplayed] nur für Above-the-fold-Inhalte).
 *
 * Pinnt SDK 34 + en-Qualifier für deterministische String-Assertionen.
 * Plain-Application: Die echte VividApplication startet den Ktor-Remote-Control-
 * Server (fester Port) — unter Robolectric kollidieren die Ports über parallele
 * Test-Worker. Die Screens brauchen die echte Application nicht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class HelpScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `help screen renders title back button and first section`() {
        composeRule.setContent {
            HelpScreen(navController = mockk(relaxed = true))
        }

        composeRule.onNodeWithText("Help").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Tips").assertIsDisplayed()
    }

    @Test
    fun `bot command quick reference lists core commands`() {
        composeRule.setContent {
            HelpScreen(navController = mockk(relaxed = true))
        }

        // Unterhalb des Folds, aber in der Semantics-Baum-Struktur vorhanden.
        composeRule.onNodeWithText("Bot Commands (Quick Reference)").assertExists()
        composeRule.onNodeWithText("!help / !commands").assertExists()
        composeRule.onNodeWithText("!uptime").assertExists()
        composeRule.onNodeWithText("!start / !go-live").assertExists()
    }

    @Test
    fun `every catalog command row is rendered`() {
        composeRule.setContent {
            HelpScreen(navController = mockk(relaxed = true))
        }

        // Katalog ↔ UI: Jeder Katalog-Eintrag erscheint mit seiner Display-Form.
        for (entry in BotCommandsCatalog.all) {
            composeRule.onNodeWithText(entry.display).assertExists()
        }
    }

    @Test
    fun `media and owner commands previously missing from help are present`() {
        composeRule.setContent {
            HelpScreen(navController = mockk(relaxed = true))
        }

        // Regression: Diese Befehle fehlten vor der Katalog-Umstellung in der
        // In-App-Hilfe komplett (Hardcode-Liste war nie aktualisiert worden).
        composeRule.onNodeWithText("!battery").assertExists()
        composeRule.onNodeWithText("!lut [warm|cool|none]").assertExists()
        composeRule.onNodeWithText("!poll Frage | Option A | Option B").assertExists()
        composeRule.onNodeWithText("!prev / !previous").assertExists()
        composeRule.onNodeWithText("!play").assertExists()
    }

    @Test
    fun `description map covers every catalog primary`() {
        // Katalog ↔ Strings: Kein Katalog-Eintrag ohne lokalisierte Beschreibung.
        val missing = BotCommandsCatalog.all.map { it.primary } - botCommandDescriptions.keys
        assertTrue(
            "Beschreibung fehlt für: $missing (help_cmd_* anlegen)",
            missing.isEmpty(),
        )
        assertEqualsCatalogSize()
    }

    /** Keine toten Beschreibungs-Einträge: Map nicht größer als Katalog. */
    private fun assertEqualsCatalogSize() {
        val catalogPrimaries = BotCommandsCatalog.all.map { it.primary }.toSet()
        val stale = botCommandDescriptions.keys - catalogPrimaries
        assertTrue(
            "Beschreibungs-Einträge ohne Katalog-Eintrag: $stale",
            stale.isEmpty(),
        )
    }
}
