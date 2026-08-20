package com.vivid.irlbroadcaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.vivid.core.data.AccentColor
import com.vivid.core.ui.theme.DarkExtendedColors
import com.vivid.core.ui.theme.LightExtendedColors
import com.vivid.core.ui.theme.LocalExtendedColors

/**
 * Vivid-Farbpalette — Material 3, generiert um den Brand-Seed „Vivid Green“
 * (Android-Grün `#3DDC84`, identisch zur Akzentfarbe in den Projekt-SVGs).
 * Ersetzt die Template-Standardfarben (Purple/Pink) aus dem Android-Studio-Scaffold.
 *
 * Stufe 2 der UI-Farbschemata (PARITY-Zusatz-Feature): User-Toggle
 * (System/Hell/Dunkel/AMOLED) + kuratierte Akzentfarben als Settings-Kategorie
 * „Darstellung“. Der Standard-Akzent [AccentColor.VIVID_GREEN] nutzt exakt die
 * ursprünglichen (Material-Theme-Builder-)Werte — die übrigen Akzente sind
 * echte Material-3-TonalSpot-Paletten (HCT-Seed, Chroma 40) der
 * material-color-utilities.
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
 * AMOLED-Variante des Dark-Schemas: identisch bis auf rein-schwarze Flächen
 * (Background/Surface/Container) — spart auf OLED-Displays Energie und
 * maximiert den Kontrast. Wird über die Settings-Kategorie „Darstellung“
 * gewählt ([ThemeMode.AMOLED]).
 */
internal val VividAmoledColorScheme = VividDarkColorScheme.copy(
    background = Color(0xFF000000),
    onBackground = VividDarkColorScheme.onBackground,
    surface = Color(0xFF000000),
    onSurface = VividDarkColorScheme.onSurface,
    surfaceVariant = Color(0xFF10150F),
    onSurfaceVariant = VividDarkColorScheme.onSurfaceVariant,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0F0D),
    surfaceContainer = Color(0xFF111411),
    surfaceContainerHigh = Color(0xFF1B1E1B),
    surfaceContainerHighest = Color(0xFF252925),
    outline = Color(0xFF545D56),
    outlineVariant = Color(0xFF3F4842),
)

/**
 * Primary-Familie einer Akzentfarbe (Light + Dark). Alle anderen Rollen
 * (Neutral, Secondary, Tertiary, Error) bleiben Vivid-Basis — die App nutzt
 * Secondary/Tertiary nur für semantische Badges.
 */
internal data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
)

/**
 * Alle kuratierten Akzente inkl. Vivid-Grün (identisch zur Basis-Palette).
 * Schlüssel muss mit [AccentColor.entries] übereinstimmen (Test sichert das).
 */
internal val accentPalettes: Map<AccentColor, AccentPalette> = mapOf(
    AccentColor.VIVID_GREEN to AccentPalette(
        lightPrimary = Color(0xFF006B3F),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF9EF5BC),
        lightOnPrimaryContainer = Color(0xFF00210F),
        darkPrimary = Color(0xFF6BE59A),
        darkOnPrimary = Color(0xFF00391F),
        darkPrimaryContainer = Color(0xFF00522E),
        darkOnPrimaryContainer = Color(0xFF9EF5BC),
    ),
    AccentColor.OCEAN_BLUE to AccentPalette(
        lightPrimary = Color(0xFF256293),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFCFE5FF),
        lightOnPrimaryContainer = Color(0xFF001D34),
        darkPrimary = Color(0xFF99CBFF),
        darkOnPrimary = Color(0xFF003355),
        darkPrimaryContainer = Color(0xFF004A78),
        darkOnPrimaryContainer = Color(0xFFCFE5FF),
    ),
    AccentColor.ROYAL_PURPLE to AccentPalette(
        lightPrimary = Color(0xFF6A5294),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFE5DEFF),
        lightOnPrimaryContainer = Color(0xFF250A4C),
        darkPrimary = Color(0xFFD4BBFF),
        darkOnPrimary = Color(0xFF3A2362),
        darkPrimaryContainer = Color(0xFF523A7A),
        darkOnPrimaryContainer = Color(0xFFE5DEFF),
    ),
    AccentColor.SUNSET_ORANGE to AccentPalette(
        lightPrimary = Color(0xFF855303),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDDB9),
        lightOnPrimaryContainer = Color(0xFF2B1700),
        darkPrimary = Color(0xFFFDB967),
        darkOnPrimary = Color(0xFF472A00),
        darkPrimaryContainer = Color(0xFF663E00),
        darkOnPrimaryContainer = Color(0xFFFFDDB9),
    ),
    AccentColor.ROSE_PINK to AccentPalette(
        lightPrimary = Color(0xFF91475A),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFD9E0),
        lightOnPrimaryContainer = Color(0xFF3D0318),
        darkPrimary = Color(0xFFFFB1C2),
        darkOnPrimary = Color(0xFF58192D),
        darkPrimaryContainer = Color(0xFF753043),
        darkOnPrimaryContainer = Color(0xFFFFD9E0),
    ),
    AccentColor.TEAL to AccentPalette(
        lightPrimary = Color(0xFF006A62),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF91F4E7),
        lightOnPrimaryContainer = Color(0xFF00201D),
        darkPrimary = Color(0xFF74D7CB),
        darkOnPrimary = Color(0xFF003732),
        darkPrimaryContainer = Color(0xFF005049),
        darkOnPrimaryContainer = Color(0xFF91F4E7),
    ),
)

/** Primary-Familie der [darkPrimary]-Seite in das übergebene Dunkel-Schema einsetzen. */
internal fun ColorScheme.withAccentDark(palette: AccentPalette): ColorScheme = copy(
    primary = palette.darkPrimary,
    onPrimary = palette.darkOnPrimary,
    primaryContainer = palette.darkPrimaryContainer,
    onPrimaryContainer = palette.darkOnPrimaryContainer,
)

/**
 * App-Theme: Design-Modus + Akzentfarbe aus den Settings (Kategorie
 * „Darstellung“). Defaults = bisheriges Verhalten: System-Dark-Mode +
 * Vivid-Grün. `darkTheme` ist bewusst überschreibbar (für Tests und die
 * Einstellung), [amoled] entscheidet über die schwarzen Flächen.
 */
@Composable
fun VividTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    accent: AccentColor = AccentColor.VIVID_GREEN,
    content: @Composable () -> Unit,
) {
    val palette = accentPalettes.getValue(accent)
    val colorScheme = when {
        amoled -> VividAmoledColorScheme.withAccentDark(palette)
        darkTheme -> VividDarkColorScheme.withAccentDark(palette)
        else -> VividLightColorScheme.copy(
            primary = palette.lightPrimary,
            onPrimary = palette.lightOnPrimary,
            primaryContainer = palette.lightPrimaryContainer,
            onPrimaryContainer = palette.lightOnPrimaryContainer,
        )
    }

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
