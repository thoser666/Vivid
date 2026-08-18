package com.vivid.irlbroadcaster

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SentryOptOutTest {

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
}
