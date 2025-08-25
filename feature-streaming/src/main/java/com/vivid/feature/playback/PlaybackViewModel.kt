package com.vivid.feature.playback

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlaybackViewModel @Inject constructor() : ViewModel() {

    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl = _currentStreamUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun setStreamUrl(url: String) {
        _currentStreamUrl.value = url
    }

    fun togglePlayback() {
        _isPlaying.value = !_isPlaying.value
    }
}
