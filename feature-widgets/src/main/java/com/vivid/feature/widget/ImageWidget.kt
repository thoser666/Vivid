package com.vivid.feature.widget

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import com.vivid.feature.widgets.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-Zustand des Bild-Widgets. */
data class ImageWidgetUiState(
    val enabled: Boolean = false,
    val uri: String = "",
    val sizeDp: Int = 100,
    val opacity: Float = 0.8f,
)

@HiltViewModel
class ImageWidgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageWidgetUiState())
    val uiState: StateFlow<ImageWidgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.imageWidgetEnabled,
                        uri = settings.imageWidgetUri,
                        sizeDp = settings.imageWidgetSizeDp,
                        opacity = settings.imageWidgetOpacity,
                    )
                }
            }
        }
    }
}

/**
 * Bild-Widget über der Streaming-Vorschau:
 * Zeigt ein Logo oder Wasserzeichen mit konfigurierbarer Größe und Transparenz.
 */
@Composable
fun ImageWidget(
    modifier: Modifier = Modifier,
    viewModel: ImageWidgetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled || uiState.uri.isBlank()) return

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(Uri.parse(uiState.uri))
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.image_widget_description),
        modifier = modifier
            .size(uiState.sizeDp.dp)
            .alpha(uiState.opacity),
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
    )
}
