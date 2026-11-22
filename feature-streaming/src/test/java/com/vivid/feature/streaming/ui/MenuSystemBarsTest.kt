package com.vivid.feature.streaming.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28, 30, 34])
class MenuSystemBarsTest {
    @Suppress("DEPRECATION")
    @Test fun `menus restore system bars through the controller supported by the window`() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Popups need their own controller even when no Activity window is available.
            val view = mockk<View>()
            val controller = mockk<WindowInsetsController>(relaxed = true)
            every { view.windowInsetsController } returns controller
            showMenuSystemBars(view)
            verify(exactly = 1) { controller.systemBarsBehavior = 1 }
            verify(exactly = 1) { controller.show(WindowInsets.Type.systemBars()) }
        } else {
            val activity = Robolectric.buildActivity(Activity::class.java).setup()
            try {
                val window = activity.get().window
                val view = window.decorView
                view.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                showLegacyMenuSystemBars(view, window)
                val hidden = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                assertEquals(0, view.systemUiVisibility and hidden)
                assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } finally { activity.pause().stop().destroy() }
        }
    }
}
