package com.vivid.feature.chat.twitch

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM-Verschlüsselung für die Token-Persistenz.
 *
 * Arbeite auf der JVM und auf dem Android Keystore (über einen injizierten
 * [SecretKey]). Das 12-Byte-IV wird dem Ciphertext vorangestellt und im
 * [decrypt] wieder abgetrennt.
 */
class AesGcmTokenCipher(
    private val secretKey: SecretKey,
    private val random: SecureRandom = SecureRandom(),
) : TokenCipher {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, random)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return (iv + ciphertext).toTokenString()
    }

    override fun decrypt(encrypted: String): String {
        val bytes = encrypted.toTokenBytes()
        require(bytes.size > IV_LENGTH) { "Verschlüsselter Token ist zu kurz." }
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val ciphertext = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_SIZE_BITS = 128
    }
}