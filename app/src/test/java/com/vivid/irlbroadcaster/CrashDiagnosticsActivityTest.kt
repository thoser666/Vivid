package com.vivid.irlbroadcaster

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.vivid.R
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Startup-Safe-Mode-Diagnose (Robolectric):
 *
 *  1. Mit persistiertem Crash im [LogStore] zeigt die Activity die
 *     Crash-Zeile (💥 + Stacktrace-Ausschnitt) an. *  2. Ohne Crash erscheint der Empty-State-Text.
 *  3. "Vollstaendigen Start versuchen" startet [MainActivity] (Robolectric-
 *     Shadow: nextStartedActivity) und loescht das Safe-Mode-Flag - sonst
 *     wuerde MainActivity.onResume sofort zurueck in die Diagnose leiten
 *     (Redirect-Schleife). Der Zaehler bleibt unberuehrt (Reset erst bei
 *     RESUMED der vollen UI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CrashDiagnosticsActivityTest {

    private lateinit var logsDir: File

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        logsDir = File(app.filesDir, "logs").apply { mkdirs() }
        CrashSafeModeState.active = true
    }

    @After
    fun tearDown() {
        logsDir.deleteRecursively()
        CrashSafeModeState.active = false
    }

    private fun store(): LogStore = LogStore(logsDir)

    private fun allTextViews(view: View): List<TextView> =
        if (view is TextView) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { allTextViews(view.getChildAt(it)) }
        else emptyList()

    private fun allButtons(view: View): List<Button> =
        if (view is Button) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { allButtons(view.getChildAt(it)) }
        else emptyList()

    private fun contentTexts(activity: CrashDiagnosticsActivity): String {
        val root = activity.findViewById<View>(android.R.id.content)
        return allTextViews(root).joinToString("\n") { it.text.toString() }
    }

    // --- 1) Crash sichtbar -----------------------------------------------------

    @Test
    fun `letzter persistierter Crash wird angezeigt`() {
        store().add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.ASSERT,
                tag = "Vivid",
                message = "FATAL EXCEPTION: main; Activity: com.vivid.irlbroadcaster.MainActivity",
                isCrash = true,
            ),
        )

        ActivityScenario.launch(CrashDiagnosticsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val shown = contentTexts(activity)
                assertTrue(
                    "Crash-Text muss angezeigt werden, war: $shown",
                    shown.contains("MainActivity") && shown.contains("💥"),
                )
            }
        }
    }

    @Test
    fun `ohne Crash erscheint der Empty-State`() {
        ActivityScenario.launch(CrashDiagnosticsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val shown = contentTexts(activity)
                assertTrue(
                    "Empty-State muss erscheinen, war: $shown",
                    shown.contains(activity.getString(R.string.crash_diag_empty)),
                )
            }
        }
    }

    // --- 2) Retry-Button -------------------------------------------------------

    @Test
    fun `Retry-Button startet die volle UI`() {
        val controller = buildActivity(CrashDiagnosticsActivity::class.java).setup()
        val activity = controller.get()

        val retry = allButtons(activity.findViewById(android.R.id.content))
            .firstOrNull { it.text == activity.getString(R.string.crash_diag_retry) }
            ?: throw AssertionError("Retry-Button nicht gefunden")
        retry.performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            "Retry muss MainActivity starten",
            MainActivity::class.java.name,
            started.component?.className,
        )
        assertEquals(
            "Retry muss das Safe-Mode-Flag loeschen (sonst Redirect-Schleife ueber MainActivity.onResume)",
            false,
            CrashSafeModeState.active,
        )
        controller.destroy()
    }
}
