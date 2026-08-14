package com.vivid.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Erweiterte semantische Farb-Rollen (Material 3): Das Basis-[androidx.compose.material3.ColorScheme]
 * kennt keine Warning-/Success-Rollen. Diese Tokens ergänzen es (Material Theme Builder
 * erweitertes Palette-Schema) und werden von `VividTheme` (app) themen-abhängig bereitgestellt.
 */
@Immutable
data class ExtendedColors(
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

val LightExtendedColors = ExtendedColors(
    warning = Color(0xFF705D00),
    warningContainer = Color(0xFFFBE27A),
    onWarningContainer = Color(0xFF221B00),
    success = Color(0xFF006D36),
    successContainer = Color(0xFFA0F7B0),
    onSuccessContainer = Color(0xFF00210D),
)

val DarkExtendedColors = ExtendedColors(
    warning = Color(0xFFE7C64A),
    warningContainer = Color(0xFF544300),
    onWarningContainer = Color(0xFFFBE27A),
    success = Color(0xFF84DB99),
    successContainer = Color(0xFF005227),
    onSuccessContainer = Color(0xFFA0F7B0),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
