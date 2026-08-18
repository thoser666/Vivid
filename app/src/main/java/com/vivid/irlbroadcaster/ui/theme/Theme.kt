package com.vivid.irlbroadcaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.vivid.core.ui.theme.DarkExtendedColors
import com.vivid.core.ui.theme.LightExtendedColors
import com.vivid.core.ui.theme.LocalExtendedColors

/**
 * Vivid-Farbpalette — Material 3, generiert um den Brand-Seed „Vivid Green“
 * (Android-Grün `#3DDC84`, identisch zur Akzentfarbe in den Projekt-SVGs).
 * Ersetzt die Template-Standardfarben (Purple/Pink) aus dem Android-Studio-Scaffold.
 *
 * Stufe 1 der UI-Farbschemata (PARITY-Zusatz-Feature): System-Dark-Mode +
 * Branding-Palette. Stufe 2 (User-Toggle Hell/Dunkel/AMOLED + Akzentfarbe)
 * ist als Settings-Kategorie „Darstellung“ geplant.
 */
internal val VividLightColorScheme = lightColorScheme(
    primary = Color(0xFF006B3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF5BC),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3B6471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF8),
    onTertiaryContainer = Color(0xFF001F27),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
)

internal val VividDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6BE59A),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF00522E),
    onPrimaryContainer = Color(0xFF9EF5BC),
    secondary = Color(0xFFB4CCBA),
    onSecondary = Color(0xFF203528),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFA2CDDB),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF214D59),
    onTertiaryContainer = Color(0xFFBEEAF8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
)

/**
 * App-Theme: folgt standardmäßig dem System-Dark-Mode (isSystemInDarkTheme).
 * `darkTheme` ist bewusst überschreibbar (für Tests und die geplante
 * Stufe-2-Einstellung „Darstellung“).
 */
@Composable
fun VividTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VividDarkColorScheme else VividLightColorScheme

    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
