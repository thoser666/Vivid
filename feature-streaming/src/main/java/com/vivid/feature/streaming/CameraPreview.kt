package com.vivid.feature.streaming

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    preview: Preview,
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Hier können Skalierungstyp etc. konfiguriert werden
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            // Die PreviewView wird mit dem Preview Use Case von CameraX verbunden
            preview.setSurfaceProvider(previewView.surfaceProvider)
        },
    )
}
