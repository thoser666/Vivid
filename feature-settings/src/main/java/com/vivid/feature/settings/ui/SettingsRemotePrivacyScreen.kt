package com.vivid.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vivid.core.data.AppSettings

/**
 * Kategorie „Remote & Datenschutz“: Web-Remote-Control (LAN-Server, Token,
 * Berechtigung) und Sentry-Fehlerberichte (Opt-out).
 */
@Composable
fun SettingsRemotePrivacyScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    remoteControl: RemoteControlInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Ab Android 17 (API 37, targetSdk 37) braucht die Web-Remote-Control die
    // ACCESS_LOCAL_NETWORK-Runtime-Berechtigung. Wird sie hier erteilt, wird
    // der LAN-Server neu gestartet, damit er sie übernimmt.
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.restartRemoteControlServer()
        }
    }
    val needsLocalNetworkPermission =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) != PackageManager.PERMISSION_GRANTED

    SettingsSectionScaffold(
        title = "Remote & Datenschutz",
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Web-Remote-Control: Zugangsdaten für den LAN-Server
        Text("Web-Remote-Control", style = MaterialTheme.typography.titleLarge)
        if (remoteControl.token.isNotBlank()) {
            Text(
                text = "Im gleichen WLAN erreichbar unter http://<GERÄTE-IP>:${remoteControl.port}/status — Aktionen via Authorization: Bearer <token>.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = remoteControl.token,
                onValueChange = {},
                readOnly = true,
                label = { Text("Remote-Token") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Token wird geladen…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (needsLocalNetworkPermission) {
            OutlinedButton(
                onClick = { localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("LAN-Zugriff für Remote-Control erlauben")
            }
        }

        // Datenschutz: Sentry-Fehlerberichte an/aus (Opt-out, Default: an)
        Text("Datenschutz & Fehlerberichte", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Fehlerberichte senden (Sentry)", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.sentryEnabled,
                onCheckedChange = viewModel::onSentryEnabledChange,
            )
        }
        Text(
            text = "Sendet bei Abstürzen Fehlerberichte an Sentry (Standard: an). Es werden keine Screenshots aufgenommen; Geräte-/IP-Informationen sind deaktiviert. Aus = es wird nichts an Sentry übertragen. Details: PRIVACY.md im Repository.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
