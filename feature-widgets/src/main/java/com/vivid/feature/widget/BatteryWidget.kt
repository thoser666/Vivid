package com.vivid.feature.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-Zustand des Akku-Widgets. */
data class BatteryWidgetUiState(
    val enabled: Boolean = false,
    val showIcon: Boolean = true,
    val showPercent: Boolean = true,
    val level: Int = 100,
    val isCharging: Boolean = false,
    val lowThreshold: Int = 15,
)

/** Liest Akku-Level aus dem System — in Unit-Tests injizierbar. */
typealias BatteryLevelReader = () -> Pair<Int, Boolean>

/** Akku-Level aus dem System-Broadcast lesen. */
private fun systemBatteryLevel(context: Context): Pair<Int, Boolean> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return percent to charging
}

/** Akku-Icon basierend auf Ladestand und Lade-Status. */
private fun batteryIcon(level: Int, isCharging: Boolean): String = when {
    isCharging -> "\uD83D\uDD0B" // 🔋 (充电)
    level <= 10 -> "🪫"
    level <= 20 -> "🟥"
    level <= 40 -> "🟧"
    level <= 60 -> "🟨"
    level <= 80 -> "🟩"
    else -> "🔋"
}

@HiltViewModel
class BatteryWidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Test-Hook: Batterie-Reader kann durch Test-Funktion ersetzt werden. */
    internal var batteryLevelReader: BatteryLevelReader = { systemBatteryLevel(context) }

    private val _uiState = MutableStateFlow(BatteryWidgetUiState())
    val uiState: StateFlow<BatteryWidgetUiState> = _uiState.asStateFlow()

    init {
        // Settings übernehmen.
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.batteryEnabled,
                        showIcon = settings.batteryShowIcon,
                        showPercent = settings.batteryShowPercent,
                        lowThreshold = settings.batteryLowThresholdPercent,
                    )
                }
            }
        }

        // Akku-Level alle 30 Sekunden aktualisieren.
        viewModelScope.launch {
            while (true) {
                val (level, charging) = batteryLevelReader()
                _uiState.update { it.copy(level = level, isCharging = charging) }
                delay(30_000L)
            }
        }
    }

    companion object {
        /** Prüft, ob der Akku unter dem Schwellenwert liegt. */
        fun isLowBattery(level: Int, threshold: Int): Boolean = level <= threshold
    }
}

/**
 * Akku-Anzeige-Widget über der Streaming-Vorschau:
 * Zeigt Batteriestatus mit Icon und Prozentwert.
 */
@Composable
fun BatteryWidget(
    modifier: Modifier = Modifier,
    viewModel: BatteryWidgetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.showIcon) {
            Text(
                text = batteryIcon(uiState.level, uiState.isCharging),
                fontSize = 16.sp,
            )
        }
        if (uiState.showPercent) {
            val color = if (BatteryWidgetViewModel.isLowBattery(uiState.level, uiState.lowThreshold)) {
                Color(0xFFFF5252) // Rot bei Low-Battery
            } else {
                Color.White
            }
            Text(
                text = "${uiState.level}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}
