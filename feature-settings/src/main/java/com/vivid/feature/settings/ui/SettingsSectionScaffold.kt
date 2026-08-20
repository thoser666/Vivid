package com.vivid.feature.settings.ui

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
                            contentDescription = "Zurück",
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
                Text("Speichern")
            }
        }
    }
}

/** Anzeigename des Chat-Bot-Betriebsmodus (UI-spezifisch). */
val ChatBotMode.displayName: String
    get() = when (this) {
        ChatBotMode.COMMAND -> "Bot (wie Moblin)"
        ChatBotMode.AUTONOMOUS -> "KI autonom"
    }

/** Anzeigename des Design-Modus (Settings-Kategorie „Darstellung“). */
val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Hell"
        ThemeMode.DARK -> "Dunkel"
        ThemeMode.AMOLED -> "AMOLED"
    }

/** Anzeigename der Akzentfarbe (Settings-Kategorie „Darstellung“). */
val AccentColor.displayName: String
    get() = when (this) {
        AccentColor.VIVID_GREEN -> "Vivid Grün"
        AccentColor.OCEAN_BLUE -> "Ozean-Blau"
        AccentColor.ROYAL_PURPLE -> "Royal-Lila"
        AccentColor.SUNSET_ORANGE -> "Sonnenuntergang"
        AccentColor.ROSE_PINK -> "Rosé"
        AccentColor.TEAL -> "Petrol"
    }

/** Swatch-Farbe des Akzents (aus dem HCT-Seed, rein sRGB). */
val AccentColor.seedColor: Color
    get() = Color(seedHex.removePrefix("#").toLong(16) or 0xFF000000)

/** Anzeigename des Befehlsscopes (UI-spezifisch). */
val ChatBotCommandScope.displayName: String
    get() = when (this) {
        ChatBotCommandScope.ALL -> "Alle !-Befehle"
        ChatBotCommandScope.MENTION -> "Nur Erwähnung"
        ChatBotCommandScope.PREFIX -> "Eigenes Präfix"
    }

/**
 * Passwort-/Token-Eingabefeld mit Sichtbarkeits-Toggle (Auge).
 * Genutzt für Twitch-OAuth-Token und LLM-API-Key.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Eingabe ausblenden" else "Eingabe anzeigen",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
