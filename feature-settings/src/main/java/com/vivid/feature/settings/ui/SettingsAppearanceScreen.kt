package com.vivid.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AccentColor
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ThemeMode

/**
 * Kategorie „Darstellung“ (PARITY-Zusatz „UI-Farbschemata“, Stufe 2):
 * Design-Modus (System/Hell/Dunkel/AMOLED) + kuratierte Akzentfarbe.
 * Wirkt sofort beim Speichern — kein App-Neustart nötig (VividTheme liest
 * den State in der MainActivity live).
 */
@Composable
fun SettingsAppearanceScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    SettingsSectionScaffold(
        title = "Darstellung",
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Design-Modus: System / Hell / Dunkel / AMOLED
        Text("Design-Modus", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Legt fest, ob Vivid hell, dunkel oder im AMOLED-Modus erscheint — oder dem System folgt. AMOLED nutzt rein-schwarze Flächen (spart auf OLED-Displays Energie).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.themeMode == mode,
                    onClick = { viewModel.onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemeMode.entries.size,
                    ),
                    label = { Text(mode.displayName) },
                )
            }
        }
        Text(
            text = when (uiState.themeMode) {
                ThemeMode.SYSTEM -> "Folgt der System-Einstellung deines Geräts (Standard)."
                ThemeMode.LIGHT -> "Immer hell — unabhängig von der System-Einstellung."
                ThemeMode.DARK -> "Immer dunkel — unabhängig von der System-Einstellung."
                ThemeMode.AMOLED -> "Dunkel mit rein-schwarzen Flächen: maximaler Kontrast, weniger Akku-Verbrauch auf OLED-Screens."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Akzentfarbe: kuratierte Material-3-Paletten (Swatch = HCT-Seed)
        Text(
            text = "Akzentfarbe",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Färbt Buttons, Schalter, Links und Hervorhebungen. Jede Farbe ist eine komplette Material-3-Palette (TonalSpot) — der Standard bleibt Vivid-Grün.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                AccentSwatch(
                    accent = accent,
                    selected = uiState.themeAccent == accent,
                    onClick = { viewModel.onAccentColorChange(accent) },
                )
            }
        }
    }
}

/** Farb-Kreis einer Akzentfarbe; ausgewählt → Auswahl-Ring in Primary-Farbe. */
@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = ringColor,
                    shape = CircleShape,
                )
                .padding(if (selected) 3.dp else 5.dp)
                .background(color = accent.seedColor, shape = CircleShape),
        )
        Text(
            text = accent.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
