package com.vivid.irlbroadcaster

import io.sentry.Hint
import io.sentry.SentryEvent
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SentryOptOutTest {

    // --- Pure Entscheidung (kein Sentry-Import nötig) ---

    @Test
    fun `event is kept unchanged when reporting is enabled`() {
        val event = Any()
        assertSame(event, applySentryOptOut(event, sentryEnabled = true))
    }

    @Test
    fun `event is dropped when reporting is disabled`() {
        assertNull(applySentryOptOut(Any(), sentryEnabled = false))
    }

    @Test
    fun `opt-out decision is independent of the event type`() {
        // Typ-Generizität: die Entscheidung gilt für jedes Event-Objekt,
        // nicht nur für konkrete Sentry-Klassen.
        val event = "crash-payload"
        assertSame(event, applySentryOptOut(event, sentryEnabled = true))
        assertNull(applySentryOptOut(event, sentryEnabled = false))
    }

    // --- beforeSend-Callback-Verkabelung (exakt die Fabrik von VividApplication) ---

    private val hint = Hint()

    @Test
    fun `beforeSend passes a real SentryEvent through when enabled`() {
        val event = SentryEvent()
        val callback = sentryBeforeSendCallback { true }

        assertSame(event, callback.execute(event, hint))
    }

    @Test
    fun `beforeSend returns null and drops a real SentryEvent when disabled`() {
        val event = SentryEvent()
        val callback = sentryBeforeSendCallback { false }

        assertNull(callback.execute(event, hint))
    }

    @Test
    fun `beforeSend reads the toggle live on every event not at init time`() {
        var enabled = true
        val callback = sentryBeforeSendCallback { enabled }
        val event = SentryEvent()

        assertSame(event, callback.execute(event, hint))

        // Toggle wird zur Laufzeit umgeschaltet — der nächste Aufruf verwirft sofort,
        // ohne dass der Callback neu registriert werden müsste.
        enabled = false
        assertNull(callback.execute(event, hint))
    }
}
