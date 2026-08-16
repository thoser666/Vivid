package com.vivid.feature.chat.media

/**
 * Abstraktion über die Media-Player-Steuerung des Chat-Bots
 * (Moblin-Parität: Media-Player-Steuerung via MediaSession).
 *
 * Die Android-Implementierung steuert den aktiven Musik-Player über
 * [android.media.session.MediaController] und braucht dafür den
 * nutzerseitig gewährten **Benachrichtigungszugriff** (siehe
 * [MediaNotificationListener]).
 */
interface ChatMediaPlayer {

    /** „Titel – Interpret“ des aktuellen Songs, oder null wenn nichts läuft. */
    fun nowPlaying(): String?

    fun play()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()

    /** Ob der Benachrichtigungszugriff aktiv ist (fremde Media-Sessions abrufbar). */
    fun hasAccess(): Boolean
}
