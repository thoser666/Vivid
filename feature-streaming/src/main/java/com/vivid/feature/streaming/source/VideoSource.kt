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
 * nutzt noch keine Fabriken (nur die Kamera ist implementiert); S2 registriert
 * hier die Screen-Capture-Quelle, S3 (Video-Player) folgt später über dieselbe
 * Schnittstelle.
 */
fun interface VideoSourceFactory {
    fun create(kind: VideoSourceKind): VideoSource?
}

/**
 * Zentraler Registry für die aktive Videoquelle.
 *
 * S1: Die Kamera ist die einzige implementierte Quelle — ein Wechsel auf
 * Screen-Capture/Video-Player wurde mit false abgelehnt. Ab S2 werden Quellen
 * über [registerFactory] nach [VideoSourceKind] registriert; [switchTo] löst die
 * passende Fabrik auf und macht die erzeugte Quelle aktiv. Eine nicht
 * registrierte Quelle (z. B. Video-Player vor S3) wird weiterhin abgelehnt.
 */
@Singleton
class VideoSourceRegistry @Inject constructor() {

    private val factories = mutableMapOf<VideoSourceKind, VideoSourceFactory>()

    private val _activeKind = MutableStateFlow<VideoSourceKind>(VideoSourceKind.CAMERA)

    /** Die aktuell aktive Quelle (Default: CAMERA). */
    val activeKind: StateFlow<VideoSourceKind> = _activeKind.asStateFlow()

    private val _activeSource = MutableStateFlow<VideoSource?>(null)

    /** Die aktuell aktive [VideoSource]-Instanz (null für die eingebaute Kamera). */
    val activeSource: StateFlow<VideoSource?> = _activeSource.asStateFlow()

    /**
     * Registriert eine Fabrik für [kind]. Mehrfach-Registrierung ersetzt die
     * vorherige Fabrik (z. B. nach einer Neu-Initialisierung der Quelle).
     */
    fun registerFactory(kind: VideoSourceKind, factory: VideoSourceFactory) {
        factories[kind] = factory
    }

    /**
     * Wechselt die aktive Quelle.
     *
     * Die Kamera ist immer verfügbar. Für andere Quellen wird die registrierte
     * [VideoSourceFactory] aufgelöst; liefert sie eine Quelle, wird diese aktiv.
     * Ohne registrierte Fabrik (oder wenn die Fabrik keine Quelle liefert) wird
     * der Wechsel abgelehnt (false) und der Zustand bleibt unverändert.
     */
    fun switchTo(kind: VideoSourceKind): Boolean {
        if (kind == VideoSourceKind.CAMERA) {
            _activeKind.value = VideoSourceKind.CAMERA
            _activeSource.value = null
            return true
        }
        val factory = factories[kind] ?: return false
        val source = factory.create(kind) ?: return false
        _activeKind.value = kind
        _activeSource.value = source
        return true
    }
}