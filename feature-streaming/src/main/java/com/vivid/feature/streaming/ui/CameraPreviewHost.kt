package com.vivid.feature.streaming.ui

import android.annotation.SuppressLint
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind

/**
 * Hosts the composed camera/encoder preview surface. Both the streaming screen
 * and the scene editor use this host so preview attachment and touch gestures
 * stay in one place.
 */
@Composable
internal fun CameraPreviewHost(
    streamingEngine: StreamingEngine,
    activeSourceKind: VideoSourceKind,
    previewSize: IntSize,
    displayRotationDegrees: Int,
) {
    // Screenshot-/UI-Tests setzen ein Standbild (die Emulator-Kamera liefert nur
    // ein farbiges Testmuster, siehe PreviewFrameOverride). Ist eines gesetzt,
    // entfaellt die komplette Kamera-Anbindung — der Play-Screenshot zeigt dann
    // eine Szene statt eines Testbilds. Produktion: null, unveraendert.
    val stillFrame = PreviewFrameOverride.frame
    if (stillFrame != null) {
        Image(
            bitmap = stillFrame.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    // Every source previews the same composed GL output that reaches the encoder.
    AndroidView(
        factory = { context ->
            @SuppressLint("ClickableViewAccessibility")
            SurfaceView(context).also { view ->
                streamingEngine.useSceneBackedIdlePreview = true
                if (activeSourceKind == VideoSourceKind.CAMERA) streamingEngine.initializeCamera()
                view.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            streamingEngine.attachPreview(
                                holder.surface,
                                view.width,
                                view.height,
                                (view.display?.rotation ?: Surface.ROTATION_0) * 90,
                            )
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            streamingEngine.attachPreview(
                                holder.surface, width, height,
                                (view.display?.rotation ?: Surface.ROTATION_0) * 90,
                            )
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            streamingEngine.detachPreview(holder.surface)
                        }
                    },
                )
                // Tap-to-Focus (Tipp), Pinch-Zoom und Zoom-Reset (Doppeltipp)
                // auf der Kamera-Vorschau (RootEncoder-Kamera, nicht CameraX).
                val gestures = StreamingPreviewGestures(
                    context = context,
                    onTapToFocus = { v, e -> streamingEngine.tapToFocus(v, e) },
                    onZoomScale = { scale -> streamingEngine.zoomBy(scale) },
                    onDoubleTap = { streamingEngine.resetZoom() },
                )
                view.setOnTouchListener { target, event ->
                    streamingEngine.dispatchSceneTouch(target.width, target.height, event) ||
                        gestures.onTouch.onTouch(target, event)
                }
            }
        },
        update = { view ->
            if (activeSourceKind == VideoSourceKind.CAMERA) streamingEngine.initializeCamera()
            // GL crops the native camera buffer to this viewport without stretching.
            view.holder.setFixedSize(previewSize.width, previewSize.height)
            view.scaleX = 1f
            view.scaleY = 1f
            val surface = view.holder.surface
            if (surface?.isValid == true) {
                streamingEngine.attachPreview(
                    surface, previewSize.width, previewSize.height,
                    displayRotationDegrees,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
