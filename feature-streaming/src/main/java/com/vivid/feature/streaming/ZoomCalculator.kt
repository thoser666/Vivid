package com.vivid.feature.streaming

/** Reine Pinch-Zoom-Logik, unabhängig von Android-Klassen unit-testbar. */
object ZoomCalculator {

    /** Kleinster Zoom-Faktor (ungezoomt). */
    const val MIN_ZOOM = 1.0f

    /**
     * Begrenzt [value] auf [range]. Ungültige Werte (NaN/Infinity) oder ein
     * invertierter Bereich fallen auf den unteren Bereichswert zurück.
     */
    fun clamp(value: Float, range: ZoomRange): Float {
        if (value.isNaN() || value.isInfinite()) return range.min
        if (range.min > range.max) return range.min
        return value.coerceIn(range.min, range.max)
    }

    /**
     * Neuer Zoom aus [currentZoom] × [scaleFactor] (Pinch-Geste des
     * ScaleGestureDetectors), begrenzt auf [range].
     */
    fun zoomForScale(currentZoom: Float, scaleFactor: Float, range: ZoomRange): Float {
        if (scaleFactor.isNaN() || scaleFactor.isInfinite()) return clamp(currentZoom, range)
        return clamp(currentZoom * scaleFactor, range)
    }
}
