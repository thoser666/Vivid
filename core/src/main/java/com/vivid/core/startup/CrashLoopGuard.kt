package com.vivid.core.startup

import java.io.File

/**
 * Dateibasierter Startversuch-Zähler (`startup_attempts`) für die
 * [CrashLoopPolicy]. Bewusst **ohne Android-Framework** (nur `java.io.File`),
 * damit die Erkennung auch dann funktioniert, wenn genau die Android-/DI-Ebene
 * die Crash-Ursache ist.
 *
 * **Fail-open bei Korruption/Fehlen:** Ein unlesbarer oder fehlender Zähler
 * gilt als `0` (frischer Start) — ein defekter Zähler darf niemanden fälschlich
 * in den Safe-Mode sperren. Eine echte Crash-Schleife erhöht den Zähler bei
 * jedem weiteren Start erneut und greift damit spätestens beim übernächsten
 * Start wieder.
 *
 * Alle Operationen sind `runCatching`-geschützt — der Zähler darf niemals
 * selbst zum Startabsturz werden.
 */
class CrashLoopGuard(private val file: File) {

    /** Aktueller Zählerstand gescheiterter Starts in Folge (0 = frisch). */
    fun currentAttempts(): Int =
        runCatching {
            if (!file.exists()) 0 else file.readText().trim().toIntOrNull() ?: 0
        }.getOrDefault(0)

    /**
     * Inkrementiert und persistiert sofort (`attempts + 1`). Der Aufrufer tut
     * dies **nur bei [CrashLoopPolicy.Decision.NORMAL]** — ein Safe-Mode-Start
     * (Diagnose-Activity) ist kein „gescheiterter Vollversuch“ und darf den
     * Zähler nicht beliebig aufblähen.
     */
    fun recordAttempt(): Int {
        val next = CrashLoopPolicy.onStartupAttempt(currentAttempts())
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(next.toString())
        }
        return next
    }

    /** UI erreicht: vollständiger Reset auf [CrashLoopPolicy.HEALTHY_RESET]. */
    fun markUiReached() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(CrashLoopPolicy.HEALTHY_RESET.toString())
        }
    }
}
