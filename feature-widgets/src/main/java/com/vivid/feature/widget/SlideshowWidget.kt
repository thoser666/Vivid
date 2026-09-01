package com.vivid.feature.widget

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.widgets.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reine Slideshow-Zustandslogik, bewusst ohne Android-/Coil-Abhängigkeit. */
object SlideshowController {
    fun parseUris(raw: String): List<String> = raw
        .split('\n', ';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun nextIndex(current: Int, itemCount: Int): Int =
        if (itemCount <= 0) 0 else (current + 1).mod(itemCount)

    fun intervalSeconds(value: Int): Long = value.coerceIn(5, 3600).toLong()
}

data class SlideshowWidgetUiState(
    val enabled: Boolean = false,
    val imageUris: List<String> = emptyList(),
    val intervalSeconds: Int = 30,
    val sizeDp: Int = 240,
    val opacity: Float = 1f,
    val currentIndex: Int = 0,
)

@HiltViewModel
class SlideshowWidgetViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SlideshowWidgetUiState())
    val uiState: StateFlow<SlideshowWidgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.slideshowWidgetEnabled,
                        imageUris = SlideshowController.parseUris(settings.slideshowWidgetUris),
                        intervalSeconds = settings.slideshowWidgetIntervalSeconds,
                        sizeDp = settings.slideshowWidgetSizeDp,
                        opacity = settings.slideshowWidgetOpacity,
                        currentIndex = 0,
                    )
                }
            }
        }
    }

    fun advance() {
        _uiState.update { state ->
            state.copy(currentIndex = SlideshowController.nextIndex(state.currentIndex, state.imageUris.size))
        }
    }
}

@Composable
fun SlideshowWidget(
    modifier: Modifier = Modifier,
    viewModel: SlideshowWidgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.enabled || state.imageUris.isEmpty()) return

    val currentUri = state.imageUris[state.currentIndex.coerceIn(0, state.imageUris.lastIndex)]
    LaunchedEffect(state.intervalSeconds, state.imageUris) {
        while (true) {
            delay(SlideshowController.intervalSeconds(state.intervalSeconds) * 1_000L)
            viewModel.advance()
        }
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(currentUri).crossfade(true).build(),
        contentDescription = stringResource(R.string.slideshow_widget_description),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(state.sizeDp.dp).alpha(state.opacity),
    )
}
