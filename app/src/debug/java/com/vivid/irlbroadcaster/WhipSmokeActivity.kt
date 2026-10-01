package com.vivid.irlbroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.R
import com.vivid.feature.streaming.whip.WHIPSmokeRunner
import com.vivid.feature.streaming.whip.WHIPSmokeRunner.WhipSmokeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Debug-only Einstiegspunkt für den WHIP-P0-Gerätesmoke (docs/whip-spike.md §11.8):
 * ruft [WHIPSmokeRunner] (→ `WHIPIngestProbe.sdkSmoke`, dlopen/RegisterNatives-Probe)
 * auf einem Hintergrund-Thread und zeigt das Ergebnis. Bewusst KEIN Launcher-Intent-
 * Filter und kein Produktivpfad — Start via
 * `adb shell am start -n com.vivid.debug/com.vivid.irlbroadcaster.WhipSmokeActivity`
 * (Shell-UID darf non-exported Activities starten). Liegt ausschließlich im
 * debug-SourceSet und landet nie in Release-/FOSS-Store-Builds.
 */
class WhipSmokeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("WHIP-Geraetesmoke-Activity gestartet")
        setContent {
            MaterialTheme {
                WhipSmokeScreen(onRunSmoke = ::runSmoke)
            }
        }
    }

    private fun runSmoke(): WhipSmokeResult {
        val result = WHIPSmokeRunner.run(applicationContext)
        if (result.ok) {
            Timber.i("WHIP-Geraetesmoke OK: %s", result.detail)
        } else {
            Timber.w("WHIP-Geraetesmoke FEHLGESCHLAGEN: %s", result.detail)
        }
        return result
    }
}

@Composable
private fun WhipSmokeScreen(onRunSmoke: () -> WhipSmokeResult) {
    var result by remember { mutableStateOf<WhipSmokeResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.whip_smoke_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.whip_smoke_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    scope.launch(Dispatchers.Default) {
                        val r = onRunSmoke()
                        withContext(Dispatchers.Main) {
                            result = r
                            running = false
                        }
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (running) R.string.whip_smoke_running else R.string.whip_smoke_run,
                    ),
                )
            }
            val shown = result
            when {
                running -> Text(text = stringResource(R.string.whip_smoke_running))
                shown != null -> Text(
                    text = shown.detail,
                    color = if (shown.ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
