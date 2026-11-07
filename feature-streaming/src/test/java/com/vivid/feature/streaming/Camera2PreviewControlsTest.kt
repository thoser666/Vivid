package com.vivid.feature.streaming

import android.hardware.camera2.CameraMetadata
import com.pedro.encoder.input.video.Camera2ApiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Camera2PreviewControlsTest {
    private val camera = mockk<Camera2ApiManager>(relaxed = true)
    private val controls = Camera2PreviewControls(camera)

    @Test
    fun `cinematic selects EIS and standard selects OIS without combining request modes`() {
        val info = mockk<android.hardware.camera2.CameraCharacteristics>()
        every { camera.cameraCharacteristics } returns info
        every { info.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) } returns intArrayOf(0, 1)
        every { info.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) } returns intArrayOf(0, 1)
        every { camera.enableVideoStabilization() } returns true
        every { camera.enableOpticalVideoStabilization() } returns true
        assertTrue(controls.setStabilizationMode(com.vivid.core.data.StabilizationMode.CINEMATIC))
        io.mockk.verifyOrder { camera.disableOpticalVideoStabilization(); camera.enableVideoStabilization() }
        assertTrue(controls.setStabilizationMode(com.vivid.core.data.StabilizationMode.STANDARD))
        io.mockk.verifyOrder { camera.disableVideoStabilization(); camera.enableOpticalVideoStabilization() }
        every { info.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) } returns intArrayOf(0)
        assertFalse(controls.setStabilizationMode(com.vivid.core.data.StabilizationMode.CINEMATIC))
        verify(exactly = 1) { camera.enableVideoStabilization() }
    }

    @Test
    fun `torch success requires the camera state to change`() {
        every { camera.isLanternEnabled } returns false
        assertFalse(controls.enableTorch())
        verify { camera.enableLantern() }

        every { camera.isLanternEnabled } returns true
        assertTrue(controls.enableTorch())
        assertFalse(controls.disableTorch())
        verify { camera.disableLantern() }
    }

    @Test
    fun `stabilization clears both request modes even when cached flags are off`() {
        every { camera.isVideoStabilizationEnabled } returns false
        every { camera.isOpticalStabilizationEnabled } returns false

        assertTrue(controls.disableStabilization())
        verify(exactly = 1) { camera.disableVideoStabilization() }
        verify(exactly = 1) { camera.disableOpticalVideoStabilization() }
    }

    @Test
    fun `automatic white balance selects AUTO instead of the first preset`() {
        every { camera.getAutoWhiteBalanceModesAvailable() } returns listOf(
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
            CameraMetadata.CONTROL_AWB_MODE_AUTO,
        )
        every { camera.enableAutoWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_AUTO) } returns true

        assertTrue(controls.enableAutoWhiteBalance())
        verify(exactly = 0) { camera.enableAutoWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT) }
    }

    @Test
    fun `white balance without AUTO support does not send a command`() {
        every { camera.getAutoWhiteBalanceModesAvailable() } returns listOf(CameraMetadata.CONTROL_AWB_MODE_OFF)

        assertFalse(controls.hasWhiteBalanceControl())
        assertFalse(controls.enableAutoWhiteBalance())
        verify(exactly = 0) { camera.enableAutoWhiteBalance(any()) }
    }

    @Test
    fun `automatic mode locks verify the resulting hardware flags`() {
        every { camera.isAutoExposureEnabled } returns true
        every { camera.isAutoWhiteBalanceEnabled } returns true
        assertFalse(controls.disableAutoExposure())
        assertFalse(controls.disableAutoWhiteBalance())

        every { camera.isAutoExposureEnabled } returns false
        every { camera.isAutoWhiteBalanceEnabled } returns false
        assertTrue(controls.disableAutoExposure())
        assertTrue(controls.disableAutoWhiteBalance())
    }

    @Test
    fun `camera command failures are reported as false`() {
        every { camera.disableLantern() } throws IllegalStateException("Camera closed")
        every { camera.enableAutoExposure() } throws IllegalStateException("Camera closed")

        assertFalse(controls.disableTorch())
        assertFalse(controls.enableAutoExposure())
    }
}
