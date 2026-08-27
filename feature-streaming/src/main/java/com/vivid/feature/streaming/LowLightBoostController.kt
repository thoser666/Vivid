package com.vivid.feature.streaming

import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Callback-Typ: akzeptiert einen [BaseFilterRender] oder null (zum Entfernen aller Filter).
 */
typealias BoostApplier = (BaseFilterRender?) -> Unit

/**
 * Stellt einen software-basierten Low-Light-Boost bereit: ein OpenGL-Brightness-Filter,
 * der die Helligkeit des gesamten Frames anhebt (Vorschau + Encoder). Funktioniert auf
 * allen Videoquellen (Kamera, Screen-Capture, Video-Player), nicht nur auf der Kamera.
 *
 * Der Boost ergänzt den Hardware-Torch — falls die Kamera eine Taschenlampe hat,
 * kann der Nutzer beide unabhängig voneinander aktivieren.
 *
 * Moblin-Parität: „Low-Light-Boost (Helligkeits-/Gain-Anhebung bei schlechten
 * Lichtverhältnissen)“ — nutzt einen eigenen GLSL-Brightness-Filter statt eines
 * der RootEncoder-Mitgelieferten, da RootEncoder keinen eingebauten
 * Brightness-Filter bereitstellt.
 */
class LowLightBoostController {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Der aktive Boost-Render oder null (wenn Boost deaktiviert). */
    private var activeRender: BaseFilterRender? = null

    /**
     * Schaltet den Low-Light-Boost um.
     *
     * @return true, wenn der Boost jetzt aktiv ist.
     */
    fun toggle(applyBoost: BoostApplier): Boolean {
        val newState = !_enabled.value
        _enabled.value = newState
        if (newState) {
            val render = createBrightnessRender()
            activeRender = render
            applyBoost(render)
        } else {
            activeRender = null
            applyBoost(null)
        }
        return newState
    }

    /**
     * Setzt den Boost auf einen bestimmten Zustand.
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setEnabled(enabled: Boolean, applyBoost: BoostApplier): Boolean {
        if (_enabled.value == enabled) return false
        _enabled.value = enabled
        if (enabled) {
            val render = createBrightnessRender()
            activeRender = render
            applyBoost(render)
        } else {
            activeRender = null
            applyBoost(null)
        }
        return true
    }

    /**
     * Setzt den Boost-Zustand zurück (ohne Applicator — nur State).
     * Nützlich beim Stoppen des Streams.
     */
    fun resetState() {
        _enabled.value = false
        activeRender = null
    }

    companion object {
        /**
         * Erzeugt einen Low-Light-Brightness-Filter. Verwendet einen GLSL-Shader,
         * der die Helligkeit des Frames um einen festen Faktor anhebt.
         *
         * @return der Renderer oder null, wenn die Instanziierung fehlschlägt.
         */
        fun createBrightnessRender(): BaseFilterRender? = try {
            LowLightBrightnessFilterRender()
        } catch (_: Exception) {
            null
        }
    }
}
