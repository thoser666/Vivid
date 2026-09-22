package com.vivid.irlbroadcaster

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.vivid.BuildConfig
import com.vivid.R
import com.vivid.core.log.LogStore
import java.io.File

/**
 * Startup-Safe-Mode-Diagnose (Crash-Schleife, siehe [CrashLoopPolicy]):
 *
 * Startet Vivid zweimal hintereinander nicht bis zur UI, zeigt diese Activity
 * den letzten persistierten Crash aus dem In-App-Log (`LogStore`, Tages-
 * dateien `logs/yyyy-MM-dd.log`) — **ohne** Hilt, DataStore, Sentry, Remote-
 * Control-Server oder Compose. Bewusst klassische Views: Die Diagnose muss
 * auch dann funktionieren, wenn genau eine dieser Abhängigkeiten die
 * Crash-Ursache ist.
 *
 * Funktionen: Crash anzeigen (monospace), in die Zwischenablage kopieren,
 * teilen (ACTION_SEND) und „Vollständigen Start versuchen" — ein Ausflug in
 * die volle UI; crasht die wieder, greift der Safe-Mode beim nächsten Start
 * erneut ([CrashLoopPolicy.decide] bleibt auf dem Zählerstand).
 */
class CrashDiagnosticsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashes = runCatching {
            LogStore(File(filesDir, "logs")).load(retentionDays = 30).filter { it.isCrash }
        }.getOrDefault(emptyList())
        val lastCrash = crashes.lastOrNull()

        val padding = (16 * resources.displayMetrics.density).toInt()
        val root = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        column.addView(
            TextView(this).apply {
                setText(R.string.crash_diag_title)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        column.addView(
            TextView(this).apply {
                setText(R.string.crash_diag_intro)
                setPadding(0, padding / 2, 0, padding / 2)
            },
        )

        column.addView(
            TextView(this).apply {
                text = lastCrash?.format() ?: getString(R.string.crash_diag_empty)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, 0, 0, padding)
            },
        )

        column.addView(
            TextView(this).apply {
                text = getString(
                    R.string.crash_diag_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                )
                setPadding(0, 0, 0, padding)
            },
        )

        fun button(textRes: Int, onClick: () -> Unit) = Button(this).apply {
            setText(textRes)
            setOnClickListener { onClick() }
        }

        column.addView(
            button(R.string.crash_diag_copy) {
                val text = lastCrash?.format() ?: getString(R.string.crash_diag_empty)
                ContextCompat.getSystemService(this, ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.crash_diag_title), text),
                )
                Toast.makeText(this, R.string.crash_diag_copied, Toast.LENGTH_SHORT).show()
            },
        )
        column.addView(
            button(R.string.crash_diag_share) {
                val text = buildString {
                    appendLine(getString(R.string.crash_diag_title))
                    appendLine(
                        getString(
                            R.string.crash_diag_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    )
                    appendLine()
                    append(lastCrash?.format() ?: getString(R.string.crash_diag_empty))
                }
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        getString(R.string.crash_diag_share),
                    ),
                )
            },
        )
        column.addView(
            button(R.string.crash_diag_retry) {
                // Flag loeschen, sonst leitet MainActivity.onResume sofort
                // zurueck in die Diagnose (Redirect-Schleife). Der
                // Crash-Schleifen-Zaehler bleibt unberuehrt: Crasht die volle
                // UI vor RESUMED erneut, greift der Safe-Mode beim ueber-
                // naechsten Start wieder (Zaehler weiter >= 2).
                CrashSafeModeState.active = false
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            },
        )

        root.addView(column)
        setContentView(root)
    }
}
