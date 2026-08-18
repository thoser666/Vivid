package com.vivid.irlbroadcaster

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
