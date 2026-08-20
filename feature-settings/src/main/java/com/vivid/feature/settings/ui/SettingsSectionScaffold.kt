package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AccentColor
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.core.data.ThemeMode

/**
 * Gemeinsames Layout für die Settings-Sub-Screens (Kategorie-Ansichten).
 * Bietet Top-Bar mit Zurück-Pfeil, scrollbaren Inhalt und den
 * „Speichern“-Button unten. Die einzelnen Kategorie-Screens füllen
 * `content` mit ihren Feldern — alle lesen/schreiben denselben
 * [SettingsViewModel]-State.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save_button))
            }
        }
    }
}

/** Anzeigename des Chat-Bot-Betriebsmodus (UI-spezifisch, String-Ressource). */
val ChatBotMode.displayNameRes: Int
    get() = when (this) {
        ChatBotMode.COMMAND -> R.string.chatbot_mode_command
        ChatBotMode.AUTONOMOUS -> R.string.chatbot_mode_autonomous
    }

/** Anzeigename des Design-Modus (Settings-Kategorie „Darstellung“). */
val ThemeMode.displayNameRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.AMOLED -> R.string.theme_mode_amoled
    }

/** Anzeigename der Akzentfarbe (Settings-Kategorie „Darstellung“). */
val AccentColor.displayNameRes: Int
    get() = when (this) {
        AccentColor.VIVID_GREEN -> R.string.accent_vivid_green
        AccentColor.OCEAN_BLUE -> R.string.accent_ocean_blue
        AccentColor.ROYAL_PURPLE -> R.string.accent_royal_purple
        AccentColor.SUNSET_ORANGE -> R.string.accent_sunset_orange
        AccentColor.ROSE_PINK -> R.string.accent_rose_pink
        AccentColor.TEAL -> R.string.accent_teal
    }

/** Swatch-Farbe des Akzents (aus dem HCT-Seed, rein sRGB). */
val AccentColor.seedColor: Color
    get() = Color(seedHex.removePrefix("#").toLong(16) or 0xFF000000)

/** Anzeigename des Befehlsscopes (UI-spezifisch, String-Ressource). */
val ChatBotCommandScope.displayNameRes: Int
    get() = when (this) {
        ChatBotCommandScope.ALL -> R.string.chatbot_scope_all
        ChatBotCommandScope.MENTION -> R.string.chatbot_scope_mention
        ChatBotCommandScope.PREFIX -> R.string.chatbot_scope_prefix
    }

/**
 * Passwort-/Token-Eingabefeld mit Sichtbarkeits-Toggle (Auge).
 * Genutzt für Twitch-OAuth-Token und LLM-API-Key.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.secret_hide else R.string.secret_show,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
