package com.vivid.feature.streaming

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.library.view.GlStreamInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Zentraler Filter-Ketten-Composer (P0 der Skizze
 * docs/architecture/privacy-anonymization.md, §4): baut die GL-Filterkette
 * **kanonisch** aus dem Soll-Zustand der vier Komponenten wieder auf:
 *
 * `[Privacy? (Position 0)] + [VideoFilter?] + [LUT?] + [LowLight?]`
 *
 * Motivation: Die drei Bestands-Controller (VideoFilter/LUT/LowLight)
 * exklusivierten sich bisher über den Single-Slot (`GlInterface.setFilter`)
 * und deaktivierten mit `clearFilters()` — zwei aktive Filter kämpften um
 * denselben Slot, und jede Deaktivierung löschte die ganze Kette. Der
 * Composer ersetzt diese Einzelfilter-Aufrufe durch einen vollständigen
 * Rebuild: jede Änderung an irgendeinem Slot triggert [requestRebuild],
 * das den **aktuellen** Soll-Zustand aller Slots liest und die Kette in
 * einem Zug (`clearFilters()` + `addFilter(...)` in Kanon-Reihenfolge)
 * neu aufbaut. Racing-Events konsolidieren sich damit automatisch (jeder
 * Rebuild ist ein vollständiger Zustands-Read).
 *
 * **Frische Instanzen pro Rebuild** für die kreativen Filter (Hausmuster der
 * Render-Fabriken: Filter sind nicht reuse-fähig) über die Bestands-Fabriken
 * — als injizierbare Defaults ([videoFilterRenderFactory] usw.), damit die
 * Kanon-Reihenfolge auch JVM-testbar ist (echte Render-Konstruktoren werfen
 * auf der JVM; im Test treten Mocks ein).
 *
 * **Garantie-Postulat:** Die Anonymisierung ist kein kreativer Filter — sie
 * steht immer an Position 0 (vor LUT/VideoFilter/LowLight, damit kreative
 * Effekte das unkenntlich gemachte Bild nicht wieder „entschärfen“). Ihre
 * Render-Instanz wird über alle Rebuilds **wiederverwendet** (nur die
 * Ellipsen-Uniforms ändern sich framesynchron — Uniform-Zustand, kein
 * Ketten-Zustand).
 *
 * Die Controller selbst bleiben unverändert (State + Render-Fabriken); nur
 * ihre Applier-Lambdas in der [StreamingEngine] rufen jetzt
 * [requestRebuild] statt direkt `setFilter`/`clearFilters`. Bei genau einem
 * aktiven kreativen Filter entsteht exakt dieselbe Kette wie zuvor — die
 * Bestands-Engine-Tests (`StreamingEngineFiltersTest`) bleiben unverändert
 * grün.
 *
 * P0-Umfang: die Ellipsen des Privacy-Filters werden nur programmatisch
 * gesetzt ([setEllipses]) — Zonen-UI folgt in P1, Gesichter in P2 (Route A),
 * der Voll-Frame-Modus in P2b ([setFullFrame]).
 */
class PrivacyComposer(
    private val filterController: VideoFilterController,
    private val lowLightBoostController: LowLightBoostController,
    private val lutController: LutController,
    private val lutSize: Int,
    /** Fabrik der wiederverwendeten Privacy-Render-Instanz (Position 0). */
    private val privacyRenderFactory: () -> PrivacyBlurFilterRender? = { createPrivacyRender() },
    /** Fabrik für frische VideoFilter-Renders pro Rebuild (Bestands-Factory). */
    private val videoFilterRenderFactory: (VideoFilter) -> BaseFilterRender? = {
        VideoFilterController.createFilterRender(it)
    },
    /** Fabrik für frische LUT-Renders pro Rebuild (Bestands-Zustand). */
    private val lutRenderFactory: () -> BaseFilterRender? = {
        lutController.createActiveLutRender(lutSize)
    },
    /** Fabrik für frische LowLight-Renders pro Rebuild (Boost aktiv?). */
    private val lowLightRenderFactory: () -> BaseFilterRender? = {
        if (lowLightBoostController.enabled.value) {
            LowLightBoostController.createBrightnessRender()
        } else {
            null
        }
    },
) {

    /** External GL overlays must survive effect rebuilds in the experimental capture path. */
    @Volatile var preserveExternalFilters: Boolean = false
    private val managedFilters = java.util.WeakHashMap<GlStreamInterface, List<BaseFilterRender>>()

    private val _privacyEnabled = MutableStateFlow(false)

    /** Ob die Anonymisierung aktiv ist (Position 0 in der Kette). */
    val privacyEnabled: StateFlow<Boolean> = _privacyEnabled.asStateFlow()

    /**
     * Voll-Frame-Modus (P2b): das ganze Bild wird mit der groben Mosaik-
     * Stufe pixeliert. Wie die Ellipsen reiner Render-Zustand (Uniforms) —
     * kein Ketten-Zustand, deshalb über Rebuilds hinweg erhalten.
     */
    @Volatile
    private var fullFrameEnabled = false

    /** Test-Lesegröße des Voll-Frame-Soll-Zustands. */
    internal val fullFrame: Boolean
        get() = fullFrameEnabled

    /**
     * Die wiederverwendete Privacy-Render-Instanz. Bewusst genau einmal
     * erzeugt und über alle Rebuilds behalten (Ellipsen-Uniforms bleiben
     * erhalten, kein Ketten-Flackern). `null`, wenn die Instanziierung
     * fehlschlägt (z. B. JVM-Unit-Test ohne GL) — der Privacy-Slot fällt
     * dann aus, der Rebuild bleibt wohlgeformt.
     */
    private val privacyRender: PrivacyBlurFilterRender? = privacyRenderFactory()

    /**
     * Baut die Filterkette kanonisch neu auf (siehe Klassen-Doku). Aufrufer
     * ist die Engine mit dem bereits aufgelösten [GlStreamInterface] — ohne
     * Kamera/GL ist der Aufruf ein No-Op (Idle-Guard liegt in der Engine).
     */
    @Synchronized
    fun requestRebuild(gl: GlStreamInterface?) {
        if (gl == null) return
        val desired = mutableListOf<BaseFilterRender>()
        // Position 0: Anonymisierung (immer zuerst — Garantie-Postulat).
        if (_privacyEnabled.value) {
            privacyRender?.let { desired.add(it) }
        }
        // Kreative Filter in fester Kanon-Reihenfolge, frische Instanzen aus
        // den Bestands-Fabriken (JVM: null → Slot fällt aus).
        filterController.activeFilter.value
            .takeIf { it != VideoFilter.NONE }
            ?.let { videoFilterRenderFactory(it) }
            ?.let { desired.add(it) }
        lutRenderFactory()?.let { desired.add(it) }
        lowLightRenderFactory()?.let { desired.add(it) }

        if (preserveExternalFilters) {
            managedFilters[gl].orEmpty().forEach { gl.removeFilter(it) }
            // Own effects remain before external overlays, with privacy at position zero.
            desired.forEachIndexed { index, filter -> gl.addFilter(index, filter) }
        } else {
            gl.clearFilters()
            desired.forEach { gl.addFilter(it) }
        }
        managedFilters[gl] = desired
    }

    /**
     * Setzt die Anonymisierung auf [enabled] und rebuilt die Kette.
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setPrivacyEnabled(enabled: Boolean, gl: GlStreamInterface?): Boolean {
        if (_privacyEnabled.value == enabled) return false
        _privacyEnabled.value = enabled
        requestRebuild(gl)
        return true
    }

    /**
     * Ersetzt die Ellipsen-Zonen (framesynchron über Uniform-Update — kein
     * Ketten-Rebuild nötig, solange der Privacy-Render in der Kette ist; ist
     * er es noch nicht, gilt der Zustand beim nächsten Rebuild).
     */
    fun setEllipses(ellipses: List<PrivacyEllipse>) {
        privacyRender?.setEllipses(ellipses)
    }

    /**
     * Schaltet den Voll-Frame-Modus (P2b, Skizze §5) um: das **gesamte**
     * Bild wird pixeliert, mit der groben Mosaik-Stufe
     * [PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID] statt der feinen
     * Zonen-Stufe — damit Umgebung und Architektur nicht lesbar bleiben.
     * Framesynchron wie [setEllipses], ohne Ketten-Rebuild.
     */
    fun setFullFrame(enabled: Boolean) {
        fullFrameEnabled = enabled
        privacyRender?.setFullFrame(enabled)
        privacyRender?.setPixelGrid(
            if (enabled) {
                PrivacyBlurFilterRender.FULL_FRAME_PIXEL_GRID
            } else {
                PrivacyBlurFilterRender.DEFAULT_PIXEL_GRID
            },
        )
    }

    companion object {
        /**
         * Erzeugt den Privacy-Render. `null`, wenn die Instanziierung
         * fehlschlägt (JVM-Unit-Test ohne GL) — wie die übrigen Render-
         * Fabriken im Haus (VideoFilterController/LutController/LowLight).
         */
        fun createPrivacyRender(): PrivacyBlurFilterRender? = try {
            PrivacyBlurFilterRender()
        } catch (_: Exception) {
            null
        }
    }
}
