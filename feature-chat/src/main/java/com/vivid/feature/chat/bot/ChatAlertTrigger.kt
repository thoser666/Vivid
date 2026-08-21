package com.vivid.feature.chat.bot

import com.vivid.feature.chat.model.ChatAlertType

/**
 * Trigger für synthetische Event-Alerts (`!testalert follow|sub|raid`) —
 * entkoppelt wie [ChatModeration], damit die Engine (feature-chat) nicht an
 * den Twitch-Client hängt: Die App-Implementierung delegiert an den
 * `TwitchChatEventSubReader.triggerTestAlert`, der den Alert in denselben
 * Flow speist, den das Chat-Overlay anzeigt. Ohne Bindung (Tests) greift der
 * [NoOpChatAlertTrigger]-Fallback — der Befehl antwortet dann trotzdem mit
 * der Bestätigung, löst aber nichts aus.
 */
interface ChatAlertTrigger {
    /** Löst lokal einen synthetischen Event-Alert aus (erscheint im Overlay). */
    fun triggerTestAlert(type: ChatAlertType)
}

/** Fallback, wenn keine Implementierung an die Engine übergeben wurde. */
object NoOpChatAlertTrigger : ChatAlertTrigger {
    override fun triggerTestAlert(type: ChatAlertType) = Unit
}
