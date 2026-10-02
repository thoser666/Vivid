package com.vivid.feature.streaming

import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraStreamProfileTest {
    private val requested = ResolvedEncoderConfig(VideoCodecPreference.H265, EncoderPreset.FHD60, false)
    private val encoder = object : EncoderCapabilities {
        override fun supports(mime: String, width: Int, height: Int, fps: Int) = true
    }

    @Test
    fun `1080p60 encoder with 30fps camera selects 1080p30`() {
        val camera = CameraCaptureCapabilities { width, height, fps -> width == 1920 && height == 1080 && fps == 30 }
        val actual = resolveCameraStreamProfile(requested, true, camera, encoder)!!
        assertEquals(EncoderPreset.FHD30, actual.preset)
        assertTrue(actual.fallbackApplied)
    }

    @Test
    fun `selected camera with only 720p30 selects HD30`() {
        val camera = CameraCaptureCapabilities { width, height, fps -> width == 1280 && height == 720 && fps == 30 }
        assertEquals(EncoderPreset.HD30, resolveCameraStreamProfile(requested, true, camera, encoder)?.preset)
    }

    @Test
    fun `supported 60fps profile remains unchanged`() {
        val camera = CameraCaptureCapabilities { _, _, _ -> true }
        assertEquals(requested, resolveCameraStreamProfile(requested, true, camera, encoder))
    }

    @Test
    fun `camera and encoder must support the same combination`() {
        val camera = CameraCaptureCapabilities { width, _, fps -> width == 1920 && fps == 30 }
        val incompatible = object : EncoderCapabilities {
            override fun supports(mime: String, width: Int, height: Int, fps: Int) = width == 1280
        }
        assertNull(resolveCameraStreamProfile(requested, true, camera, incompatible))
    }

    @Test
    fun `strict profile fails instead of silently changing fps`() {
        val camera = CameraCaptureCapabilities { _, _, fps -> fps == 30 }
        assertNull(resolveCameraStreamProfile(requested, false, camera, encoder))
    }

    @Test
    fun `missing camera capabilities never produce an unverified profile`() {
        assertNull(resolveCameraStreamProfile(requested, true, CameraCaptureCapabilities { _, _, _ -> false }, encoder))
    }

    @Test
    fun `camera compatible fallback also checks codec support`() {
        val camera = CameraCaptureCapabilities { _, _, fps -> fps == 30 }
        val avcOnly = object : EncoderCapabilities {
            override fun supports(mime: String, width: Int, height: Int, fps: Int) = mime == "video/avc"
        }
        assertEquals(
            ResolvedEncoderConfig(VideoCodecPreference.H264, EncoderPreset.FHD30, true),
            resolveCameraStreamProfile(requested, true, camera, avcOnly),
        )
    }
}
