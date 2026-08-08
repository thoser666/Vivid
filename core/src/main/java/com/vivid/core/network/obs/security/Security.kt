package com.vivid.core.network.obs.security

import java.security.MessageDigest
import okio.ByteString.Companion.toByteString

fun generateAuthenticationString(password: String, salt: String, challenge: String): String {
    val secretString = password + salt
    val secretHash = sha256Hash(secretString)
    val secretBase64 = secretHash.toByteString().base64()

    val authResponseString = secretBase64 + challenge
    val authResponseHash = sha256Hash(authResponseString)
    return authResponseHash.toByteString().base64()
}

private fun sha256Hash(input: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
}
