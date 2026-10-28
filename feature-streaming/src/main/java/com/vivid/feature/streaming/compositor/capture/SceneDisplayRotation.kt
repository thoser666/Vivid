package com.vivid.feature.streaming.compositor.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import android.view.Surface
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Cache display rotation on change events; no display/binder query is needed per video frame. */
internal class SceneDisplayRotation(context: Context) : Closeable {
    private val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val closed = AtomicBoolean(false)
    @Volatile var degrees: Int = 0
        private set
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = update(displayId)
        override fun onDisplayRemoved(displayId: Int) = update(displayId)
        override fun onDisplayChanged(displayId: Int) = update(displayId)
    }

    init {
        manager.registerDisplayListener(listener, Handler(context.mainLooper))
        degrees = readDegrees()
    }

    private fun update(displayId: Int) {
        if (!closed.get() && displayId == Display.DEFAULT_DISPLAY) degrees = readDegrees()
    }

    private fun readDegrees(): Int = when (manager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) manager.unregisterDisplayListener(listener)
    }
}
