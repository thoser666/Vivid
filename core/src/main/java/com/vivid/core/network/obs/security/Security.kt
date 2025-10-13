package com.vivid.core.network.obs.security

import android.util.Base64
import java.security.MessageDigest

fun generateAuthenticationString(password: String, salt: String, challenge: String): String {
    val secretString = password + salt
    val secretHash = sha256Hash(secretString)
    val secretBase64 = Base64.encodeToString(secretHash, Base64.NO_WRAP)

    val authResponseString = secretBase64 + challenge
    val authResponseHash = sha256Hash(authResponseString)
    return Base64.encodeToString(authResponseHash, Base64.NO_WRAP)
}

private fun sha256Hash(input: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray(Charsets.UTF_8))
}