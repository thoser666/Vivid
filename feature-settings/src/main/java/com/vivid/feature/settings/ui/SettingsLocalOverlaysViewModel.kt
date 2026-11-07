package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.LocalOverlaySettings
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsLocalOverlaysViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.appSettingsFlow.map { it.localOverlays }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (LocalOverlaySettings) -> LocalOverlaySettings) {
        if (settings.value == null) return
        viewModelScope.launch { repository.updateLocalOverlays(change) }
    }
}
