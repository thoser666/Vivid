package com.vivid.feature.streaming

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Eine Personen-Maske für den Umgebungs-Modus (P4.1 der Skizze
 * docs/architecture/privacy-anonymization.md, §11): **ein Byte pro Kanal**,
 * RGBA, gleiche Orientierung wie die Eingabe-Textur des Privacy-Filters.
 *
 * Der Rot-Kanal trägt die Personen-Konfidenz (`255` = Person, `0` =
 * Hintergrund) — genau der Kanal, den der Shader als `uMaskTex` liest. RGBA
 * statt `LUMINANCE`, weil `SceneCompositorFilterRender`/`HaldClutFilterRender`
 * im Haus dasselbe Format hochladen und `GL_LUMINANCE` in GLES 3 nur noch
 * geduldet ist; 256×144 kosten damit 147 kB statt 37 kB.
 *
 * Die Maske ist ein **unveränderlicher Snapshot**: der Render lädt sie beim
 * nächsten `drawFilter()` hoch und hält die Textur dann fest. Ein neuer
 * Snapshot ersetzt den alten vollständig (kein Teil-Update, keine
 * Halbzustände); die Gültigkeit wird im Konstruktor geprüft, damit eine
 * kaputte Maske nie bis zum GL-Upload durchkommt.
 */
class EnvironmentMask(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
) {
    init {
        require(width > 0 && height > 0) {
            "Masken-Größe muss positiv sein, got ${width}x$height"
        }
        require(rgba.size == width * height * RGBA_CHANNELS) {
            "Masken-Puffer passt nicht zu ${width}x$height: ${rgba.size} Bytes " +
                "statt ${width * height * RGBA_CHANNELS}"
        }
    }

    /** Personen-Konfidenz an einer Pixel-Position (0..255, Rot-Kanal). */
    fun personAt(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) {
            "Pixel außerhalb der Maske: $x/$y in ${width}x$height"
        }
        return rgba[(y * width + x) * RGBA_CHANNELS].toInt() and 0xFF
    }

    companion object {
        /** RGBA = 4 Bytes pro Pixel (ein Wert je Kanal, Alpha = 255). */
        const val RGBA_CHANNELS = 4
    }
}

/**
 * Einstellungen der P4.1-Porträt-Aussparung in normalisierten Textur-
 * Koordinaten (0..1, Ursprung unten links wie im Shader).
 *
 * Die Aussparung ist bewusst **kein** Personen-Modell, sondern der sichere
 * Kernfall: eine weiche Ellipse um Kopf und Oberkörper. Alles außerhalb wird
 * verpixelt; verlässt die Person die Ellipse, wird **sie** verpixelt — der
 * Fehler zeigt in Richtung mehr Anonymisierung (Skizze §11.4).
 */
data class PortraitMaskSettings(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.45f,
    val radiusX: Float = 0.28f,
    val radiusY: Float = 0.45f,
    /** Anteil des Radius, über den die Kante ausläuft (0 = hart). */
    val feather: Float = 0.16f,
) {
    init {
        require(centerX in 0f..1f && centerY in 0f..1f) {
            "Zentrum außerhalb 0..1: $centerX/$centerY"
        }
        require(radiusX in 0f..1f && radiusY in 0f..1f && radiusX > 0f && radiusY > 0f) {
            "Radien müssen in (0..1] liegen: $radiusX/$radiusY"
        }
        require(feather in 0f..1f) { "Feather außerhalb 0..1: $feather" }
    }
}

/**
 * Erzeugt die P4.1-Maske ohne Modell: eine weiche Ellipse, innen Person
 * (`255`), außen Hintergrund (`0`), mit [PortraitMaskSettings.feather]
 * auslaufender Kante.
 *
 * Die Maske ist bewusst kleiner als das Bild (256×144): der Shader sampelt
 * sie bilinear und interpoliert die Kante zusätzlich — dieselbe Auflösung,
 * die der P4.2-Segmenter liefert, damit der Textur-Pfad unverändert bleibt,
 * wenn das Modell dazukommt (Skizze §11.3).
 */
object PortraitMaskGenerator {

    /** Kantenlänge der Masken-Textur (16:9, wie der P4.2-Segmenter). */
    const val WIDTH = 256
    const val HEIGHT = 144

    fun create(settings: PortraitMaskSettings = PortraitMaskSettings()): EnvironmentMask {
        val pixels = ByteArray(WIDTH * HEIGHT * EnvironmentMask.RGBA_CHANNELS)
        for (y in 0 until HEIGHT) {
            val v = (y + 0.5f) / HEIGHT
            val dy = (v - settings.centerY) / settings.radiusY
            for (x in 0 until WIDTH) {
                val u = (x + 0.5f) / WIDTH
                val dx = (u - settings.centerX) / settings.radiusX
                val person = personCoverage(hypot(dx, dy), settings.feather)
                val value = (person * 255f).roundToInt().coerceIn(0, 255).toByte()
                val index = (y * WIDTH + x) * EnvironmentMask.RGBA_CHANNELS
                pixels[index] = value
                pixels[index + 1] = value
                pixels[index + 2] = value
                pixels[index + 3] = 255.toByte()
            }
        }
        return EnvironmentMask(WIDTH, HEIGHT, pixels)
    }

    /**
     * Personen-Abdeckung an der normalisierten Ellipsen-Distanz [distance]
     * (`1` = Rand): innen voll, ab `1 - feather` linear auf 0 am Rand.
     */
    internal fun personCoverage(distance: Float, feather: Float): Float {
        if (feather <= 0f) return if (distance < 1f) 1f else 0f
        val edge = 1f - feather
        if (distance <= edge) return 1f
        if (distance >= 1f) return 0f
        val t = (distance - edge) / feather
        // Smoothstep — dieselbe weiche Kante wie im Shader, nur auf der
        // Masken-Seite; vermeidet eine sichtbare Ringkante bei kleinen
        // Masken-Auflösungen.
        return 1f - t * t * (3f - 2f * t)
    }
}

/**
 * Fail-Safe des Umgebungs-Modus (Skizze §11.1/§11.6): solange keine gültige
 * Maske hochgeladen ist, darf der Filter **nicht** auf „nichts“ zurückfallen.
 * Er schaltet dann auf Ganzbild — der Panik-Schalter gewinnt immer.
 *
 * Bewusst eine reine Funktion: die Entscheidung ist damit ohne GL-Kontext
 * prüfbar (der Upload selbst bleibt Geräte-Verifikation).
 */
internal fun environmentFullFrameFallback(
    fullFrame: Boolean,
    environmentEnabled: Boolean,
    maskUploaded: Boolean,
): Boolean = fullFrame || (environmentEnabled && !maskUploaded)
