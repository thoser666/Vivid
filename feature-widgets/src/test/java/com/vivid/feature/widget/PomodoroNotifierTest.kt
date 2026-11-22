package com.vivid.feature.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 25, 26, 34])
class PomodoroNotifierTest {
    @Suppress("DEPRECATION")
    @Test fun `completion uses the vibration API supported by the device`() {
        val context = mockk<Context>()
        val vibrator = mockk<Vibrator>(relaxed = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = mockk<VibratorManager>()
            every { context.getSystemService(VibratorManager::class.java) } returns manager
            every { manager.defaultVibrator } returns vibrator
        } else {
            every { context.getSystemService(Context.VIBRATOR_SERVICE) } returns vibrator
        }
        PomodoroNotifier(context).phaseCompleted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(exactly = 1) { vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)) }
            verify(exactly = 0) { vibrator.vibrate(any<Long>()) }
        } else {
            verify(exactly = 1) { vibrator.vibrate(400L) }
        }
    }
}
