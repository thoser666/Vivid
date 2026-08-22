package com.vivid.irlbroadcaster

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Deckt beide Einstiege in den [HelpScreen] ab:
 *
 *   helpFromStreaming   — ❓-Button im Top-Bar des Streaming-Screens
 *                         (content-desc „Open Help") → HelpScreen → Back → Streaming
 *   helpFromAbout       — Streaming → Settings → SettingsAbout → About →
 *                         „Help & User Guide" → HelpScreen → Back → About
 *
 * Voraussetzung: Debug-Build (com.vivid.debug) auf einem Emulator/Gerät
 * mit EN-Locale.  Die Strings sind als Literale gesetzt, um den
 * `applicationId`-Suffix (`.debug`) von `getIdentifier`/`getString` zu
 * umgehen.
 */
class HelpNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForApp() {
        composeRule.waitForIdle()
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun helpFromStreaming() {
        // ❓-Button im Streaming-Screen (content-desc „Open Help")
        composeRule.onNodeWithContentDescription("Open Help").performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem HelpScreen
        composeRule.onNodeWithText("Help").assertIsDisplayed()
        composeRule.onNodeWithText("Quick Tips").assertIsDisplayed()

        // Zurück per Top-Bar-Navigation-Button (content-desc „Back")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // Wieder auf dem Streaming-Screen: Start-Streaming-Button
        composeRule.onNodeWithText("Start Streaming").assertIsDisplayed()
    }

    @Test
    fun helpFromAbout() {
        // 1. Streaming → Einstellungen (content-desc „Open Settings")
        composeRule.onNodeWithContentDescription("Open Settings").performClick()
        composeRule.waitForIdle()

        // 2. Settings → SettingsAbout (Kategorie „About & Updates")
        //    Die letzte Kategorie ist oft außerhalb des Viewports — scrollen.
        composeRule
            .onNodeWithText("About & Updates")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // 3. SettingsAbout → AboutScreen („About Vivid & Updates"-Button)
        composeRule.onNodeWithText("About Vivid & Updates").performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem AboutScreen
        composeRule.onNodeWithText("About Vivid").assertIsDisplayed()

        // 4. About → HelpScreen („Help & User Guide")
        composeRule.onNodeWithText("Help & User Guide").performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem HelpScreen
        composeRule.onNodeWithText("Help").assertIsDisplayed()

        // 5. Help → About (Back)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // Wieder auf dem AboutScreen
        composeRule.onNodeWithText("About Vivid").assertIsDisplayed()
    }
}