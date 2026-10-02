package com.vivid.feature.streaming

import android.media.MediaCodec
import android.media.MediaFormat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import android.media.MediaCodecInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncoderBitrateDiagnosticsTest {
    private fun codec(mode: Int?, cbr: Boolean): MediaCodec {
        val codec = mockk<MediaCodec>()
        val format = mockk<MediaFormat>()
        val info = mockk<MediaCodecInfo>()
        val caps = mockk<MediaCodecInfo.CodecCapabilities>()
        val encoderCaps = mockk<MediaCodecInfo.EncoderCapabilities>()
        every { codec.name } returns "actual.selected.encoder"
        every { codec.outputFormat } returns format
        every { codec.codecInfo } returns info
        every { format.getString(MediaFormat.KEY_MIME) } returns "video/avc"
        every { format.containsKey(MediaFormat.KEY_BITRATE_MODE) } returns (mode != null)
        if (mode != null) every { format.getInteger(MediaFormat.KEY_BITRATE_MODE) } returns mode
        every { info.getCapabilitiesForType("video/avc") } returns caps
        every { caps.encoderCapabilities } returns encoderCaps
        every { encoderCaps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR) } returns cbr
        return codec
    }

    @Test
    fun `CBR is verified against the actual selected encoder`() {
        val diagnostic = RootEncoderBitrateDiagnostics.read(codec(2, true), "video/avc", 6_000)
        assertEquals(EncoderBitrateDiagnostics("actual.selected.encoder", EncoderBitrateMode.CBR, true, 6_000), diagnostic)
    }

    @Test
    fun `supported CBR cannot silently fall back to VBR`() {
        assertThrows(IllegalStateException::class.java) {
            RootEncoderBitrateDiagnostics.read(codec(1, true), "video/avc", 6_000)
        }
    }

    @Test
    fun `unsupported CBR reports configured VBR and never infers missing mode`() {
        assertEquals(EncoderBitrateMode.VBR, RootEncoderBitrateDiagnostics.read(codec(1, false), "video/avc", 6_000).mode)
        assertEquals(EncoderBitrateMode.UNKNOWN, RootEncoderBitrateDiagnostics.read(codec(null, false), "video/avc", 6_000).mode)
    }

    @Test
    fun `missing or vendor mode is not misreported as VBR`() {
        assertEquals(EncoderBitrateMode.UNKNOWN, configuredBitrateMode(null))
        assertEquals(EncoderBitrateMode.UNKNOWN, configuredBitrateMode(99))
    }

    @Test
    fun `configured Android modes keep their meaning`() {
        assertEquals(EncoderBitrateMode.CBR, configuredBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR))
        assertEquals(EncoderBitrateMode.VBR, configuredBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR))
        assertEquals(EncoderBitrateMode.CQ, configuredBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ))
    }
}
