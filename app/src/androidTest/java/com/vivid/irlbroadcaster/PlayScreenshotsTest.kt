package com.vivid.irlbroadcaster

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Erzeugt die zwei Play-Store-Screenshots per UI-Test (fastlane screengrab,
 * Lane `capture_play_screenshots` im Fastfile):
 *
 *   `1_live_stream` → Live-Stream-Hauptscreen (Start-Destination)
 *   `2_settings`    → Einstellungen (Top-Bar-Zahnrad, content-desc „Open Settings“)
 *
 * Die Lane übersetzt die Namen in die supply-konformen Dateinamen
 * (`1_en-US.png`, `2_en-US.png`) unter `fastlane/metadata/android/images/phoneScreenshots/`.
 *
 * Wichtig für Compose: `UiAutomatorScreenshotStrategy` ist nötig, sonst
 * liefert screengrab schwarze Screenshots (bekanntes Compose-Problem).
 */
class PlayScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // LocaleTestRule: screengrab instrumentiert pro Locale neu — die Regel
    // stellt die Gerätesprache auf das Test-Locale (en-US) und zurück.
    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    @Before
    fun setUp() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @Test
    fun captureLiveStream() {
        composeRule.waitForIdle()
        Screengrab.screenshot("1_live_stream")
    }

    @Test
    fun captureSettings() {
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Open Settings").performClick()
        composeRule.waitForIdle()
        Screengrab.screenshot("2_settings")
    }
}
