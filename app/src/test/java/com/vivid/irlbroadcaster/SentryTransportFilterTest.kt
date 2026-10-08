package com.vivid.irlbroadcaster

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import java.io.IOException
import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vertrag der Transport-Betriebszustands-Filterung (Sentry VIVID-3P, #267).
 *
 * Die Fehlerklassen stammen aus den bereits attribuierten Sentry-Issues des
 * Watchdogs (#217/#219/#220/#224/#244/#248/#267) — der Filter existiert, damit
 * dieser Zustand keinen neuen Triage-Lauf mehr erzeugt.
 */
class SentryTransportFilterTest {

    private val hint = Hint()

    private fun event(
        level: SentryLevel,
        throwable: Throwable,
    ): SentryEvent = SentryEvent().apply {
        this.level = level
        setThrowable(throwable)
    }

    // --- Der Befund aus #267 ---

    @Test
    fun `NoRouteToHostException wird als erwarteter Betriebszustand verworfen`() {
        val disposition = classifySentryEvent(
            SentryLevel.ERROR,
            NoRouteToHostException("No route to host"),
        )

        assertEquals(SentryEventDisposition.DROP_EXPECTED_TRANSPORT, disposition)
    }

    /**
     * Absichert den am Sentry-Artefakt empirisch ermittelten Weg: auf dem
     * `beforeSend`-Pfad ist `getExceptions()` noch `null`. Eine Policy, die dort
     * hineingreift, erkennt nichts und wäre stumm wirkungslos — der Test schlägt
     * dann genau hier fehl.
     */
    @Test
    fun `Policy greift auch dann, wenn getExceptions() noch null ist`() {
        val e = event(SentryLevel.ERROR, NoRouteToHostException("No route to host"))
        assertNull("Sentry fuellt exceptions erst bei der Serialisierung", e.exceptions)

        assertEquals(
            SentryEventDisposition.DROP_EXPECTED_TRANSPORT,
            classifySentryEvent(e.level, e.throwable),
        )
    }

    @Test
    fun `throwableTypeChain liefert dieselbe Kette wie der Sentry-Event-Accessor`() {
        val e = event(SentryLevel.ERROR, NoRouteToHostException("No route to host"))

        assertEquals(listOf(NoRouteToHostException::class.java.name), e.exceptionTypeNames())
    }

    // --- Sicherheitsnetz 1: FATAL ist tabu ---

    @Test
    fun `FATAL bleibt stehen, auch bei einem erlaubten Transporttyp`() {
        val disposition = classifySentryEvent(
            SentryLevel.FATAL,
            NoRouteToHostException("No route to host"),
        )

        assertEquals(SentryEventDisposition.KEEP, disposition)
    }

    // --- Sicherheitsnetz 2: kein Basisklassen-Abgleich (die BindException-Falle) ---

    /**
     * #228 (VIVID-37/3E, Registry `REMOTE-EADDRINUSE-STARTUP`) ist ein echter
     * Start-Crash und strukturell Geschwister der hier gefilterten Typen: Alle vier
     * erben von `java.net.SocketException`. Ein Abgleich auf die Oberklasse würde
     * ihn mit verschlucken — dieser Test ist die Versicherung dagegen.
     */
    @Test
    fun `BindException aus #228 bleibt stehen obwohl sie eine SocketException ist`() {
        assertTrue(
            "Voraussetzung: BindException und NoRouteToHostException sind Geschwister",
            SocketException::class.java.isAssignableFrom(BindException::class.java) &&
                SocketException::class.java.isAssignableFrom(NoRouteToHostException::class.java),
        )

        val disposition = classifySentryEvent(
            SentryLevel.ERROR,
            BindException("Address already in use"),
        )

        assertEquals(SentryEventDisposition.KEEP, disposition)
    }

    @Test
    fun `Allowlist enthaelt keine SocketException-Oberklasse und keine IOException-Oberklasse`() {
        // Wäre eine Basisklasse enthalten, wuerde jede beliebige SocketException
        // durchfallen — einschliesslich der echten Crashes aus #222/#228.
        assertTrue(SocketException::class.java.name !in EXPECTED_TRANSPORT_EXCEPTIONS)
        assertTrue(IOException::class.java.name !in EXPECTED_TRANSPORT_EXCEPTIONS)
        assertTrue(Throwable::class.java.name !in EXPECTED_TRANSPORT_EXCEPTIONS)
    }

    // --- Sicherheitsnetz 3: nur die komplette Kette ---

    @Test
    fun `gemischte Kette mit einem Produktfehler bleibt stehen`() {
        val gemischt = IllegalStateException("boom", NoRouteToHostException("No route to host"))

        assertEquals(SentryEventDisposition.KEEP, classifySentryEvent(SentryLevel.ERROR, gemischt))
    }

    @Test
    fun `reine Transportkette aus mehreren Gliedern wird verworfen`() {
        // Zwei erlaubte Typen ineinander: ConnectException (SocketException) und
        // SocketTimeoutException (InterruptedIOException) — die Kette mischt die
        // Basisklassen, bleibt aber vollstaendig erlaubt. initCause statt
        // Zweier-Konstruktor: Java-Konstruktoren werden nicht vererbt, ein
        // SocketTimeoutException(msg, cause) existiert also nicht.
        val kette = ConnectException("Failed to connect /192.168.1.100:4455").apply {
            initCause(SocketTimeoutException("Read timed out"))
        }

        assertEquals(
            // Aussen nach innen, wie throwableTypeChain dokumentiert.
            listOf(
                ConnectException::class.java.name,
                SocketTimeoutException::class.java.name,
            ),
            throwableTypeChain(kette),
        )
        assertEquals(
            SentryEventDisposition.DROP_EXPECTED_TRANSPORT,
            classifySentryEvent(SentryLevel.ERROR, kette),
        )
    }

    @Test
    fun `Produktfehler ohne Transportbezug bleibt stehen`() {
        assertEquals(
            SentryEventDisposition.KEEP,
            classifySentryEvent(SentryLevel.ERROR, NullPointerException("npe")),
        )
    }

    // --- Datenvertrag der Allowlist ---

    @Test
    fun `jeder belegte Issue-Typ steht exakt in der Allowlist`() {
        val erwartet = setOf(
            "java.net.NoRouteToHostException",
            "java.net.ConnectException",
            "java.net.SocketTimeoutException",
            "java.net.UnknownHostException",
            "io.ktor.client.plugins.ConnectTimeoutException",
        )

        assertEquals(erwartet, EXPECTED_TRANSPORT_EXCEPTIONS)
    }

    /**
     * `app` hat bewusst keine Ktor-Dependency (die Clients liegen in `core`), der
     * Ktor-Typ ist also im Test-Classpath nicht instanziierbar. Geprueft wird
     * deshalb der **FQCN-Vertrag** — genau dieser Name ist es, den die Policy
     * gegen ein Event vergleicht. Ein Typen-Attrappe im Testsource wuerde einen
     * gleichnamigen echten Typ verdecken, sobald `app` doch Ktor aufnimmt.
     */
    @Test
    fun `Ktor-ConnectTimeout-Namen werden als erlaubter Transportzustand erkannt`() {
        assertTrue(
            "io.ktor.client.plugins.ConnectTimeoutException" in EXPECTED_TRANSPORT_EXCEPTIONS,
        )
    }

    @Test
    fun `jeder Netzwerk-Allowlist-Eintrag wird am echten Objekt erkannt`() {
        val echteInstanzen = listOf(
            NoRouteToHostException("No route to host"),
            ConnectException("Failed to connect"),
            SocketTimeoutException("Read timed out"),
            UnknownHostException("Unable to resolve host"),
        )

        echteInstanzen.forEach { instanz ->
            assertEquals(
                "${instanz.javaClass.name} muss als Betriebszustand erkannt werden",
                SentryEventDisposition.DROP_EXPECTED_TRANSPORT,
                classifySentryEvent(SentryLevel.ERROR, instanz),
            )
        }
    }

    // --- Randfaelle ---

    @Test
    fun `Event ohne Throwable bleibt stehen`() {
        assertEquals(SentryEventDisposition.KEEP, classifySentryEvent(SentryLevel.ERROR, null))
        assertEquals(SentryEventDisposition.KEEP, classifySentryEvent(SentryLevel.FATAL, null))
    }

    /**
     * Fehlende Information ist kein Freibrief: Ohne gesetztes Level laesst sich
     * nicht ausschliessen, dass es doch ein FATAL-Crash ist — das Event bleibt.
     */
    @Test
    fun `unbekanntes Level bleibt stehen`() {
        assertEquals(
            SentryEventDisposition.KEEP,
            classifySentryEvent(null, NoRouteToHostException("No route to host")),
        )
    }

    @Test
    fun `zyklische cause-Kette terminiert statt endlos zu laufen`() {
        val zyklus = object : RuntimeException("selbstbezueglich") {
            override val cause: Throwable? get() = this
        }

        assertEquals(listOf(zyklus.javaClass.name), throwableTypeChain(zyklus))
    }

    @Test
    fun `tiefenbegrenzte Kette bleibt unter der Grenze`() {
        // 41-gliedrige Kette: initCause ist nur einmal aufrufbar, deshalb von
        // hinten nach vorn aufbauen.
        var tief: Throwable = IOException("Ebene 40")
        for (ebene in 39 downTo 0) {
            tief = IOException("Ebene $ebene", tief)
        }

        assertTrue(throwableTypeChain(tief).size <= 16)
    }

    // --- Verkabelung: beforeSend-Kette aus Opt-out und Filter ---

    @Test
    fun `beforeSend verwirft den Betriebszustand am echten SentryEvent`() {
        val e = event(SentryLevel.ERROR, NoRouteToHostException("No route to host"))
        val callback = sentryBeforeSendWithTransportFilter { true }

        assertNull(callback.execute(e, hint))
    }

    @Test
    fun `beforeSend laesst FATAL unveraendert durch`() {
        val e = event(SentryLevel.FATAL, NoRouteToHostException("No route to host"))
        val callback = sentryBeforeSendWithTransportFilter { true }

        assertSame(e, callback.execute(e, hint))
    }

    @Test
    fun `beforeSend laesst einen Produktfehler unveraendert durch`() {
        val e = event(SentryLevel.ERROR, NullPointerException("npe"))
        val callback = sentryBeforeSendWithTransportFilter { true }

        assertSame(e, callback.execute(e, hint))
    }

    @Test
    fun `Opt-out dominiert den Betriebszustands-Filter`() {
        val e = event(SentryLevel.ERROR, NoRouteToHostException("No route to host"))
        val callback = sentryBeforeSendWithTransportFilter { false }

        assertNull("Opt-out verwirft jedes Event", callback.execute(e, hint))
    }

    @Test
    fun `beforeSend liest den Opt-out pro Event und faengt einen Wechsel ab`() {
        var enabled = true
        val callback = sentryBeforeSendWithTransportFilter { enabled }
        val e = event(SentryLevel.ERROR, NullPointerException("npe"))

        assertSame(e, callback.execute(e, hint))
        enabled = false
        assertNull(callback.execute(e, hint))
    }
}