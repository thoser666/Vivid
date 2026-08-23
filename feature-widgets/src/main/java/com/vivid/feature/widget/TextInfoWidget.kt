package com.vivid.feature.widget

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.feature.widgets.R

/**
 * Text-/Info-Widget über der Streaming-Vorschau: Uhrzeit, Datum, GPS-Koordinaten und
 * Geschwindigkeit. Blendet sich aus, wenn das Widget in den Einstellungen deaktiviert ist.
 * Wird ein Standortfeld angezeigt, ohne dass die Location-Permission erteilt ist, wird
 * sie einmalig angefragt — verweigert der Nutzer, laufen Uhrzeit/Datum weiter und
 * GPS/Geschwindigkeit bleiben „–“.
 */
@Composable
fun TextInfoWidget(
    modifier: Modifier = Modifier,
    viewModel: TextInfoWidgetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled) return

    // GPS-Permission anfragen, sobald das Widget Standortfelder anzeigen soll.
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(uiState.enabled, uiState.showLocation, uiState.showSpeed, uiState.showAltitude, uiState.template) {
        val hasTemplate = uiState.template.isNotBlank()
        val needsLocation = uiState.enabled && (uiState.showLocation || uiState.showSpeed || uiState.showAltitude || hasTemplate)
        if (needsLocation &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (uiState.template.isNotBlank() && uiState.resolvedTemplate.isNotBlank()) {
            Text(
                text = uiState.resolvedTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        } else {
            if (uiState.showTime) {
                Text(
                    text = uiState.time,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (uiState.date.isNotBlank()) {
                    Text(
                        text = uiState.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (uiState.showLocation && uiState.location.isNotBlank()) {
                Text(
                    text = stringResource(R.string.widget_location_label, uiState.location),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
            if (uiState.showSpeed && uiState.speed.isNotBlank()) {
                Text(
                    text = stringResource(R.string.widget_speed_label, uiState.speed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
            if (uiState.showAltitude && uiState.altitude.isNotBlank()) {
                Text(
                    text = stringResource(R.string.widget_altitude_label, uiState.altitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
    }
}
