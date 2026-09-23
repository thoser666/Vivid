package com.vivid.irlbroadcaster

import io.sentry.SentryOptions

/**
 * Pure Entscheidung der Sentry-beforeSend-Opt-out-Logik (isoliert testbar,
 * ohne Sentry- oder Android-Abhängigkeit):
 *
 *  - [sentryEnabled] = true  → das Event wird durchgereicht (unverändert)
 *  - [sentryEnabled] = false → das Event wird verworfen (null, kein Versand)
 *
 * Generisch über den Event-Typ, damit die Kernlogik ohne Sentry-Klassen
 * getestet werden kann — [VividApplication] nutzt sie mit [io.sentry.SentryEvent].
 */
internal fun <T> applySentryOptOut(event: T, sentryEnabled: Boolean): T? =
    if (sentryEnabled) event else null

/**
 * Baut den echten [SentryOptions.BeforeSendCallback], den [VividApplication]
 * bei der Sentry-Initialisierung registriert. Der Toggle-Stand wird **live**
 * über [isEnabled] gelesen (pro Event), damit ein Umschalten in den Settings
 * sofort wirkt — kein Snapshot beim Init.
 *
 * Als Fabrik extrahiert, damit die Callback-Verkabelung in einem reinen
 * Unit-Test mit einem echten [io.sentry.SentryEvent] geprüft werden kann
 * (Android-Kontext nicht nötig).
 */
internal fun sentryBeforeSendCallback(
    isEnabled: () -> Boolean,
): SentryOptions.BeforeSendCallback =
    SentryOptions.BeforeSendCallback { event, _ ->
        applySentryOptOut(event, isEnabled())
    }

/**
 * Baut den [SentryOptions.BeforeSendReplayCallback] für Session-Replay-Envelopes.
 *
 * Hintergrund: Replays sind **keine** Sentry-Events — der normale
 * [SentryOptions.BeforeSendCallback] filtert sie nicht. Dieser Callback greift
 * auf denselben [applySentryOptOut]-Stand zu, damit der Opt-out-Toggle
 * („Fehlerberichte senden“) auch Replay-Envelopes verwirft. Er ist die dritte
 * Verteidigungslinie neben den Rates ([com.vivid.core.startup.SentryReplayPolicy.plan])
 * und der Laufzeit-Steuerung ([com.vivid.core.startup.SentryReplayPolicy.runtimeAction]).
 */
internal fun sentryBeforeSendReplayCallback(
    isEnabled: () -> Boolean,
): SentryOptions.BeforeSendReplayCallback =
    SentryOptions.BeforeSendReplayCallback { event, _ ->
        applySentryOptOut(event, isEnabled())
    }
