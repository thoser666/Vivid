package com.vivid.irlbroadcaster

import android.view.WindowManager
import android.content.pm.ActivityInfo
import com.vivid.core.data.AppOrientation
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import kotlinx.coroutines.runBlocking
import androidx.core.view.WindowCompat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LANDSCAPE_OPTION_TAG = "orientation_LANDSCAPE"

/**
 * Edge-to-Edge-Tests für die echte [MainActivity] (PARITY
 * „Edge-to-Edge/UX-Modernisierung“).
 *
 * Hintergrund: targetSdk 37 — ab SDK 35 erzwingt Android Edge-to-Edge; die App
 * zeichnet hinter Status-/Navigationsleiste und steuert die Icon-Farben selbst.
 * Geprüft wird über echte, nicht-binder Observable:
 *
 *  1. `enableEdgeToEdge()` ist aktiv: `layoutInDisplayCutoutMode` = ALWAYS
 *     (so setzt `EdgeToEdgeApi30` den Modus — direkt am Window ablesbar).
 *  2. Der Icon-Sync aus `VividTheme` greift: Beim Robolectric-Default (helles
 *     System-Design → Settings-Default SYSTEM → helles App-Design) fordert die
 *     App dunkle Status-Bar-Icons an (`isAppearanceLightStatusBars = true`,
 *     gesetzt vom SideEffect der Theme-Komposition).
 *
 * Die Activity startet mit echtem Hilt-Graph unter [HiltTestApplication] —
 * der Test ist zugleich der erste Smoke-Test des echten Activity-Starts
 * (inkl. StreamingScreen-Route) unter Robolectric. Bewusst NICHT
 * `VividApplication`: Die echte Application startet den Ktor-Remote-Control-
 * Server (Port-Bind!) und Sentry — beides hat im JVM-Test nichts zu suchen
 * (Hausmuster der Screen-Tests, siehe AboutScreenRobolectricTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@HiltAndroidTest
class MainActivityE2ERobolectricTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `first launch requests capture permissions once and recreation does not repeat it`() {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val request = org.robolectric.Shadows.shadowOf(activity).lastRequestedPermission
        org.junit.Assert.assertNotNull(request)
        assertEquals(setOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS), request!!.requestedPermissions.toSet())
        assertTrue(activity.getSharedPreferences("capture_permissions", android.content.Context.MODE_PRIVATE)
            .getBoolean("initial_request_complete", false))
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        org.junit.Assert.assertNull(org.robolectric.Shadows.shadowOf(composeRule.activity).lastRequestedPermission)
    }

    @Test
    fun `operating modes lock only the streaming screen orientation`() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
            activity.applyAppOrientation(AppOrientation.LANDSCAPE, streamingScreen = true)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.requestedOrientation)
            activity.applyAppOrientation(AppOrientation.PORTRAIT, streamingScreen = true)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
        }
    }

    @Test
    fun `menus follow sensors regardless of saved operating mode`() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            AppOrientation.entries.forEach { mode ->
                activity.applyAppOrientation(mode, streamingScreen = false)
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
            }
            activity.applyAppOrientation(null, streamingScreen = false)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
            activity.applyAppOrientation(null, streamingScreen = true)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
        }
    }

    @Test
    fun `settings and restored submenus follow sensors then return to saved stream mode`() {
        try {
            composeRule.onNodeWithTag("open_settings").performClick()
            composeRule.waitForIdle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, composeRule.activity.requestedOrientation)
            composeRule.onNodeWithText(composeRule.activity.getString(
                com.vivid.feature.settings.R.string.cat_appearance_title)).performScrollTo().performClick()
            composeRule.onNodeWithTag(LANDSCAPE_OPTION_TAG).performClick()
            awaitLandscapeSelection()
            composeRule.onNodeWithTag(LANDSCAPE_OPTION_TAG).assertIsSelected()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, composeRule.activity.requestedOrientation)

            // Rotation recreates the Activity: restored navigation must keep the menu policy.
            composeRule.activityRule.scenario.recreate()
            awaitLandscapeSelection()
            composeRule.onNodeWithTag(LANDSCAPE_OPTION_TAG).assertIsSelected()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, composeRule.activity.requestedOrientation)
            composeRule.onNodeWithContentDescription(composeRule.activity.getString(
                com.vivid.feature.settings.R.string.settings_back)).performClick()
            composeRule.waitForIdle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, composeRule.activity.requestedOrientation)
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, composeRule.activity.requestedOrientation)
        } finally {
            runBlocking { composeRule.activity.settingsRepository.updateAppOrientation(AppOrientation.PORTRAIT) }
            composeRule.waitForIdle()
        }
    }

    private fun awaitLandscapeSelection() {
        // DataStore writes and restoration are asynchronous beyond Compose idleness.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasTestTag(LANDSCAPE_OPTION_TAG) and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `edge-to-edge is enabled and the app draws behind system bars`() {
        composeRule.waitForIdle()

        val window = composeRule.activity.window
        assertEquals(
            "enableEdgeToEdge() muss den Cutout-Modus auf ALWAYS stellen (App zeichnet hinter System-Bars)",
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            window.attributes.layoutInDisplayCutoutMode,
        )
    }

    @Test
    fun `theme syncs status bar icon appearance with light design mode`() {
        composeRule.waitForIdle()

        // Robolectric-Default = helles System-Design → VividTheme (SYSTEM → hell)
        // muss dunkle Status-Bar-Icons anfordern (isAppearanceLight = true).
        val controller = WindowCompat.getInsetsController(
            composeRule.activity.window,
            composeRule.activity.window.decorView,
        )
        assertTrue(
            "Bei hellem Design müssen dunkle Status-Bar-Icons angefordert werden (isAppearanceLightStatusBars)",
            controller.isAppearanceLightStatusBars,
        )
    }
}
