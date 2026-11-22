package com.vivid.core.network.obs.security

import com.google.gson.Gson
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

    // --- #320: Gson-De-/Serialisierung der Security-Datenklassen, die R8
    // durch ProGuard-Keep-Rules (app/proguard-rules.pro) geschützt werden.
    // Ohne Keep-Rules würde R8 die Klassen umbenennen/auslagern und Gson
    // könnte sie zur Laufzeit nicht mehr instanziieren → JsonIOException:
    // "Abstract classes can't be instantiated!" (VIVID-3V).

    private val gson = Gson()

    @Test
    fun `Gson deserializer can reconstruct an AuthenticationChallenge with authentication data`() {
        val challengeJson = """
            {
              "op": 0,
              "d": {
                "rpcVersion": 1,
                "authentication": {
                  "challenge": "challengeValue",
                  "salt": "saltValue"
                }
              }
            }
        """.trimIndent()

        val challenge = gson.fromJson(challengeJson, AuthenticationChallenge::class.java)

        assertEquals(0, challenge.op)
        assertEquals(1, challenge.d.rpcVersion)
        assertEquals("saltValue", challenge.d.authentication?.salt)
        assertEquals("challengeValue", challenge.d.authentication?.challenge)
    }

    @Test
    fun `Gson deserializer handles a challenge without authentication field`() {
        val challengeJson = """
            {
              "op": 0,
              "d": {
                "rpcVersion": 1,
                "authentication": null
              }
            }
        """.trimIndent()

        val challenge = gson.fromJson(challengeJson, AuthenticationChallenge::class.java)

        assertEquals(1, challenge.d.rpcVersion)
        assertTrue(challenge.d.authentication == null)
    }

    @Test
    fun `Gson serializer produces a valid AuthenticationResponse envelope`() {
        val response = AuthenticationResponse(
            op = 1,
            d = AuthenticationResponse.Data(
                rpcVersion = 1,
                authentication = "encodedAuthString",
                eventSubscriptions = 8192 + 8 + 1,
            ),
        )

        val json = gson.toJson(response)

        assertTrue(json.contains("\"op\":1"))
        assertTrue(json.contains("\"rpcVersion\":1"))
        assertTrue(json.contains("\"authentication\":\"encodedAuthString\""))
        assertTrue(json.contains("\"eventSubscriptions\":8201"))
    }

    @Test
    fun `Gson round-trips AuthenticationResponse`() {
        val original = AuthenticationResponse(
            op = 1,
            d = AuthenticationResponse.Data(
                rpcVersion = 1,
                authentication = "encodedAuthString",
                eventSubscriptions = 8201,
            ),
        )

        val json = gson.toJson(original)
        val restored = gson.fromJson(json, AuthenticationResponse::class.java)

        assertEquals(original.op, restored.op)
        assertEquals(original.d.rpcVersion, restored.d.rpcVersion)
        assertEquals(original.d.authentication, restored.d.authentication)
        assertEquals(original.d.eventSubscriptions, restored.d.eventSubscriptions)
    }
}
