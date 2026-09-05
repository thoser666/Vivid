package com.vivid.feature.chat.twitch

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert einen [TokenCipher], dessen Schlüssel unzugänglich im Android
 * Keystore liegt (AES-256/GCM — Schlüsselmaterial verlässt den Keystore nie).
 */
@Singleton
class AndroidKeystoreTokenCipher @Inject constructor() : TokenCipher {

    private val delegate: TokenCipher by lazy {
        AesGcmTokenCipher(loadOrCreateKey())
    }

    override fun encrypt(plainText: String): String = delegate.encrypt(plainText)

    override fun decrypt(encrypted: String): String = delegate.decrypt(encrypted)

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        internal const val KEY_ALIAS = "vivid_twitch_oauth"
    }
}