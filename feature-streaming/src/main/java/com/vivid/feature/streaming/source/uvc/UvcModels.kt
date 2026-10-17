package com.vivid.feature.streaming.source.uvc

/**
 * USB/UVC-Datenmodell (#305).
 *
 * Reine Kotlin-Typen ohne Android-Abhängigkeiten (vollständig JVM-testbar).
 * Die Bezeichner enthalten keine Geräte-Seriennummern, Pfade oder Secrets —
 * persistiert wird nur der gewünschte Modus als [UvcCaptureMode.preferenceString].
 */

/** UVC-Pixelformat (Nutzer-sichtbar; MJPEG bevorzugt, YUY2 als Fallback). */
enum class UvcPixelFormat {
    MJPEG,
    YUY2,
}

/**
 * Ein vom Gerät gemeldeter Aufnahmemodus.
 *
 * Die Liste stammt immer vom geöffneten Gerät ([UvcCameraGateway.supportedFormats]) —
 * niemals geraten: Nicht gemeldete Auflösungen/FPS/Formate werden nicht angeboten.
 */
data class UvcCaptureMode(
    val width: Int,
    val height: Int,
    val fps: Int,
    val format: UvcPixelFormat,
) {
    /** Persistierbare Darstellung („1280x720@30:MJPEG“) — kein Gerätepfad. */
    fun preferenceString(): String = "${width}x${height}@${fps}:${format.name}"

    companion object {
        /** Parst [UvcCaptureMode.preferenceString]; null bei ungültiger Eingabe. */
        fun parsePreference(value: String?): UvcCaptureMode? {
            if (value.isNullOrBlank()) return null
            val modeAndFormat = value.split(":")
            if (modeAndFormat.size != 2) return null
            val format = runCatching { UvcPixelFormat.valueOf(modeAndFormat[1]) }.getOrNull()
                ?: return null
            val sizeAndFps = modeAndFormat[0].split("@")
            if (sizeAndFps.size != 2) return null
            val wh = sizeAndFps[0].split("x")
            if (wh.size != 2) return null
            val width = wh[0].toIntOrNull() ?: return null
            val height = wh[1].toIntOrNull() ?: return null
            val fps = sizeAndFps[1].toIntOrNull() ?: return null
            if (width <= 0 || height <= 0 || fps <= 0) return null
            return UvcCaptureMode(width, height, fps, format)
        }
    }
}

/**
 * Rohformat-Eintrag des UVC-Helpers (1:1 aus `Format`/`Size` gemappt).
 *
 * [type] ist der Size-Deskriptortyp des Helpers (`UVCCamera.UVC_VS_FRAME_MJPEG = 7`,
 * `UVC_VS_FRAME_UNCOMPRESSED = 5`) — nicht die nativen FRAME_FORMAT_* Werte 0/1.
 */
data class UvcFormatInfo(
    val type: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    companion object {
        const val TYPE_YUY2 = 5
        const val TYPE_MJPEG = 7
    }
}

/**
 * Ein angeschlossenes USB-Videogerät (reine Daten; der [UsbDevice]-Handle lebt
 * in [UvcDeviceRepository] und wird nie persistiert oder geloggt).
 *
 * [stableSessionId] is a runtime UUID retained only while this exact attachment
 * remains enumerated. Paths and serial descriptors are never persisted.
 * Vendor-specific candidates are verified against real modes only after opening.
 */
data class UvcDeviceDescriptor(
    val stableSessionId: String,
    val vendorId: Int,
    val productId: Int,
    val productLabel: String,
    val manufacturerLabel: String? = null,
    val verifiedVideoClass: Boolean = true,
    val modes: List<UvcCaptureMode> = emptyList(),
) {
    /** Persistierbarer Geräteschlüssel (`"vid:pid"`) — keine Seriennummer. */
    fun preferenceKey(): String = "%04x:%04x".format(vendorId, productId)
}

/** Erwartete UVC-Fehler — kein stilles Catch-and-Ignore, sondern expliziter Zustand. */
sealed interface UvcFailure {
    data object CameraPermissionDenied : UvcFailure
    data object PermissionDenied : UvcFailure
    data object DeviceBusy : UvcFailure
    data object UnsupportedMode : UvcFailure
    data object ModeNegotiationFailed : UvcFailure
    data object OpenFailed : UvcFailure
    data object PreviewFailed : UvcFailure
    data object DetachedDuringCapture : UvcFailure
    data object NativeLibraryMissing : UvcFailure
    data object UnsupportedHost : UvcFailure
}

/** Verbindungszustand genau eines UVC-Geräts (ein Owner pro geöffneter Ressource). */
sealed interface UvcConnectionState {
    data object Disconnected : UvcConnectionState
    data class Attached(val device: UvcDeviceDescriptor) : UvcConnectionState
    data class PermissionRequired(val device: UvcDeviceDescriptor) : UvcConnectionState
    data class Opening(val device: UvcDeviceDescriptor) : UvcConnectionState
    data class Ready(val device: UvcDeviceDescriptor, val mode: UvcCaptureMode) : UvcConnectionState
    data class Starting(val device: UvcDeviceDescriptor, val mode: UvcCaptureMode) : UvcConnectionState
    data class Capturing(val device: UvcDeviceDescriptor, val mode: UvcCaptureMode) : UvcConnectionState
    /** Keep the last enumerated modes available so the user can recover by choosing another. */
    data class Error(
        val reason: UvcFailure,
        val device: UvcDeviceDescriptor? = null,
    ) : UvcConnectionState
}

/**
 * Mappt gemeldete Rohformate auf [UvcCaptureMode] — ohne erfundene Optionen:
 * Unbekannte Typen fallen heraus, ungültige Größen/FPS ebenfalls.
 */
fun mapUvcModes(formats: List<UvcFormatInfo>): List<UvcCaptureMode> =
    formats.mapNotNull { info ->
        val format = when (info.type) {
            UvcFormatInfo.TYPE_MJPEG -> UvcPixelFormat.MJPEG
            UvcFormatInfo.TYPE_YUY2 -> UvcPixelFormat.YUY2
            else -> return@mapNotNull null
        }
        if (info.width <= 0 || info.height <= 0 || info.fps <= 0) return@mapNotNull null
        UvcCaptureMode(info.width, info.height, info.fps, format)
    }.distinct().sortedWith(compareBy({ it.format }, { it.width * it.height }, { it.fps }))

/**
 * Vernünftiger Standardmodus: MJPEG bevorzugt (geringere USB-Bandbreite),
 * 1280×720@30 wenn gemeldet, sonst größte MJPEG-Fläche, sonst größte YUY2-Fläche.
 * Konservative Fallbacks (640×480) ergeben sich automatisch, wenn das Gerät
 * nicht mehr meldet — versprochen wird nur Gemeldetes.
 */
fun selectDefaultUvcMode(modes: List<UvcCaptureMode>): UvcCaptureMode? {
    if (modes.isEmpty()) return null
    val mjpeg = modes.filter { it.format == UvcPixelFormat.MJPEG }
    val pool = mjpeg.ifEmpty { modes }
    pool.firstOrNull { it.width == 1280 && it.height == 720 }?.let { exact ->
        return pool.filter { it.width == 1280 && it.height == 720 }
            .minByOrNull { kotlin.math.abs(it.fps - 30) } ?: exact
    }
    return pool.maxWithOrNull(compareBy({ it.width * it.height }, { it.fps }))
}
