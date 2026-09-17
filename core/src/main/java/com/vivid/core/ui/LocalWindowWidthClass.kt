package com.vivid.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Breitenklasse des aktuellen Fensters (Material 3 Window Size Classes) als
 * Modul-Naht: `core` ist Compose-faehig, soll aber ohne Activity-Abhaengigkeit
 * bleiben. Die MainActivity berechnet die Klasse einmal pro Activity-
 * Neuerstellung (Rotation/Faltung -> onCreate) via
 * `calculateWindowSizeClass` und stellt sie app-weit bereit; Screens lesen
 * sie ueber diese Local und adaptieren Layouts (Tablets, Foldables,
 * Querformat).
 *
 * Default [WindowWidthSizeClass.Compact]: Phone-Verhalten — gilt auch in
 * Compose-Tests ohne Activity und auf Geraeten vor der ersten Berechnung.
 */
val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * Max-Breite des scrollbaren Inhalts in Settings-Screens: Auf Expanded-
 * Fenstern (Tablets/Foldables/Querformat) auf 600 dp kappen (M3-Empfehlung
 * fuer Formulare) und zentrieren; sonst [Dp.Unspecified] = volle Breite.
 */
fun adaptiveContentMaxWidth(widthClass: WindowWidthSizeClass): Dp =
    if (widthClass == WindowWidthSizeClass.Expanded) 600.dp else Dp.Unspecified

/**
 * Max-Breite des Kamera-Regler-Panels (EV-Slider + Auto-Toggles) im
 * Streaming-Screen: Expanded-Fenster bekommen mehr Platz (320 dp), das
 * Phone bleibt bei der kompakten 220-dp-Kappe.
 */
fun adaptiveControlsMaxWidth(widthClass: WindowWidthSizeClass): Dp =
    if (widthClass == WindowWidthSizeClass.Expanded) 320.dp else 220.dp
