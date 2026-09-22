package com.vivid.irlbroadcaster

/**
 * Prozess-lokaler Safe-Mode-Status (Startup-Safe-Mode, siehe
 * `com.vivid.core.startup.CrashLoopPolicy`): VividApplication entscheidet beim
 * Start anhand des Startversuch-Zählers, ob der Safe-Mode greift, und
 * spiegelt die Entscheidung hier — [MainActivity] liest sie und leitet in die
 * [CrashDiagnosticsActivity] weiter. Bewusst in-prozess (kein Disk-Lookup in
 * der Activity): Beide Komponenten teilen denselben Prozess.
 *
 * Der „Vollständigen Start versuchen“-Button der Diagnose setzt das Flag
 * zurück, damit der Ausflug in die volle UI nicht sofort zurückRedirectet.
 */
object CrashSafeModeState {

    /** `true` = Startversuch als Crash-Schleife erkannt → Diagnose statt UI. */
    @Volatile
    var active: Boolean = false
}
