package com.vivid.core.network.obs.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityTest {

    @Test
    fun `produces the expected authentication string for the OBS protocol example`() {
        val result = generateAuthenticationString(
            password = "mypassword",
            salt = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6",
            challenge = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6",
        )

        // Cross-checked against an independent SHA-256 + Base64 implementation.
        assertEquals("aaQHqxfeDdoVWOPnS/x/NTtd1dSDFRxQ4bbthU0d6nQ=", result)
    }

    @Test
    fun `is deterministic for the same input`() {
        val first = generateAuthenticationString("s3cret!pw", "randomSaltValue123", "randomChallengeValue456")
        val second = generateAuthenticationString("s3cret!pw", "randomSaltValue123", "randomChallengeValue456")

        assertEquals("qHAIbP45WpUjaHV2DgT+cK87Nt+6sadeYuySYBbcujE=", first)
        assertEquals(first, second)
    }

    @Test
    fun `changes when password salt or challenge changes`() {
        val base = generateAuthenticationString("pw", "salt", "challenge")

        assertTrue(generateAuthenticationString("pw2", "salt", "challenge") != base)
        assertTrue(generateAuthenticationString("pw", "salt2", "challenge") != base)
        assertTrue(generateAuthenticationString("pw", "salt", "challenge2") != base)
    }
}
