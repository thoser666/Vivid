package com.vivid.feature.streaming

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.input.gl.render.filters.BeautyFilterRender
import com.pedro.encoder.input.gl.render.filters.BlurFilterRender
import com.pedro.encoder.input.gl.render.filters.CartoonFilterRender
import com.pedro.encoder.input.gl.render.filters.DuotoneFilterRender
import com.pedro.encoder.input.gl.render.filters.EdgeDetectionFilterRender
import com.pedro.encoder.input.gl.render.filters.GreyScaleFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender
import com.pedro.encoder.input.gl.render.filters.NoiseFilterRender
import com.pedro.encoder.input.gl.render.filters.NegativeFilterRender
import com.pedro.encoder.input.gl.render.filters.PixelatedFilterRender
import com.pedro.encoder.input.gl.render.filters.SepiaFilterRender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Callback-Typ: akzeptiert einen [BaseFilterRender] oder null ( zum Entfernen aller Filter).
 */
typealias FilterApplier = (BaseFilterRender?) -> Unit

/**
 * Steuert die OpenGL-Video-Filter über eine Filter-Applicator-Lambda.
 *
 * Die Filter wirken auf den **Encoder-Pfad** (Vorschau + gestreamtes Video),
 * nicht nur auf die Vorschau — das bedeutet, dass der gewählte Effekt auch
 * in dem tatsächlich gestreamten Bild zu sehen ist.
 */
class VideoFilterController {

    private val _activeFilter = MutableStateFlow(VideoFilter.NONE)

    /** Der aktuell aktive Filter. */
    val activeFilter: StateFlow<VideoFilter> = _activeFilter.asStateFlow()

    /**
     * Wendet den angegebenen Filter über den bereitgestellten [applyFilter]-Callback an.
     *
     * @param filter Der zu setzende Filter.
     * @param applyFilter Lambda, das den Renderer an die Camera/Display/Player-Instanz weitergibt.
     * @return true, wenn der Filter erfolgreich gesetzt wurde.
     */
    fun setFilter(filter: VideoFilter, applyFilter: FilterApplier): Boolean {
        return try {
            val render = if (filter == VideoFilter.NONE) null else createFilterRender(filter)
            applyFilter(render)
            _activeFilter.value = filter
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Wechselt zum nächsten Filter in der Liste (zirkulär).
     *
     * @return Der neue aktive Filter.
     */
    fun nextFilter(applyFilter: FilterApplier): VideoFilter {
        val values = VideoFilter.entries
        val nextIndex = (values.indexOf(_activeFilter.value) + 1) % values.size
        val next = values[nextIndex]
        setFilter(next, applyFilter)
        return next
    }

    /**
     * Setzt den Filter-Zustand zurück (ohne Applicator — nur State).
     * Nützlich beim Stoppen des Streams oder bei Quellen-Wechsel.
     */
    fun resetFilterState() {
        _activeFilter.value = VideoFilter.NONE
    }

    companion object {
        /**
         * Erzeugt den passenden RootEncoder-Renderer für einen [VideoFilter].
         * Jeder Aufruf liefert eine frische Instanz (Filter sind nicht reuse-fähig).
         *
         * @return der Renderer oder null, wenn die Instanziierung fehlschlägt
         *   (z. B. in Unit-Tests ohne Android-Context).
         */
        fun createFilterRender(filter: VideoFilter): BaseFilterRender? = try {
            when (filter) {
                VideoFilter.NONE -> NoFilterRender()
                VideoFilter.GRAYSCALE -> GreyScaleFilterRender()
                VideoFilter.SEPIA -> SepiaFilterRender()
                VideoFilter.NOISE -> NoiseFilterRender()
                VideoFilter.NEGATIVE -> NegativeFilterRender()
                VideoFilter.EDGE_DETECTION -> EdgeDetectionFilterRender()
                VideoFilter.CARTOON -> CartoonFilterRender()
                VideoFilter.PIXELATED -> PixelatedFilterRender()
                VideoFilter.BLUR -> BlurFilterRender()
                VideoFilter.BEAUTY -> BeautyFilterRender()
                VideoFilter.DUOTONE -> DuotoneFilterRender()
            }
        } catch (_: Exception) {
            null
        }
    }
}
