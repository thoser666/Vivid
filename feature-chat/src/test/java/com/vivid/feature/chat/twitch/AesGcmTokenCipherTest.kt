package com.vivid.feature.chat.twitch

import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AesGcmTokenCipherTest {

    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    private fun cipher() = AesGcmTokenCipher(key)

    @Test
    fun `encrypt and decrypt round-trips a token`() {
        val encrypted = cipher().encrypt("oauth:secret-token")
        assertEquals("oauth:secret-token", cipher().decrypt(encrypted))
    }

    @Test
    fun `encrypt yields a different blob for the same input`() {
        assertNotEquals(cipher().encrypt("token"), cipher().encrypt("token"))
    }

    @Test
    fun `decrypt rejects a tampered blob`() {
        val encrypted = cipher().encrypt("oauth:secret-token").toCharArray()
        encrypted[encrypted.size - 1] = if (encrypted.last() == 'a') 'b' else 'a'
        assertThrows(Exception::class.java) {
            cipher().decrypt(String(encrypted))
        }
    }

    @Test
    fun `decrypt rejects an empty blob`() {
        assertThrows(IllegalArgumentException::class.java) {
            cipher().decrypt("")
        }
    }

    @Test
    fun `decrypt does not work with a different key`() {
        val otherKey = SecretKeySpec(ByteArray(32) { (it * 2 + 5).toByte() }, "AES")
        val encrypted = cipher().encrypt("oauth:secret-token")
        assertThrows(Exception::class.java) {
            AesGcmTokenCipher(otherKey).decrypt(encrypted)
        }
    }
}