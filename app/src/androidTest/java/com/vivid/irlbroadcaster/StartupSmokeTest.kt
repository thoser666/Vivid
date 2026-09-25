package com.vivid.irlbroadcaster

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivid.feature.streaming.R as StreamingR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Startup-Smoke-Test: „Startet die App beim Kaltstart und erreicht die
 * Haupt-UI?".
 *
 * Ziel: Jede Release-Pipeline (standard UND foss) muss diesen Test auf einem
 * Emulator bestehen, BEVOR ein Build ausgeliefert wird. Er bewacht damit exakt
 * die Crash-Klasse, die auf dem S23 (Android 14 / API 34) nach Einfuehrung
 * der M3 Window Size Classes (`de4b9131`, `androidx.window` Vendor-Aufloesung
 * von `androidx.window.extensions.*` / `androidx.window.sidecar.*`) beim
 * Layout von `MainActivity` auftrat: `NoClassDefFoundError` → sofortiger
 * Kaltstart-Crash, keine UI, kein Stack im FOSS-Build (kein Sentry).
 *
 * Der Test ist bewusst minimal und bewusst OHNE zusätzliche Interaktion:
 * * Activity starten (Kaltstart via `createAndroidComposeRule<MainActivity>`),
 * * innerhalb eines festen Timeouts warten, bis der Streaming-Screen
 *   (Start-Destination) gerendert ist,
 * * andernfalls Semantics-Tree loggen und testen.
 *
 * Ein Timeout statt `waitForIdle()` deckt zusätzlich „stehende/hängende"
 * Starts ab (UI kommt nie in den Idle-Zustand).
 */
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun str(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun coldStart_reachesMainUiWithinTimeout() {
        // Warte hart auf den Start-Button des Streaming-Screens (Start-Destination).
        val startButton = composeRule.onNodeWithText(str(StreamingR.string.streaming_start))
        composeRule.waitUntil(timeoutMillis = START_TIMEOUT_MS) {
            isDisplayed(startButton)
        }

        // Explizite Assertion, damit der Test auch aussagekräftig scheitert.
        startButton.assertIsDisplayed()
    }

    @Test
    fun coldStart_semanticsTreeIsNotEmpty() {
        composeRule.waitUntil(timeoutMillis = START_TIMEOUT_MS) {
            isDisplayed(composeRule.onNodeWithText(str(StreamingR.string.streaming_start)))
        }
        // Protokolliert den kompletten Baum bei Diagnose in CI-Logs.
        composeRule.onRoot().printToLog("VIVID_STARTUP_SMOKE")
    }

    /**
     * Poll-Helfer für [waitUntil]: `isDisplayed()` wirft bei fehlendem/anders
     * sichtbaren Node eine `AssertionError` (Node noch nicht in der
     * Semantics). runCatching + getOrDefault(false) lässt den Compiler hier
     * nicht inferieren (Result<Unit>), daher explizit try/catch.
     */
    private fun isDisplayed(node: SemanticsNodeInteraction): Boolean {
        return try {
            node.isDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }
    }

    private companion object {
        /** 30 s: Kaltstart + Hilt-Initialisierung + Kamerapreview-Aufbau. */
        val START_TIMEOUT_MS: Long = TimeUnit.SECONDS.toMillis(30)
    }
}