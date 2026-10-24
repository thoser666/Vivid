package com.vivid.feature.streaming.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.data.*
import com.vivid.feature.chat.ui.ChatOverlay
import com.vivid.feature.chat.ui.ChatOverlayViewModel
import com.vivid.feature.widget.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class SceneWidgetsOverlayViewModel @Inject constructor(repository: SceneRepository) : ViewModel() {
    val state = combine(repository.scenesFlow, repository.widgetsFlow, repository.activeSceneIdFlow,
        repository.libraryInitializedFlow) { scenes, widgets, activeId, initialized ->
        val scene = scenes.find { it.id == activeId && it.enabled }
        SceneOverlayState(initialized, scene?.widgets.orEmpty().mapNotNull { placement ->
            widgets.find { it.id == placement.widgetId && it.enabled && placement.enabled }?.let { placement to it }
        })
    }
}

data class SceneOverlayState(val initialized: Boolean = false, val widgets: List<Pair<SceneWidget, StreamWidget>> = emptyList())

@Composable
internal fun SceneWidgetsOverlay(viewModel: SceneWidgetsOverlayViewModel = hiltViewModel()) {
    val loaded by viewModel.state.collectAsStateWithLifecycle<SceneOverlayState?>(null)
    val state = loaded ?: return
    if (!state.initialized) {
        LegacyWidgetsOverlay()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val canvasWidth = constraints.maxWidth
        val canvasHeight = constraints.maxHeight
        // Several subtitle placements display the same microphone transcription.
        // A single recognizer avoids competing Android recognition sessions.
        val subtitleId = state.widgets.firstOrNull { it.second.type == WidgetType.SUBTITLES }?.second?.id
        val subtitles = if (subtitleId != null) activeWidgetViewModel<SubtitleWidgetViewModel>(subtitleId, "scene-subtitles") {
            vm, id -> vm.useWidget(id)
        } else null
        state.widgets.forEach { (placement, widget) ->
            key(widget.id) {
                var contentSize by remember { mutableStateOf(IntSize.Zero) }
                Box(Modifier.offset {
                    IntOffset(((canvasWidth - contentSize.width * placement.scale).coerceAtLeast(0f) * placement.x / 100f).roundToInt(),
                        ((canvasHeight - contentSize.height * placement.scale).coerceAtLeast(0f) * placement.y / 100f).roundToInt())
                }.onSizeChanged { contentSize = it }
                    .graphicsLayer {
                        scaleX = placement.scale
                        scaleY = placement.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }) {
                    WidgetContent(widget, subtitles)
                }
            }
        }
    }
}

/** ViewModels belong to the navigation entry; retire their runtime when a widget leaves composition. */
@Composable
private inline fun <reified VM : ViewModel> activeWidgetViewModel(
    id: String?, storeKey: String? = id, crossinline select: (VM, String?) -> Unit,
): VM {
    val vm: VM = hiltViewModel(key = storeKey)
    select(vm, id)
    DisposableEffect(vm) { onDispose { select(vm, "__inactive__") } }
    return vm
}

@Composable
private fun WidgetContent(widget: StreamWidget, subtitles: SubtitleWidgetViewModel?) {
    when (widget.type) {
        WidgetType.TEXT -> TextInfoWidget(viewModel = activeWidgetViewModel<TextInfoWidgetViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.IMAGE -> ImageWidget(viewModel = activeWidgetViewModel<ImageWidgetViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.QR_CODE -> QrCodeWidget(viewModel = activeWidgetViewModel<QrCodeWidgetViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.SLIDESHOW -> SlideshowWidget(viewModel = activeWidgetViewModel<SlideshowWidgetViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.GRID -> GridOverlay(viewModel = activeWidgetViewModel<GridOverlayViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.BATTERY -> BatteryWidget(viewModel = activeWidgetViewModel<BatteryWidgetViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.SUBTITLES -> SubtitlesOverlay(viewModel = requireNotNull(subtitles))
        WidgetType.CHAT -> ChatOverlay(viewModel = activeWidgetViewModel<ChatOverlayViewModel>(widget.id) { vm, id -> vm.useWidget(id) })
        WidgetType.BROWSER -> BrowserWidget(widget.configuration)
        WidgetType.POMODORO -> PomodoroWidget(widget)
    }
}

/** Browser sources use a page viewport in physical pixels, independent of scene scale. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWidget(configuration: WidgetConfiguration) {
    val url = configuration.browserUrl
    if (!isBrowserWidgetUrl(url)) return
    val density = LocalDensity.current
    AndroidView(
        factory = { context -> WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !isBrowserWidgetUrl(request.url.toString())
            }
        } },
        modifier = Modifier.requiredSize(with(density) { configuration.browserWidth.coerceIn(1, 4096).toDp() },
            with(density) { configuration.browserHeight.coerceIn(1, 4096).toDp() }),
        update = { if (it.tag != url) { it.tag = url; it.loadUrl(url) } },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

@Composable
private fun LegacyWidgetsOverlay() {
    Box(Modifier.fillMaxSize()) {
        ChatOverlay(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 84.dp), viewModel = activeWidgetViewModel<ChatOverlayViewModel>(null) { vm, id -> vm.useWidget(id) })
        TextInfoWidget(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 84.dp), viewModel = activeWidgetViewModel<TextInfoWidgetViewModel>(null) { vm, id -> vm.useWidget(id) })
        GridOverlay(viewModel = activeWidgetViewModel<GridOverlayViewModel>(null) { vm, id -> vm.useWidget(id) })
        ImageWidget(Modifier.align(Alignment.TopStart).padding(12.dp), viewModel = activeWidgetViewModel<ImageWidgetViewModel>(null) { vm, id -> vm.useWidget(id) })
        QrCodeWidget(Modifier.align(Alignment.TopEnd).padding(12.dp), viewModel = activeWidgetViewModel<QrCodeWidgetViewModel>(null) { vm, id -> vm.useWidget(id) })
        SlideshowWidget(Modifier.align(Alignment.Center).padding(12.dp), viewModel = activeWidgetViewModel<SlideshowWidgetViewModel>(null) { vm, id -> vm.useWidget(id) })
        SubtitlesOverlay(Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp), viewModel = activeWidgetViewModel<SubtitleWidgetViewModel>(null) { vm, id -> vm.useWidget(id) })
    }
}
