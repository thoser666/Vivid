package com.vivid.feature.streaming.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.core.data.PrivacyZone
import com.vivid.feature.streaming.R
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Zonen-Editor (P1 der Skizze docs/architecture/privacy-anonymization.md):
 * Overlays über der Vorschau, in dem bis zu [PrivacyZone.MAX_ZONES] Ellipsen-
 * Zonen per Touch positioniert und skaliert werden. Die Koordinaten werden
 * **normalisiert (0..1) quellrelativ** gespeichert — (0, 0) ist oben links,
 * (1, 1) unten rechts; die Engine mappt sie framesynchron auf die Ellipsen-
 * Uniforms des Privacy-Shaders (Y-Flip in [com.vivid.feature.streaming.toPrivacyEllipse]).
 *
 * Interaktion:
 * - **Ziehen in einer Ellipse** verschiebt ihr Zentrum (Radius bleibt).
 * - **Ziehen am Rand (außerhalb, aber nah)** skaliert die Ellipse symmetrisch
 *   um ihr Zentrum (Abstand des Fingers zum Zentrum = Radius).
 * - **„+ Zone“** legt eine neue Zone in der Bildmitte an (max. 4).
 * - **Mülleimer** löscht die zuletzt ausgewählte Zone.
 *
 * Bewusst nur im Idle-Zustand sichtbar (Szenen-Sperre-Muster): Während des
 * Streams bleibt der Editor geschlossen — Zonen werden vorher vorbereitet.
 * Die Anonymisierung selbst wirkt unabhängig davon auf Vorschau + Encoder
 * (Fail-safe, nicht Fail-hidden, Skizze §3).
 */
@Composable
fun BoxScope.ZoneEditorOverlay(
    zones: List<PrivacyZone>,
    onZonesChange: (List<PrivacyZone>) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }

    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(12.dp)
            .testTag("zone_editor"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.privacy_zone_editor_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = {
                        if (zones.size < PrivacyZone.MAX_ZONES) {
                            onZonesChange(
                                zones + PrivacyZone(
                                    centerX = 0.5f,
                                    centerY = 0.5f,
                                    radiusX = DEFAULT_ZONE_RADIUS,
                                    radiusY = DEFAULT_ZONE_RADIUS,
                                ),
                            )
                            selected = zones.size
                        }
                    },
                    enabled = zones.size < PrivacyZone.MAX_ZONES,
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = stringResource(R.string.privacy_zone_add),
                    )
                }
                if (selected >= 0 && selected < zones.size) {
                    FilledTonalIconButton(
                        onClick = {
                            onZonesChange(
                                zones.filterIndexed { index, _ -> index != selected },
                            )
                            selected = -1
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.privacy_zone_delete),
                        )
                    }
                }
                FilledTonalIconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.privacy_zone_editor_close),
                    )
                }
            }
            Text(
                text = stringResource(R.string.privacy_zone_editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ZoneCanvas(
                zones = zones,
                selected = selected,
                onSelect = { selected = it },
                onZoneMoved = { index, zone ->
                    onZonesChange(
                        zones.mapIndexed { i, z -> if (i == index) zone else z },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // 4:3-Symbolfläche (Zonen sind quellrelativ normiert) — ohne
                    // feste Aspect-Ratio hätte die Canvas null Höhe.
                    .aspectRatio(4f / 3f),
            )
        }
    }
}

/**
 * Die Zeichenfläche des Editors: zeichnet die Zonen (Ellipsen mit weicher
 * Kante analog zum Shader) und verarbeitet das Touch-Drag/Resize. Die Fläche
 * ist eine 4:3-Symbolfläche — die Zonen sind quellrelativ normiert, die echte
 * Vorschau kann jedes Seitenverhältnis haben.
 */
@Composable
private fun ZoneCanvas(
    zones: List<PrivacyZone>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onZoneMoved: (Int, PrivacyZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorStroke = MaterialTheme.colorScheme.primary
    val zoneFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val idleStroke = MaterialTheme.colorScheme.onSurfaceVariant
    val idleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    // Aktiver Drag: Index der Zone + Startzustand (Zentrum beim Touch-Down).
    // Neueste Werte ohne Detector-Restart: pointerInput(Unit) darf bei jedem
    // Drag-Frame (neue Zonen-Liste!) NICHT neu starten — sonst bräche die
    // Geste mitten im Ziehen ab (Compose-Fallstrick bei keyed pointerInput).
    val currentZones by rememberUpdatedState(zones)
    val currentOnZoneMoved by rememberUpdatedState(onZoneMoved)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("zone_canvas")
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .pointerInput(Unit) {
                    // Direkte Manipulation ohne Touch-Slop: die Zone wird beim
                    // DOWN-Event aufgelöst und folgt ab dem ersten Pixel —
                    // detectDragGestures würde die ersten Move-Pixel als Slop
                    // schlucken (Test wie UX: Ziel läuft nie hinterher).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val nx = (down.position.x / w).coerceIn(0f, 1f)
                        val ny = (down.position.y / h).coerceIn(0f, 1f)
                        val index = nearestZone(currentZones, nx, ny)
                        if (index < 0) return@awaitEachGesture
                        currentOnSelect(index)
                        val start = currentZones[index]
                        // Resize, wenn der Touch außerhalb der Ellipse, aber
                        // innerhalb des Resize-Saums liegt.
                        val resizing = isOnResizeHandle(start, nx, ny)
                        var zone = start
                        var lastPosition = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) break
                            change.consume()
                            val delta = change.position - lastPosition
                            lastPosition = change.position
                            zone = if (resizing) {
                                // Symmetrische Skalierung: Abstand des Zeigers
                                // zum Zentrum wird der neue Radius (X/Y entkoppelt
                                // über das Flächenverhältnis der Canvas).
                                val dx = abs(change.position.x - start.centerX * w) / w
                                val dy = abs(change.position.y - start.centerY * h) / h
                                zone.copy(
                                    radiusX = dx.coerceIn(MIN_ZONE_RADIUS, MAX_ZONE_RADIUS),
                                    radiusY = dy.coerceIn(MIN_ZONE_RADIUS, MAX_ZONE_RADIUS),
                                )
                            } else {
                                zone.copy(
                                    centerX = PrivacyZone.shift(zone.centerX, delta.x / w),
                                    centerY = PrivacyZone.shift(zone.centerY, delta.y / h),
                                )
                            }
                            currentOnZoneMoved(index, zone)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            zones.forEachIndexed { index, zone ->
                val active = index == selected
                val drawStyle = if (active) editorStroke else idleStroke
                val fill = if (active) zoneFill else idleFill
                drawOval(
                    color = fill,
                    topLeft = Offset(
                        (zone.centerX - zone.radiusX) * w,
                        (zone.centerY - zone.radiusY) * h,
                    ),
                    size = Size(zone.radiusX * 2 * w, zone.radiusY * 2 * h),
                )
                drawOval(
                    color = drawStyle,
                    topLeft = Offset(
                        (zone.centerX - zone.radiusX) * w,
                        (zone.centerY - zone.radiusY) * h,
                    ),
                    size = Size(zone.radiusX * 2 * w, zone.radiusY * 2 * h),
                    style = Stroke(width = if (active) 4f else 2f),
                )
            }
        }
    }
}

/** Nächste Zone zum Touch-Punkt (innerhalb der Ellipse) — sonst -1. */
private fun nearestZone(
    zones: List<PrivacyZone>,
    nx: Float,
    ny: Float,
): Int {
    var best = -1
    var bestDist = Float.MAX_VALUE
    zones.forEachIndexed { index, zone ->
        val dx = (nx - zone.centerX) / zone.radiusX.coerceAtLeast(0.001f)
        val dy = (ny - zone.centerY) / zone.radiusY.coerceAtLeast(0.001f)
        val dist = hypot(dx, dy)
        if (dist <= 1f && dist < bestDist) {
            best = index
            bestDist = dist
        }
    }
    return best
}

/**
 * Resize-Saum: Touch außerhalb der Ellipse, aber nahe am Rand (0..[RESIZE_SLOP]
 * in normalisierten Ellipsen-Koordinaten) skaliert statt zu verschieben.
 */
private fun isOnResizeHandle(zone: PrivacyZone, x: Float, y: Float): Boolean {
    val dx = (x - zone.centerX) / zone.radiusX.coerceAtLeast(0.001f)
    val dy = (y - zone.centerY) / zone.radiusY.coerceAtLeast(0.001f)
    val dist = hypot(dx, dy)
    return dist > 1f && dist <= 1f + RESIZE_SLOP
}

/** Standard-Radius einer neuen Zone (halbe Bildmitte, dezente Größe). */
private const val DEFAULT_ZONE_RADIUS = 0.15f

/** Minimaler Radius (Anti-Frust: eine weggezoomte Zone lässt sich greifen). */
private const val MIN_ZONE_RADIUS = 0.03f

/** Maximaler Radius (eine Zone überdeckt nie das ganze Bild). */
private const val MAX_ZONE_RADIUS = 0.45f

/** Resize-Saum in normalisierten Ellipsen-Koordinaten (außerhalb des Randes). */
private const val RESIZE_SLOP = 0.35f
