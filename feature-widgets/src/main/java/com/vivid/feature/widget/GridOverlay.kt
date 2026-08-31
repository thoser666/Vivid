package com.vivid.feature.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-Zustand des Grid-Overlays. */
data class GridOverlayUiState(
    val enabled: Boolean = false,
    val spacingDp: Int = 40,
)

@HiltViewModel
class GridOverlayViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GridOverlayUiState())
    val uiState: StateFlow<GridOverlayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.gridOverlayEnabled,
                        spacingDp = settings.gridOverlaySpacingDp,
                    )
                }
            }
        }
    }
}

/**
 * Raster-Overlay über der Streaming-Vorschau zur Widget-Positionierung.
 * Zeigt ein feines weißes Raster mit konfigurierbarem Abstand.
 */
@Composable
fun GridOverlay(
    modifier: Modifier = Modifier,
    viewModel: GridOverlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled) return

    val density = LocalDensity.current
    val gridColor = Color.White.copy(alpha = 0.25f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidthPx = with(density) { 0.5.dp.toPx() }
        val dashLengthPx = with(density) { 8.dp.toPx() }
        val gapLengthPx = with(density) { 4.dp.toPx() }
        val spacingPx = with(density) { uiState.spacingDp.dp.toPx() }
        val width = size.width
        val height = size.height

        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx))

        // Vertikale Linien
        var x = 0f
        while (x <= width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = strokeWidthPx,
                pathEffect = pathEffect,
            )
            x += spacingPx
        }

        // Horizontale Linien
        var y = 0f
        while (y <= height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = strokeWidthPx,
                pathEffect = pathEffect,
            )
            y += spacingPx
        }
    }
}
