package com.vivid.feature.streaming

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidCameraCaptureCapabilitiesTest {
    private fun camera(durationNs: Long, ranges: Array<Range<Int>> = arrayOf(Range(30, 60))): AndroidCameraCaptureCapabilities {
        val map = mockk<StreamConfigurationMap>()
        every { map.getOutputSizes(SurfaceTexture::class.java) } returns arrayOf(Size(1920, 1080))
        every { map.getOutputMinFrameDuration(SurfaceTexture::class.java, any()) } returns durationNs
        val characteristics = mockk<CameraCharacteristics>()
        every { characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) } returns map
        every { characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) } returns ranges
        return AndroidCameraCaptureCapabilities(characteristics)
    }

    @Test
    fun frameDurationLimitsFpsForThisResolution() {
        val camera = camera(33_333_333L)
        assertFalse(camera.supports(1920, 1080, 60))
        assertTrue(camera.supports(1920, 1080, 30))
        assertFalse(camera.supports(1280, 720, 30))
    }

    @Test
    fun sixtyFpsRequiresBothFrameDurationAndAeRange() {
        assertTrue(camera(16_666_667L).supports(1920, 1080, 60))
        assertFalse(camera(16_666_667L, arrayOf(Range(15, 30))).supports(1920, 1080, 60))
    }

    @Test
    fun unknownCharacteristicsAndDurationAreNotUnlimitedSupport() {
        assertFalse(AndroidCameraCaptureCapabilities(null).supports(1920, 1080, 30))
        assertFalse(camera(0L).supports(1920, 1080, 60))
    }
}
