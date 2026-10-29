package com.vivid.feature.streaming.scene

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLDisplay
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.MotionEvent
import android.graphics.Matrix
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vivid.feature.streaming.ui.SceneWidgetsOverlay
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Attached offscreen UI with an encoder-owned lifecycle, including WebView and Compose animations. */
@Singleton
class SceneLayerHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factory: SceneWidgetViewModelFactory,
) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = mutableMapOf<SceneLayerFilterRender, Session>()

    fun createFilter(hidden: () -> Boolean, mirrored: () -> Boolean, useLayoutBlackout: () -> Boolean = { true }): SceneLayerFilterRender = SceneLayerFilterRender(hidden, mirrored,
        onReady = { filter, surface, width, height -> main.post {
            if (!filter.released) {
                runCatching { Session(filter, surface, width, height).also { sessions[filter] = it }.start() }
                    .onFailure { sessions.remove(filter)?.close(); Timber.e(it, "Scene surface could not start") }
            }
        } },
        onReleased = { filter -> main.post { sessions.remove(filter)?.close() } },
        useLayoutBlackout = useLayoutBlackout,
    )

    /** Hardware Canvas owns an HWUI EGL context. Retire it before RootEncoder terminates EGL. */
    fun releaseOutputSurfaces() {
        fun closeSessions() {
            val previous = sessions.values.toList()
            sessions.clear()
            previous.forEach { it.close() }
        }
        if (Looper.myLooper() == main.looper) closeSessions() else {
            val done = java.util.concurrent.CountDownLatch(1)
            var failure: Throwable? = null
            main.post {
                try { closeSessions() } catch (error: Throwable) { failure = error }
                finally { done.countDown() }
            }
            check(done.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Scene surface shutdown timed out" }
            failure?.let { throw it }
        }
    }

    /** Preview shows the complete frame, so touches follow the same letterbox geometry. */
    fun dispatchTouch(previewWidth: Int, previewHeight: Int, event: MotionEvent): Boolean =
        sessions.values.lastOrNull()?.touch(previewWidth, previewHeight, event) ?: false

    private inner class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner,
        HasDefaultViewModelProviderFactory {
        override val lifecycle = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val savedState = SavedStateRegistryController.create(this)
        override val savedStateRegistry get() = savedState.savedStateRegistry
        override val defaultViewModelProviderFactory get() = factory
        init {
            savedState.performAttach()
            savedState.performRestore(null)
            lifecycle.currentState = Lifecycle.State.RESUMED
        }
        fun close() {
            lifecycle.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private inner class Session(
        private val filter: SceneLayerFilterRender,
        private val target: Surface,
        private val width: Int,
        private val height: Int,
    ) {
        private val owner = Owner()
        private var reader: ImageReader? = null
        private var display: VirtualDisplay? = null
        private var presentation: Presentation? = null
        private var view: ComposeView? = null
        private var dirty = true
        private var closed = false
        private val draw = Runnable { drawFrame() }

        private fun drawFrame() {
            if (closed || filter.released) return
            val root = view
            if (dirty && root != null && root.width > 0 && root.height > 0) {
                dirty = false
                synchronized(filter) {
                    if (!filter.released && target.isValid) {
                        runCatching {
                            val canvas = target.lockHardwareCanvas()
                            try {
                                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                root.draw(canvas)
                            } finally { target.unlockCanvasAndPost(canvas) }
                        }.onFailure { Timber.w(it, "Scene surface frame skipped") }
                    }
                }
            }
            // Scene updates are capped at 30 Hz; the encoder independently retains 30/60 FPS.
            main.postDelayed(draw, 33)
        }

        fun start() {
            retainSceneDisplayForProcess()
            val sink = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = sink
            sink.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, main)
            val manager = context.getSystemService(DisplayManager::class.java)
            val virtual = requireNotNull(manager.createVirtualDisplay("Vivid scene", width, height, 160,
                sink.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION))
            display = virtual
            val window = Presentation(context, virtual.display)
            presentation = window
            window.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            val content = ComposeView(window.context)
            view = content
            content.setViewTreeLifecycleOwner(owner)
            content.setViewTreeViewModelStoreOwner(owner)
            content.setViewTreeSavedStateRegistryOwner(owner)
            content.setContent {
                // A stable 360-dp short side makes saved placement independent of phone density.
                CompositionLocalProvider(LocalDensity provides Density(minOf(width, height) / 360f, 1f)) {
                    MaterialTheme { SceneWidgetsOverlay(onStateLoaded = { filter.sceneEmptyFromLayout = it.sourceEmpty }) }
                }
            }
            content.viewTreeObserver.addOnDrawListener { dirty = true }
            window.setContentView(content, ViewGroup.LayoutParams(width, height))
            window.show()
            window.window?.setLayout(width, height)
            main.post(draw)
        }

        fun touch(previewWidth: Int, previewHeight: Int, event: MotionEvent): Boolean {
            val content = view ?: return false
            if (previewWidth <= 0 || previewHeight <= 0) return false
            val scale = minOf(previewWidth.toFloat() / width, previewHeight.toFloat() / height)
            val mapped = MotionEvent.obtain(event)
            val matrix = Matrix().apply {
                setTranslate(-(previewWidth - width * scale) / 2f, -(previewHeight - height * scale) / 2f)
                postScale(1f / scale, 1f / scale)
            }
            mapped.transform(matrix)
            return try { content.dispatchTouchEvent(mapped) } finally { mapped.recycle() }
        }

        fun close() {
            if (closed) return
            closed = true
            main.removeCallbacks(draw)
            // Surface.release destroys lockHardwareCanvas's HWUI renderer synchronously.
            // Releasing it after EGL termination can abort Samsung's RenderThread.
            target.release()
            view?.disposeComposition()
            presentation?.dismiss()
            owner.close()
            display?.release()
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
            view = null
            presentation = null
            display = null
            reader = null
        }
    }
}

/**
 * HWUI caches its OpenGL context for the process, beyond individual Canvas surfaces.
 * RootEncoder terminates its EGLDisplay on every stop/detach. Android reference-counts
 * eglInitialize/eglTerminate, so retain one display reference for HWUI's lifetime.
 * This owns no surface/context; per-session GPU resources are still released normally.
 * The process releases this final display reference on exit, together with HWUI.
 */
private var sceneProcessDisplay: EGLDisplay? = null

@Synchronized
private fun retainSceneDisplayForProcess() {
    if (sceneProcessDisplay != null) return
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, null, 0, null, 0)) {
        "Cannot retain EGL display for scene rendering"
    }
    sceneProcessDisplay = display
}
