package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Einstellungen für die manuelle Kamera-Steuerung.
 *
 * Bietet:
 * - Fokus-Slider (0.0 = Unendlich, höhere Werte = näher)
 * - Linsen-Auswahl (Ultraweit/Weit/Tele)
 *
 * Die tatsächliche Kamera-Steuerung erfolgt über die StreamingEngine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCameraScreen(
    onBack: () -> Unit = {},
    onSave: () -> Unit = {},
    viewModel: SettingsCameraViewModel = hiltViewModel(),
) {
    val focusDistance by viewModel.focusDistance.collectAsState()
    val hasManualFocus by viewModel.hasManualFocus.collectAsState()
    val availableLenses by viewModel.availableLenses.collectAsState()
    val currentLensId by viewModel.currentLensId.collectAsState()

    SettingsSectionScaffold(
        title = stringResource(R.string.camera_section_title),
        onBack = onBack,
        onSave = onSave,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Manual Focus ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.camera_focus_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.camera_focus_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (hasManualFocus) {
                        // Focus Slider: 0.0 = Unendlich, 10.0 = Makro
                        Slider(
                            value = focusDistance,
                            onValueChange = { viewModel.setFocusDistance(it) },
                            valueRange = 0f..10f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.camera_focus_far),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.camera_focus_near),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.camera_focus_not_supported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // --- Lens Selection ---
            if (availableLenses.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.camera_lens_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.camera_lens_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            availableLenses.forEach { lens ->
                                FilterChip(
                                    selected = lens.id == currentLensId,
                                    onClick = { viewModel.selectLens(lens.id) },
                                    label = { Text(text = lens.displayName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
