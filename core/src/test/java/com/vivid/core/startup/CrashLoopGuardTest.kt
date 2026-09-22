package com.vivid.core.startup

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Dateibasierter Startversuch-Zähler (Startup-Safe-Mode):
 *
 *  - Persistenz über Prozessgrenzen (Datei `startup_attempts`).
 *  - **Fail-open**: fehlende, leere oder korrupte Datei gilt als 0.
 *  - [CrashLoopGuard.markUiReached] setzt vollständig zurück.
 */
class CrashLoopGuardTest {

    @TempDir
    lateinit var tmp: File

    private fun guard(): CrashLoopGuard = CrashLoopGuard(File(tmp, "startup_attempts"))

    @Test
    fun `fehlende Datei gilt als frischer Start`() {
        assertEquals(0, guard().currentAttempts())
    }

    @Test
    fun `recordAttempt persistiert ueber Instanzgrenzen`() {
        val file = File(tmp, "startup_attempts")
        CrashLoopGuard(file).recordAttempt()
        CrashLoopGuard(file).recordAttempt()

        assertEquals(2, CrashLoopGuard(file).currentAttempts())
    }

    @Test
    fun `korrupte Datei fail-open zu 0`() {
        val file = File(tmp, "startup_attempts")
        file.parentFile?.mkdirs()
        file.writeText("keine-zahl")

        assertEquals(0, CrashLoopGuard(file).currentAttempts())
    }

    @Test
    fun `leere Datei fail-open zu 0`() {
        val file = File(tmp, "startup_attempts")
        file.writeText("  \n")

        assertEquals(0, CrashLoopGuard(file).currentAttempts())
    }

    @Test
    fun `markUiReached setzt vollstaendig zurueck`() {
        val file = File(tmp, "startup_attempts")
        val g = CrashLoopGuard(file)
        g.recordAttempt()
        g.recordAttempt()

        g.markUiReached()

        assertEquals(0, g.currentAttempts())
        assertEquals("0", file.readText().trim())
    }

    @Test
    fun `gesperrtes Verzeichnis wirft nicht - fail-open`() {
        // Datei als Verzeichnis ersetzen: Jeder Schreib/Lesezugriff schlaegt fehl.
        val file = File(tmp, "startup_attempts")
        file.parentFile?.mkdirs()
        assertTrue(file.mkdir())

        val g = CrashLoopGuard(file)
        g.recordAttempt()
        g.markUiReached()

        assertEquals(0, g.currentAttempts())
    }
}
