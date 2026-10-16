package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P2 — Lebenszyklus der Hardware-Erkennung ohne Kamera (Skizze §5, Risiko #1).
 *
 * Der Scheduler ist ein reines Array als Uhr: jeder Nachversuch liegt als
 * ausstehender „Frame“ da und wird vom Test bewusst ausgelöst. Damit ist
 * nachvollziehbar, was passiert, wenn `enableFaceDetection` die Kamera noch
 * nicht tragen kann (Retry), wenn das Ziel gewechselt wird (Generation) und
 * wenn die Capability nicht erfüllt ist (Budget).
 */
class FaceDetectionProducerTest {

    private class FakeFaceDetectable(var enableResult: Boolean = true) : FaceDetectable {
        var enableCalls = 0
        var disableCalls = 0

        override fun enableFaceDetection(onFaces: (FaceFrame) -> Unit): Boolean {
            enableCalls += 1
            return enableResult
        }

        override fun disableFaceDetection() {
            disableCalls += 1
        }
    }

    private val pending = ArrayDeque<() -> Unit>()
    private val availabilityLog = mutableListOf<Boolean?>()
    private val frame: (FaceFrame) -> Unit = { }

    /**
     * @param clearsOnDetach wenn `false`, bleibt der Scheduler nach [detach]
     *   bestehen — genau dann muss die Generation allein verhindern, dass ein
     *   verwaister Versuch ein abgelöstes Ziel wiederbelebt.
     */
    private fun producer(maxAttempts: Int = 10, clearsOnDetach: Boolean = true) = FaceDetectionProducer(
        schedule = { _, action -> pending.addLast(action) },
        cancelScheduled = { if (clearsOnDetach) pending.clear() },
        onAvailableChanged = { availabilityLog.add(it) },
        maxAttempts = maxAttempts,
    )

    private fun runPending() {
        while (pending.isNotEmpty()) pending.removeFirst().invoke()
    }

    // ------------------------------------------------------------------
    // Wunsch vs. Ziel: an darf jederzeit, eingeschaltet wird nur bei Wunsch.
    // ------------------------------------------------------------------

    @Test
    fun `attaching without the wish only registers the target`() {
        val fake = FakeFaceDetectable()
        val producer = producer()

        producer.attach(fake, frame)

        assertTrue(producer.isAttached)
        assertEquals(0, fake.enableCalls)
        assertNull(producer.available)
        assertFalse(producer.isActive)
    }

    @Test
    fun `a later wish enables the already attached target without re-attaching`() {
        val fake = FakeFaceDetectable()
        val producer = producer()
        producer.attach(fake, frame)

        producer.setDesired(true)

        assertEquals(1, fake.enableCalls)
        assertTrue(producer.isActive)

        // Derselbe Wunsch erneut ist ein No-Op.
        producer.setDesired(true)
        assertEquals(1, fake.enableCalls)
    }

    @Test
    fun `the wish is remembered while no target is attached`() {
        val fake = FakeFaceDetectable()
        val producer = producer()

        producer.setDesired(true)
        assertEquals(0, fake.enableCalls, "ohne Ziel wird nichts eingeschaltet")

        producer.attach(fake, frame)
        assertEquals(1, fake.enableCalls, "beim Attach zaehlt der gespeicherte Wunsch")
        assertTrue(producer.isActive)
    }

    // ------------------------------------------------------------------
    // Kamera noch nicht offen -> Nachversuche, bis die Capability steht.
    // ------------------------------------------------------------------

    @Test
    fun `retries until the camera reports ready`() {
        val fake = FakeFaceDetectable(enableResult = false)
        val producer = producer()
        producer.setDesired(true)

        producer.attach(fake, frame)
        assertEquals(1, fake.enableCalls)
        assertEquals(false, producer.available)
        assertFalse(producer.isActive)
        assertEquals(1, pending.size, "ein Nachversuch liegt an")

        fake.enableResult = true
        pending.removeFirst().invoke()

        assertEquals(2, fake.enableCalls)
        assertTrue(pending.isEmpty(), "Erfolg beendet die Nachversuche")
        assertEquals(true, producer.available)
        assertTrue(producer.isActive)
    }

    @Test
    fun `the attempt budget marks the capability as unsupported`() {
        val fake = FakeFaceDetectable(enableResult = false)
        val producer = producer(maxAttempts = 3)
        producer.setDesired(true)

        producer.attach(fake, frame)
        runPending()

        assertEquals(3, fake.enableCalls)
        assertTrue(pending.isEmpty(), "nach dem Budget wird nicht weiter versucht")
        assertEquals(false, producer.available)
        assertFalse(producer.isActive)
    }

    // ------------------------------------------------------------------
    // Zielwechsel: alter Zustand tot, Generation schuetzt vor Wiederbelebung.
    // ------------------------------------------------------------------

    @Test
    fun `a stale retry stays inert after detach`() {
        val fake = FakeFaceDetectable(enableResult = false)
        val producer = producer(clearsOnDetach = false)
        producer.setDesired(true)
        producer.attach(fake, frame)
        assertEquals(1, pending.size)

        producer.detach()

        assertEquals(1, fake.disableCalls, "detach schaltet das Ziel ab")
        assertNull(producer.available, "Capability ist nach dem Wechsel wieder unbekannt")
        assertFalse(producer.isAttached)

        runPending()

        assertEquals(1, fake.enableCalls, "der verwaiste Versuch bleibt tot")
        assertTrue(pending.isEmpty(), "und laeutet keinen neuen Versuch ein")
    }

    @Test
    fun `attaching a new target disables the old one`() {
        val oldTarget = FakeFaceDetectable(enableResult = false)
        val newTarget = FakeFaceDetectable()
        val producer = producer()
        producer.setDesired(true)
        producer.attach(oldTarget, frame)
        assertEquals(1, pending.size)

        producer.attach(newTarget, frame)

        assertEquals(1, oldTarget.disableCalls)
        assertEquals(1, newTarget.enableCalls, "der neue Wunsch laeuft sofort")
        assertTrue(pending.isEmpty(), "die Nachversuche des alten Ziels sind abgebrochen")
        assertEquals(true, producer.available)
    }

    @Test
    fun `switching the wish off disables detection but keeps the capability`() {
        val fake = FakeFaceDetectable()
        val producer = producer()
        producer.setDesired(true)
        producer.attach(fake, frame)
        assertTrue(producer.isActive)

        producer.setDesired(false)

        assertEquals(1, fake.disableCalls)
        assertFalse(producer.isActive)
        assertEquals(true, producer.available, "die Capability-Antwort bleibt unveraendert")
        assertTrue(producer.isAttached, "der Toggle braucht spaeter kein Re-Attach")
    }

    @Test
    fun `detach reports the capability as unknown again`() {
        val fake = FakeFaceDetectable()
        val producer = producer()
        producer.setDesired(true)
        producer.attach(fake, frame)

        producer.detach()

        assertNull(producer.available)
        assertFalse(producer.isActive)
        assertFalse(producer.isAttached)
    }

    @Test
    fun `the availability callback fires only on changes`() {
        val fake = FakeFaceDetectable()
        val producer = producer()

        producer.setDesired(true)
        producer.attach(fake, frame)
        producer.setDesired(false)
        producer.detach()

        assertEquals(listOf<Boolean?>(true, null), availabilityLog)
    }
}
