package com.vivid.core.network.obs

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ObsBase64Test {

    @Test
    fun `roundtrip preserves arbitrary bytes`() {
        val original = byteArrayOf(0x00, 0x01, 0x02, 0x7F, -1, -128, 0x42, 0x24)
        assertArrayEquals(original, ObsBase64.decode(ObsBase64.encode(original)))
    }

    @Test
    fun `standard alphabet sample decodes correctly`() {
        // "Man" -> TWFu (RFC 4648 test vector)
        assertArrayEquals("Man".toByteArray(), ObsBase64.decode("TWFu"))
        // "Ma" -> TWE=
        assertArrayEquals("Ma".toByteArray(), ObsBase64.decode("TWE="))
        // "M" -> TQ==
        assertArrayEquals("M".toByteArray(), ObsBase64.decode("TQ=="))
    }

    @Test
    fun `unpadded input is tolerated`() {
        assertArrayEquals("Ma".toByteArray(), ObsBase64.decode("TWE"))
    }

    @Test
    fun `whitespace is ignored`() {
        assertArrayEquals("Ma".toByteArray(), ObsBase64.decode("T W E\n"))
    }

    @Test
    fun `empty input decodes to an empty array`() {
        assertArrayEquals(ByteArray(0), ObsBase64.decode(""))
    }

    @Test
    fun `invalid alphabet returns null`() {
        assertNull(ObsBase64.decode("TW&u"))
    }

    @Test
    fun `invalid length returns null`() {
        assertNull(ObsBase64.decode("T"))
    }
}