package com.vivid.irlbroadcaster.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.update.UpdateCheckResult
import com.vivid.BuildConfig
import com.vivid.core.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val checking: Boolean = false,
    val result: UpdateCheckResult? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    /** Installierte Version aus der BuildConfig (gesetzt von der CI-Pipeline via -PversionName/-PversionCode). */
    val installedVersionName: String = BuildConfig.VERSION_NAME
    val installedVersionCode: Int = BuildConfig.VERSION_CODE

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value.checking) return
        viewModelScope.launch {
            _uiState.value = AboutUiState(checking = true)
            // Manueller Check: immer frisch (forceRefresh) — umgeht den DataStore-Cache
            // und aktualisiert ihn nebenbei für den Settings-Badge.
            val result = updateChecker.check(installedVersionName, forceRefresh = true)
            _uiState.value = AboutUiState(checking = false, result = result)
        }
    }
}
