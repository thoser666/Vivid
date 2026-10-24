package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
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
 * Titel/Untertitel sind String-Ressourcen (i18n).
 */
data class SettingsCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
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
            titleRes = R.string.cat_streaming_title,
            subtitleRes = R.string.cat_streaming_subtitle,
            icon = Icons.Filled.Videocam,
            route = "settings_streaming",
        ),
        SettingsCategory(
            titleRes = R.string.camera_section_title,
            subtitleRes = R.string.camera_section_desc,
            icon = Icons.Filled.CameraAlt,
            route = "settings_camera",
        ),
        SettingsCategory(
            titleRes = R.string.cat_appearance_title,
            subtitleRes = R.string.cat_appearance_subtitle,
            icon = Icons.Filled.Palette,
            route = "settings_appearance",
        ),
        SettingsCategory(
            titleRes = R.string.cat_overlays_title,
            subtitleRes = R.string.cat_overlays_subtitle,
            icon = Icons.Filled.Widgets,
            route = "settings_overlays",
        ),
        SettingsCategory(
            titleRes = R.string.cat_chatbot_title,
            subtitleRes = R.string.cat_chatbot_subtitle,
            icon = Icons.Filled.ChatBubble,
            route = "settings_chatbot",
        ),
        SettingsCategory(
            titleRes = R.string.cat_remote_title,
            subtitleRes = R.string.cat_remote_subtitle,
            icon = Icons.Filled.Security,
            route = "settings_remote",
        ),
        SettingsCategory(
            titleRes = R.string.cat_about_title,
            subtitleRes = R.string.cat_about_subtitle,
            icon = Icons.Filled.Info,
            route = "settings_about",
        ),
        SettingsCategory(
            titleRes = R.string.cat_logs_title,
            subtitleRes = R.string.cat_logs_subtitle,
            icon = Icons.Filled.BugReport,
            route = "settings_logs",
        ),
    )
}
