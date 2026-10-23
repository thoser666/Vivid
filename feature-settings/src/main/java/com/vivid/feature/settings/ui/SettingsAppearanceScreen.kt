package com.vivid.feature.settings.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import com.vivid.feature.settings.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AccentColor
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ThemeMode
import com.vivid.core.i18n.AppLanguage

/**
 * Kategorie „Darstellung“ (PARITY-Zusatz „UI-Farbschemata“, Stufe 2):
 * App-Sprache, Design-Modus (System/Hell/Dunkel/AMOLED) und Akzentfarbe.
 * Die Sprache wirkt sofort; Theme-Änderungen werden beim Speichern übernommen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAppearanceScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLanguage by remember(context) { mutableStateOf(AppLanguage.current(context)) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var accentMenuExpanded by remember { mutableStateOf(false) }

    SettingsSectionScaffold(
        title = stringResource(R.string.cat_appearance_title),
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        ExposedDropdownMenuBox(
            expanded = languageMenuExpanded,
            onExpandedChange = { languageMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = stringResource(selectedLanguage.displayNameRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.appearance_language_title)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("language_picker"),
            )
            ExposedDropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(stringResource(language.displayNameRes)) },
                        onClick = {
                            languageMenuExpanded = false
                            if (selectedLanguage != language) {
                                selectedLanguage = language
                                AppLanguage.select(context, language)
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    context.findActivity()?.recreate()
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.appearance_language_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Design-Modus: System / Hell / Dunkel / AMOLED
        Text(stringResource(R.string.appearance_mode_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.appearance_mode_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.themeMode == mode,
                    onClick = { viewModel.onThemeModeChange(mode) },
                    label = { Text(stringResource(mode.displayNameRes)) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
        Text(
            text = stringResource(
                when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> R.string.appearance_mode_system_desc
                    ThemeMode.LIGHT -> R.string.appearance_mode_light_desc
                    ThemeMode.DARK -> R.string.appearance_mode_dark_desc
                    ThemeMode.AMOLED -> R.string.appearance_mode_amoled_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExposedDropdownMenuBox(
            expanded = accentMenuExpanded,
            onExpandedChange = { accentMenuExpanded = it },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = stringResource(uiState.themeAccent.displayNameRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.appearance_accent_title)) },
                leadingIcon = { AccentColorSample(uiState.themeAccent) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accentMenuExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth().testTag("accent_picker"),
            )
            ExposedDropdownMenu(
                expanded = accentMenuExpanded,
                onDismissRequest = { accentMenuExpanded = false },
            ) {
                AccentColor.entries.forEach { accent ->
                    DropdownMenuItem(
                        text = { Text(stringResource(accent.displayNameRes)) },
                        leadingIcon = { AccentColorSample(accent) },
                        modifier = Modifier.testTag("accent_option_${accent.name}").semantics {
                            selected = uiState.themeAccent == accent
                        },
                        onClick = {
                            accentMenuExpanded = false
                            viewModel.onAccentColorChange(accent)
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.appearance_accent_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AppLanguage.displayNameRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.appearance_language_system
        AppLanguage.GERMAN -> R.string.appearance_language_german
        AppLanguage.ENGLISH -> R.string.appearance_language_english
        AppLanguage.FRENCH -> R.string.appearance_language_french
        AppLanguage.RUSSIAN -> R.string.appearance_language_russian
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Decorative color sample; the adjacent localized name supplies the accessible label. */
@Composable
private fun AccentColorSample(accent: AccentColor) {
    Box(Modifier.size(24.dp).testTag("accent_sample_${accent.name}")
        .clip(CircleShape)
        .background(accent.seedColor)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}
