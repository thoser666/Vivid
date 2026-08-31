package com.vivid.feature.widget

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.widgets.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Erstellt QR-Codes ohne Netzwerkzugriff aus einem Link oder beliebigem Text. */
object QrCodeGenerator {
    private const val DEFAULT_SIZE = 180

    /** Liefert die QR-Matrix; leerer Inhalt ist absichtlich ungültig. */
    fun generateMatrix(content: String): BitMatrix {
        require(content.isNotBlank()) { "QR-Code content must not be blank" }
        return MultiFormatWriter().encode(
            content.trim(),
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
    }

    /** Rendert die Matrix als scharfes, quadratisches Schwarz-Weiß-Bitmap. */
    fun generateBitmap(content: String, size: Int = DEFAULT_SIZE): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val matrix = generateMatrix(content)
            val outputSize = outputSize(size)
            val bitmap = createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(outputSize * outputSize)
            for (y in 0 until outputSize) {
                val sourceY = y * matrix.height / outputSize
                for (x in 0 until outputSize) {
                    val sourceX = x * matrix.width / outputSize
                    pixels[y * outputSize + x] = if (matrix[sourceX, sourceY]) {
                        0xFF000000.toInt()
                    } else {
                        0xFFFFFFFF.toInt()
                    }
                }
            }
            bitmap.setPixels(pixels, 0, outputSize, 0, 0, outputSize, outputSize)
            bitmap
        }.getOrNull()
    }

    /** Begrenzt die Ausgabegröße auf einen für Overlay-Bitmaps sinnvollen Bereich. */
    fun outputSize(size: Int): Int = size.coerceIn(120, 600)
}

/** UI-Zustand des QR-Code-Widgets. */
data class QrCodeWidgetUiState(
    val enabled: Boolean = false,
    val content: String = "",
    val sizeDp: Int = 180,
    val opacity: Float = 0.95f,
)

@HiltViewModel
class QrCodeWidgetViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QrCodeWidgetUiState())
    val uiState: StateFlow<QrCodeWidgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        enabled = settings.qrCodeWidgetEnabled,
                        content = settings.qrCodeWidgetContent,
                        sizeDp = settings.qrCodeWidgetSizeDp,
                        opacity = settings.qrCodeWidgetOpacity,
                    )
                }
            }
        }
    }
}

/**
 * QR-Code-Overlay für Spenden-, Social- oder beliebige Web-Links.
 * Der Code wird lokal generiert; die Eingabe verlässt das Gerät nicht.
 */
@Composable
fun QrCodeWidget(
    modifier: Modifier = Modifier,
    viewModel: QrCodeWidgetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled || uiState.content.isBlank()) return

    val bitmap = remember(uiState.content, uiState.sizeDp) {
        QrCodeGenerator.generateBitmap(uiState.content, uiState.sizeDp)
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.qr_code_widget_description),
        modifier = modifier
            .size(uiState.sizeDp.dp)
            .alpha(uiState.opacity),
    )
}
