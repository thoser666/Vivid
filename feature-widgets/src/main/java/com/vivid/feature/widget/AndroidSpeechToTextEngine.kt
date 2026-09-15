package com.vivid.feature.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vivid.core.data.SpeechToTextEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-SpeechRecognizer-Implementierung des [SpeechToTextEngine]-Interface
 * (PARITY-Zeile 143 „Untertitel (Speech-to-Text)"). Erkennt live aus dem Mikrofon;
 * Teilsätze kommen über [RecognitionListener.onPartialResults], finale Sätze über
 * [RecognitionListener.onResults]. Plattform-Fehlercodes werden 1:1 weitergereicht —
 * die Wiederhol-Entscheidung (Backoff, Restart-Würdigkeit) trifft der reine
 * [SubtitleController] in domain.
 *
 * Capability-Fallback: [isAvailable] prüft `SpeechRecognizer.isRecognitionAvailable`
 * — Geräte ohne Erkennungsdienst (Typischer F-Droid-Fall) liefern false, das Overlay
 * zeigt dann einen Hinweis statt eines toten Widgets.
 */
@Singleton
class AndroidSpeechToTextEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToTextEngine {

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechToTextEngine.Listener? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun setListener(listener: SpeechToTextEngine.Listener?) {
        this.listener = listener
    }

    override fun start() {
        if (!isAvailable) return
        // Bestehende Sitzung sauber beenden (Recognizer ist Single-Session).
        stop()
        val engineListener = listener
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    engineListener?.onListening()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    engineListener?.onError(error)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    engineListener?.onFinal(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotEmpty()) engineListener?.onPartial(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        rec.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                // Kurze Teilsätze häufiger liefern — Untertitel sollen fließen.
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    override fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}
