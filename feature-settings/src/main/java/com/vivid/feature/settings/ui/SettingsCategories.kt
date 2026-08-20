package com.vivid.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Eine Kategorie der Einstellungen (Übersichts-Screen). Die Routen müssen mit
 * den `composable(...)`-Definitionen in `MainActivity` übereinstimmen.
 */
data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Zentrale, testbare Definition der Settings-Kategorien (Struktur wie Moblin:
 * gruppierte Einstellungen mit Sub-Screens). Wird von der Übersicht gerendert
 * und vom Unit-Test auf Vollständigkeit geprüft.
 */
object SettingsCategories {

    /** Alle Kategorien in Anzeige-Reihenfolge. */
    val all: List<SettingsCategory> = listOf(
        SettingsCategory(
            title = "Streaming & OBS",
            subtitle = "Stream-URL/-Key, Plattform-Vorlagen, Multi-Streaming, OBS-Verbindung",
            icon = Icons.Filled.Videocam,
            route = "settings_streaming",
        ),
        SettingsCategory(
            title = "Darstellung",
            subtitle = "Design-Modus (Hell/Dunkel/AMOLED), Akzentfarbe",
            icon = Icons.Filled.Palette,
            route = "settings_appearance",
        ),
        SettingsCategory(
            title = "Overlays & Widgets",
            subtitle = "Twitch-Chat-Overlay, Text-/Info-Widget (Zeit/GPS/Geschwindigkeit)",
            icon = Icons.Filled.Widgets,
            route = "settings_overlays",
        ),
        SettingsCategory(
            title = "Chat-Bot & KI",
            subtitle = "Betriebsmodus, Bot-Konto/LLM, Limits, Owner-Zugriff, Media-Befehle",
            icon = Icons.Filled.ChatBubble,
            route = "settings_chatbot",
        ),
        SettingsCategory(
            title = "Remote & Datenschutz",
            subtitle = "Web-Remote-Control (LAN), Sentry-Fehlerberichte",
            icon = Icons.Filled.Security,
            route = "settings_remote",
        ),
        SettingsCategory(
            title = "Über & Updates",
            subtitle = "Version, Update-Badge, Release-Info",
            icon = Icons.Filled.Info,
            route = "settings_about",
        ),
    )
}
