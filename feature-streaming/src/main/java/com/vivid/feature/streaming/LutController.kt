package com.vivid.feature.streaming

import android.graphics.Bitmap
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Callback-Typ: akzeptiert einen [BaseFilterRender] oder null (zum Entfernen aller Filter).
 */
typealias LutApplier = (BaseFilterRender?) -> Unit

/**
 * Verfügbare LUT-Presets (vordefinierte Farbtabellen).
 */
enum class LutPreset(
    /** Erzeugt die LUT als Bitmap. */
    val createLut: (size: Int) -> Bitmap,
) {
    NONE({ size -> HaldClutFilterRender.generateIdentityLut(size) }),
    WARM({ size -> HaldClutFilterRender.generateWarmToneLut(size) }),
    COOL({ size -> HaldClutFilterRender.generateCoolToneLut(size) }),
}

/**
 * Controller für den 3D-LUT-Filter (Hald-CLUT):
 * - Lädt eine Hald-CLUT-Textur und gibt sie als OpenGL-Filter aus
 * - Unterstützt vordefinierte Presets (Warm/Cool) und benutzerdefinierte LUTs
 * - Kombiniert mit ColorSpace-Auswahl (Gamma-Korrektur)
 *
 * Die Filter wirken auf den **Encoder-Pfad** (Vorschau + gestreamtes Video),
 * da RootEncoder's `setFilter` die GL-Pipeline beider Pfade speist.
 * (Verifiziert: RootEncoder Wiki „If you want add a filter to stream you
 * only need use setFilter method".)
 */
class LutController {

    private val _activePreset = MutableStateFlow(LutPreset.NONE)
    val activePreset: StateFlow<LutPreset> = _activePreset.asStateFlow()

    private val _activeColorSpace = MutableStateFlow(ColorSpace.SRGB)
    val activeColorSpace: StateFlow<ColorSpace> = _activeColorSpace.asStateFlow()

    /** Der aktive LUT-Render oder null (wenn deaktiviert). */
    private var activeRender: BaseFilterRender? = null

    /**
     * Aktive benutzerdefinierte LUT (aus [loadCustomLut]) — der Rebuild des
     * [PrivacyComposer] rekonstruiert den LUT-Slot aus dem Zustand; das
     * Custom-Bitmap ist kein Preset (activePreset = NONE) und muss deshalb
     * separat gehalten werden. Wird bei Preset-/Reset-Wechsel verworfen.
     */
    private var customLutBitmap: Bitmap? = null

    /**
     * Setzt den LUT-Preset.
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setPreset(preset: LutPreset, lutSize: Int, applyLut: LutApplier): Boolean {
        if (_activePreset.value == preset && customLutBitmap == null) return false
        customLutBitmap = null
        _activePreset.value = preset
        val colorSpace = _activeColorSpace.value
        val render = createLutRender(preset, lutSize, colorSpace)
        activeRender = render
        applyLut(render)
        return true
    }

    /**
     * Setzt den Color-Space (ändert die Gamma-Korrektur).
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setColorSpace(colorSpace: ColorSpace, lutSize: Int, applyLut: LutApplier): Boolean {
        if (_activeColorSpace.value == colorSpace) return false
        _activeColorSpace.value = colorSpace
        val preset = _activePreset.value
        val render = createLutRender(preset, lutSize, colorSpace)
        activeRender = render
        applyLut(render)
        return true
    }

    /**
     * Schaltet den LUT-Filter um (NONE → aktiver Preset, oder deaktivieren).
     *
     * @return true, wenn der LUT jetzt aktiv ist.
     */
    fun toggle(lutSize: Int, applyLut: LutApplier): Boolean {
        return if (_activePreset.value == LutPreset.NONE) {
            setPreset(LutPreset.WARM, lutSize, applyLut)
            true
        } else {
            setPreset(LutPreset.NONE, lutSize, applyLut)
            false
        }
    }

    /**
     * Setzt den LUT-Zustand zurück (ohne Applicator — nur State).
     */
    fun resetState() {
        _activePreset.value = LutPreset.NONE
        customLutBitmap = null
        activeRender = null
    }

    /**
     * Rekonstruiert den LUT-Slot aus dem aktuellen Zustand (für den
     * [PrivacyComposer]-Rebuild): Custom-LUT bevorzugt (falls geladen),
     * sonst der aktive Preset — jeweils als **frische** Render-Instanz
     * (Hausmuster: Filter sind nicht reuse-fähig). `null`, wenn kein LUT
     * aktiv ist oder die Erzeugung fehlschlägt (z. B. JVM-Unit-Test).
     */
    internal fun createActiveLutRender(lutSize: Int): BaseFilterRender? = try {
        val bitmap = customLutBitmap
        if (bitmap != null) {
            HaldClutFilterRender(bitmap, lutSize, _activeColorSpace.value.gamma)
        } else {
            createLutRender(_activePreset.value, lutSize, _activeColorSpace.value)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Lädt eine benutzerdefinierte LUT aus einem Bitmap.
     *
     * @return true, wenn die LUT erfolgreich geladen wurde.
     */
    fun loadCustomLut(bitmap: Bitmap, lutSize: Int, applyLut: LutApplier): Boolean {
        return try {
            val colorSpace = _activeColorSpace.value
            val render = HaldClutFilterRender(bitmap, lutSize, colorSpace.gamma)
            activeRender = render
            customLutBitmap = bitmap
            _activePreset.value = LutPreset.NONE // Custom ist kein Preset
            applyLut(render)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /**
         * Erzeugt den passenden LUT-Render für einen Preset.
         */
        fun createLutRender(
            preset: LutPreset,
            lutSize: Int,
            colorSpace: ColorSpace,
        ): BaseFilterRender? = try {
            if (preset == LutPreset.NONE) {
                null // Identität = kein Filter
            } else {
                val bitmap = preset.createLut(lutSize)
                HaldClutFilterRender(bitmap, lutSize, colorSpace.gamma)
            }
        } catch (_: Exception) {
            null
        }
    }
}
