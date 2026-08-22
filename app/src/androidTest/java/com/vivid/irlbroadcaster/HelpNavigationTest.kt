package com.vivid.irlbroadcaster

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.R
import com.vivid.feature.settings.R as SettingsR
import com.vivid.feature.streaming.R as StreamingR
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Deckt die Einstiege in den [HelpScreen] ab:
 *
 *   helpFromStreaming          — ❓-Button im Top-Bar des Streaming-Screens
 *                                (content-desc „Open Help“) → HelpScreen → Back → Streaming
 *   helpFromAbout              — Streaming → Settings → SettingsAbout → About →
 *                                „Help & User Guide“ → HelpScreen → Back → About
 *   helpExternalLinkOpensBrowser — HelpScreen → externer Doku-Link → ACTION_VIEW-Intent
 *                                (URI-Handler) statt App-Navigation; der HelpScreen bleibt
 *                                im Vordergrund.
 *
 * Statt String-Literalen werden alle Texte über die R-Klassen referenziert
 * und per `getString()` zur Laufzeit aufgelöst.  Das Projekt nutzt
 * `android.nonTransitiveRClass=true`, daher kommen die Strings aus den
 * Modul-R-Klassen:
 *
 *   com.vivid.R                     — app-eigene Strings (Help/About)
 *   com.vivid.feature.streaming.R   — Streaming-Screen (❓, Settings, Go-Live)
 *   com.vivid.feature.settings.R    — Settings-Kategorien
 *
 * Die Tests sind damit locale-robust (erwarteter und gerenderter Wert
 * stammen aus derselben Ressource) und unabhängig vom
 * `applicationId`-Suffix (Debug-Build `com.vivid.debug`).
 */
class HelpNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.waitForIdle()
        // Fängt startActivity-Aufrufe ab, damit kein echter Browser startet.
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    /** Löst eine String-Ressource in der App-Locale auf (locale-robust). */
    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun helpFromStreaming() {
        // ❓-Button im Streaming-Screen (content-desc „Open Help“)
        composeRule
            .onNodeWithContentDescription(str(StreamingR.string.streaming_help_content_desc))
            .performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem HelpScreen
        composeRule.onNodeWithText(str(R.string.help_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.help_quick_tips_title)).assertIsDisplayed()

        // Zurück per Top-Bar-Navigation-Button (content-desc „Back“)
        composeRule.onNodeWithContentDescription(str(R.string.help_back)).performClick()
        composeRule.waitForIdle()

        // Wieder auf dem Streaming-Screen: Start-Streaming-Button
        composeRule.onNodeWithText(str(StreamingR.string.streaming_start)).assertIsDisplayed()
    }

    @Test
    fun helpFromAbout() {
        // 1. Streaming → Einstellungen (content-desc „Open Settings“)
        composeRule
            .onNodeWithContentDescription(str(StreamingR.string.streaming_settings_content_desc))
            .performClick()
        composeRule.waitForIdle()

        // 2. Settings → SettingsAbout (Kategorie „About & Updates“)
        //    Die letzte Kategorie ist oft außerhalb des Viewports — scrollen.
        composeRule
            .onNodeWithText(str(SettingsR.string.cat_about_title))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // 3. SettingsAbout → AboutScreen („About Vivid & Updates“-Button)
        composeRule.onNodeWithText(str(SettingsR.string.about_open_button)).performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem AboutScreen
        composeRule.onNodeWithText(str(R.string.about_title)).assertIsDisplayed()

        // 4. About → HelpScreen („Help & User Guide“)
        composeRule.onNodeWithText(str(R.string.about_link_help)).performClick()
        composeRule.waitForIdle()

        // Wir sind auf dem HelpScreen
        composeRule.onNodeWithText(str(R.string.help_title)).assertIsDisplayed()

        // 5. Help → About (Back)
        composeRule.onNodeWithContentDescription(str(R.string.help_back)).performClick()
        composeRule.waitForIdle()

        // Wieder auf dem AboutScreen
        composeRule.onNodeWithText(str(R.string.about_title)).assertIsDisplayed()
    }

    @Test
    fun helpExternalLinkOpensBrowser() {
        // ❓-Button im Streaming-Screen → HelpScreen
        composeRule
            .onNodeWithContentDescription(str(StreamingR.string.streaming_help_content_desc))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.help_title)).assertIsDisplayed()

        // ACTION_VIEW-Intents abfangen, damit kein echter Browser startet
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        // Externer Doku-Link „User Guide (German)“ (evtl. unterhalb des Viewports)
        composeRule
            .onNodeWithText(str(R.string.help_link_guide_de))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Der Browser-Intent wurde mit der exakten URL abgesetzt …
        intended(hasAction(Intent.ACTION_VIEW))
        intended(
            hasData("https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.md"),
        )

        // … und es fand KEINE App-Navigation statt: HelpScreen ist noch im Vordergrund.
        // Der TopAppBar-Titel ist immer sichtbar; die Quick-Tips-Card kann durch das
        // vorherige Scrollen aus dem Viewport geraten sein — daher nur Existenz prüfen.
        composeRule.onNodeWithText(str(R.string.help_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.help_quick_tips_title)).assertExists()
    }
}
