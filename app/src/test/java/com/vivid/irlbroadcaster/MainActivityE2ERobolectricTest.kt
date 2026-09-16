package com.vivid.irlbroadcaster

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
