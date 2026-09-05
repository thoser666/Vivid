package com.vivid.feature.chat.twitch

/**
 * Verschlüsselt Klartexte vor der Persistenz (z. B. Twitch-OAuth-Tokens).
 *
 * Die Implementierung tauscht ausschließlich Strings — die Rückgabe ist
 * verlustfrei umwandelbar (IV + Ciphertext als ISO-8859-1-String), damit
 * der Wert über einen Preferences-DataStore gespeichert werden kann.
 */
interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(encrypted: String): String
}

/** Byte-String-Konvertierung ohne Base64 (ISO-8859-1 ist für Bytes 0–255 verlustfrei). */
internal fun ByteArray.toTokenString(): String = toString(Charsets.ISO_8859_1)

internal fun String.toTokenBytes(): ByteArray = toByteArray(Charsets.ISO_8859_1)