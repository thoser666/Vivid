package com.vivid.core.network.obs

import java.io.ByteArrayOutputStream

/**
 * Minimaler Base64-Decoder/-Encoder (Standard-Alphabet) ohne Android-API:
 * `java.util.Base64` existiert erst ab API 26, `android.util.Base64` ist in
 * lokalen Unit-Tests nicht verfügbar. Bewusst klein, deterministisch testbar —
 * wird für das `img`-Feld der OBS-`TakeSourceScreenshot`-Antwort genutzt.
 */
internal object ObsBase64 {

    private const val PAD = '='

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val DECODE: IntArray = IntArray(128) { -1 }.also { table ->
        for (i in ALPHABET.indices) {
            table[ALPHABET[i].code] = i
        }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b1 = data[i].toInt() and 0xFF
            val b2 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else -1
            val b3 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else -1
            sb.append(ALPHABET[(b1 ushr 2) and 0x3F])
            sb.append(ALPHABET[((b1 shl 4) or (if (b2 == -1) 0 else b2 ushr 4)) and 0x3F])
            sb.append(if (b2 == -1) PAD else ALPHABET[((b2 shl 2) or (if (b3 == -1) 0 else b3 ushr 6)) and 0x3F])
            sb.append(if (b3 == -1) PAD else ALPHABET[b3 and 0x3F])
            i += 3
        }
        return sb.toString()
    }

    /** Liefert `null` bei ungültigen Zeichen oder fehlerhafter Länge. */
    fun decode(encoded: String): ByteArray? {
        val cleaned = encoded.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)
        val unpadded = cleaned.trimEnd(PAD)
        if (unpadded.length % 4 == 1) return null
        if (unpadded.any { it.code >= 128 || DECODE[it.code] == -1 }) return null

        val output = ByteArrayOutputStream((unpadded.length * 3) / 4)
        var i = 0
        while (i < unpadded.length) {
            val c1 = DECODE[unpadded[i].code]
            val c2 = DECODE[unpadded[i + 1].code]
            val c3 = if (i + 2 < unpadded.length) DECODE[unpadded[i + 2].code] else 0
            val c4 = if (i + 3 < unpadded.length) DECODE[unpadded[i + 3].code] else 0
            output.write((c1 shl 2) or (c2 ushr 4))
            if (i + 2 < unpadded.length) {
                output.write(((c2 shl 4) or (c3 ushr 2)) and 0xFF)
            }
            if (i + 3 < unpadded.length) {
                output.write(((c3 shl 6) or c4) and 0xFF)
            }
            i += 4
        }
        return output.toByteArray()
    }
}