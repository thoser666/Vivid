package com.vivid.feature.chat.bot

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-Implementierung des [ChatTtsSpeaker] über die systemeigene
 * TextToSpeech-Engine (keine Runtime-Berechtigung nötig — reine Ausgabe).
 *
 * Die Engine-Initialisierung läuft asynchron ([TextToSpeech.OnInitListener]);
 * bis dahin wird still verworfen (erste Nachrichten können fehlen). Ist keine
 * TTS-Engine auf dem Gerät installiert, bleibt [initialized] false und
 * [speak] ist ein No-op — die App stürzt nie ab.
 */
@Singleton
class AndroidTtsSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatTtsSpeaker {

    @Volatile
    private var initialized = false
    private var tts: TextToSpeech? = null

    private fun ensureInitialized() {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale.getDefault())
                    initialized = true
                }
            }
        }
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        ensureInitialized()
        if (!initialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vivid-tts")
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
