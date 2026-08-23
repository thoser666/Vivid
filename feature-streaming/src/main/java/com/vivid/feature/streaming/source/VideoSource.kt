package com.vivid.feature.streaming.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quell-Typ einer Videoquelle — die Abstraktionsschicht für das Moblin-Bucket
 * „Screen Capture + Video-Player als Videoquelle“ (README/PARITY).
 *
 * S1 (dieser Schritt): Modell + Registry + Engine-Anbindung. Nur [CAMERA] ist
 * implementiert; [SCREEN_CAPTURE] (MediaProjection) und [VIDEO_PLAYER] werden in
 * den Folgeschritten S2/S3 über dieselbe Schnittstelle ergänzt, ohne dass sich
 * die Engine ändern muss.
 */
enum class VideoSourceKind {
    CAMERA,
    SCREEN_CAPTURE,
    VIDEO_PLAYER,
}

/**
 * Eine abstrakte Videoquelle. Implementierungen kapseln Start/Stopp und
 * liefern den [kind] für die Registry — die Engine spricht nur noch diese
 * Schnittstelle, nicht mehr direkt „die Kamera“.
 */
interface VideoSource {
    /** Der Typ der Quelle (Kamera, Screen-Capture, Video-Player). */
    val kind: VideoSourceKind

    /** true, solange die Quelle aktiv läuft. */
    val isActive: Boolean

    /** Startet die Quelle. false, wenn die Quelle nicht startbar ist. */
    fun start(): Boolean

    /** Stoppt die Quelle. */
    fun stop(): Boolean
}

/**
 * Fabrik für [VideoSource]s, nach [VideoSourceKind] aufgelöst. S1: die Registry
 * nutzt noch keine Fabriken (nur die Kamera ist implementiert); S2/S3 registrieren
 * hier MediaProjection- und Video-Player-Fabriken.
 */
interface VideoSourceFactory {
    fun create(kind: VideoSourceKind): VideoSource?
}

/**
 * Zentraler Registry für die aktive Videoquelle.
 *
 * S1: Die Kamera ist die einzige implementierte Quelle — ein Wechsel auf
 * Screen-Capture/Video-Player wird mit false abgelehnt, der Zustand bleibt
 * unverändert. Ab S2 wird hier per [VideoSourceFactory]-Lookup die passende
 * Quelle erzeugt und gestartet.
 */
@Singleton
class VideoSourceRegistry @Inject constructor() {

    private val _activeKind = MutableStateFlow<VideoSourceKind>(VideoSourceKind.CAMERA)

    /** Die aktuell aktive Quelle (Default: CAMERA). */
    val activeKind: StateFlow<VideoSourceKind> = _activeKind.asStateFlow()

    /**
     * Wechselt die aktive Quelle. S1: nur [VideoSourceKind.CAMERA] wird akzeptiert,
     * alles andere wird abgelehnt (false), der Zustand bleibt unverändert.
     */
    fun switchTo(kind: VideoSourceKind): Boolean {
        if (kind != VideoSourceKind.CAMERA) return false
        _activeKind.value = kind
        return true
    }
}