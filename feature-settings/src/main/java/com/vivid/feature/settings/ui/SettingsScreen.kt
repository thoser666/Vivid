package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vivid.core.update.UpdateCheckResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Einstellungen — Kategorie-Übersicht (wie die Settings-Struktur von Moblin):
 * sechs Kacheln führen in die jeweiligen Sub-Screens, in denen die Felder
 * bearbeitet und gespeichert werden.
 *
 * Routen der Sub-Screens (siehe MainActivity):
 *  - settings_streaming  → Streaming & OBS
 *  - settings_appearance → Darstellung (Design-Modus + Akzentfarbe)
 *  - settings_overlays   → Overlays & Widgets
 *  - settings_chatbot    → Chat-Bot & KI
 *  - settings_remote     → Remote & Datenschutz
 *  - settings_about      → Über & Updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    installedVersionName: String = "",
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateState by viewModel.updateState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Einmaliger Update-Check beim Öffnen der Einstellungen (für den Obtainium-Test).
    LaunchedEffect(key1 = installedVersionName) {
        viewModel.checkForUpdates(installedVersionName)
    }

    // Lauscht auf das saveEvent vom ViewModel (Speichern in einem Sub-Screen).
    LaunchedEffect(key1 = Unit) {
        viewModel.saveEvent.collectLatest {
            // Zeige eine Bestätigung an
            scope.launch {
                snackbarHostState.showSnackbar("Einstellungen gespeichert")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Einstellungen",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Wähle eine Kategorie — die Felder werden erst beim Speichern übernommen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsCategories.all.forEach { category ->
                CategoryCard(
                    title = category.title,
                    subtitle = category.subtitle,
                    icon = category.icon,
                    onClick = { navController.navigate(category.route) },
                )
            }

            // Version + Update-Status — direkt auf der Übersicht sichtbar
            if (installedVersionName.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Version $installedVersionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val result = updateState.result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            Text(
                                text = "· ⬆ Update verfügbar: ${result.latestVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Text(
                                text = "· aktuell",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        else -> Unit // Checking / Fehler: still bleiben
                    }
                }
            }

        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
