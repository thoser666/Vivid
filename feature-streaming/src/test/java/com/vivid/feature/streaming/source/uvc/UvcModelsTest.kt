package com.vivid.feature.streaming.source.uvc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UvcModelsTest {

    @Test
    fun `maps MJPEG and YUY2 types without invented options`() {
        val modes = mapUvcModes(
            listOf(
                UvcFormatInfo(type = 7, width = 1280, height = 720, fps = 30),
                UvcFormatInfo(type = 5, width = 640, height = 480, fps = 30),
                UvcFormatInfo(type = 99, width = 1920, height = 1080, fps = 60),
                UvcFormatInfo(type = 7, width = 0, height = 720, fps = 30),
                UvcFormatInfo(type = 7, width = 640, height = 480, fps = 0),
            ),
        )

        assertEquals(
            listOf(
                UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
                UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2),
            ),
            modes,
        )
    }

    @Test
    fun `Size uses descriptor subtypes rather than native frame format values`() {
        // UVCAndroid 1.0.13 Size.type uses UVC_VS_FRAME_*; native FRAME_FORMAT_* is 0/1.
        assertEquals(7, UvcFormatInfo.TYPE_MJPEG)
        assertEquals(5, UvcFormatInfo.TYPE_YUY2)
    }

    @Test
    fun `default prefers MJPEG 720p`() {
        val modes = mapUvcModes(
            listOf(
                UvcFormatInfo(type = 5, width = 1920, height = 1080, fps = 30),
                UvcFormatInfo(type = 7, width = 640, height = 480, fps = 30),
                UvcFormatInfo(type = 7, width = 1280, height = 720, fps = 30),
            ),
        )

        assertEquals(
            UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG),
            selectDefaultUvcMode(modes),
        )
    }

    @Test
    fun `default falls back to YUY2 without MJPEG`() {
        val modes = mapUvcModes(
            listOf(
                UvcFormatInfo(type = 5, width = 640, height = 480, fps = 30),
                UvcFormatInfo(type = 5, width = 320, height = 240, fps = 30),
            ),
        )

        assertEquals(
            UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2),
            selectDefaultUvcMode(modes),
        )
    }

    @Test
    fun `default is null without modes`() {
        assertNull(selectDefaultUvcMode(emptyList()))
        assertEquals(emptyList<UvcCaptureMode>(), mapUvcModes(emptyList()))
    }

    @Test
    fun `successful open may use only its advertised request when helper omits preview size`() {
        val requested = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        assertEquals(requested, resolveOpenedUvcMode(null, requested, listOf(requested)))
        assertNull(resolveOpenedUvcMode(null, requested, emptyList()))
    }

    @Test
    fun `reported negotiated mode is never rewritten to the requested mode`() {
        val requested = UvcCaptureMode(640, 480, 30, UvcPixelFormat.YUY2)
        val reported = UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG)
        assertEquals(reported, resolveOpenedUvcMode(reported, requested, listOf(requested)))
    }

    @Test
    fun `preference roundtrip keeps mode without device paths`() {
        val mode = UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG)

        assertEquals("1280x720@30:MJPEG", mode.preferenceString())
        assertEquals(mode, UvcCaptureMode.parsePreference(mode.preferenceString()))
    }

    @Test
    fun `preference parsing rejects garbage`() {
        assertNull(UvcCaptureMode.parsePreference(null))
        assertNull(UvcCaptureMode.parsePreference(""))
        assertNull(UvcCaptureMode.parsePreference("1280x720@30"))
        assertNull(UvcCaptureMode.parsePreference("1280x720@30:H264"))
        assertNull(UvcCaptureMode.parsePreference("0x720@30:MJPEG"))
        assertNull(UvcCaptureMode.parsePreference("/dev/bus/usb/001/002"))
    }

    @Test
    fun `device preference key carries no serial`() {
        val descriptor = UvcDeviceDescriptor(
            stableSessionId = "046d:0825#0",
            vendorId = 0x046d,
            productId = 0x0825,
            productLabel = "Webcam",
        )

        assertEquals("046d:0825", descriptor.preferenceKey())
    }
}
