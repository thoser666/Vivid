package com.vivid.feature.chat.bot

/**
 * Abstraktion über die Sprachausgabe (Android TextToSpeech). Kleine,
 * testbare Schnittstelle für den Chat-TTS-Controller.
 */
interface ChatTtsSpeaker {
    /** Spricht [text] laut aus (wenn das TTS-Engine-Initialisierung abgeschlossen ist). */
    fun speak(text: String)

    /** Gibt die TTS-Engine frei (z. B. wenn der Bot stoppt). */
    fun shutdown()
}
