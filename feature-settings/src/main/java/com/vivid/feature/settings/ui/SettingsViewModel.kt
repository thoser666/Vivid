package com.vivid.feature.settings.ui // In Ihrem neuen Settings-Feature-Modul

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // Wandelt den kalten Flow aus dem Repository in einen heißen StateFlow um,
    // damit die UI den letzten Zustand immer sofort hat.
    val settings: StateFlow<StreamSettings> = settingsRepository.streamSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreamSettings(), // Startwert
        )

    fun saveSettings(url: String, key: String) {
        viewModelScope.launch {
            settingsRepository.updateStreamSettings(url, key)
        }
    }
}
