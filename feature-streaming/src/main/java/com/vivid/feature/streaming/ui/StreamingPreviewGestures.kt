package com.vivid.feature.streaming.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Verdrahtet die Touch-Gesten der Kamera-Vorschau mit der Streaming-Engine:
 * - Einfacher Tipp → Tap-to-Focus
 * - Doppeltipp → Zoom zurücksetzen
 * - Pinch → Zoom (ScaleGestureDetector)
 *
 * Die eigentliche Zoom-/Fokus-Logik liegt in der Engine bzw. in
 * [com.vivid.feature.streaming.ZoomCalculator] — diese Klasse ist nur die
 * dünne Android-Gesten-Schicht.
 */
class StreamingPreviewGestures(
    context: Context,
    private val onTapToFocus: (view: View, event: MotionEvent) -> Unit,
    private val onZoomScale: (scaleFactor: Float) -> Unit,
    private val onDoubleTap: () -> Unit,
) {
    private var touchedView: View? = null

    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                touchedView?.let { onTapToFocus(it, event) }
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                onDoubleTap()
                return true
            }
        },
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onZoomScale(detector.scaleFactor)
                return true
            }
        },
    )

    /** An den Preview ([View]) zu hängender Touch-Listener. */
    val onTouch = View.OnTouchListener { view, event ->
        touchedView = view
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            view.performClick()
        }
        true
    }
}
