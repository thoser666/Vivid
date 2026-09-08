package com.vivid.core.data

/**
 * Audio-Konfiguration der lokalen Replay-Aufnahme (Record-to-Disk).
 *
 * - [ALL]: Die MP4-Aufnahme enthält Bild **und Ton** (Standard).
 * - [VIDEO_ONLY]: Die MP4-Aufnahme enthält nur das Bild — der Muxer
 *   schreibt keine Audiospur (nützlich, wenn der Stream-Ton nicht im
 *   Replay landen soll, z. B. bei lizenzierter Musik).
 */
enum class ReplayAudioMode {
    ALL,
    VIDEO_ONLY,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [ALL]). */
        fun fromName(name: String?): ReplayAudioMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALL
    }
}