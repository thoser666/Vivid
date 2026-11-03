package com.vivid.feature.streaming

/**
 * Kamera-Pfad, der einen Fehler gemeldet hat.
 *
 * Bis 02.11.2026 warf der Live-/Stream-Pfad seine Fehler still weg: nur der
 * Idle-Preview-Callback loggte (`"Idle camera: %s"`). Ein Sentry-Event ohne
 * Pfad-Angabe ließ sich deshalb nicht zuordnen — dieselbe RootEncoder-Meldung
 * kann aus beiden Pfaden kommen, und beide haben andere Ursachen (Stream:
 * Encoder hat die Kamera gerade übernommen; Vorschau: Aufbau neben einem
 * laufenden Stream). [label] ist stabil und damit gruppierungstauglich; alles
 * Variable steht in [describeCameraError] auf Breadcrumb-Ebene.
 */
internal enum class CameraErrorPath(val label: String) {
    /** Live-Kamera des Encoders (`MultiCamera2`, Streaming/Recording). */
    STREAM("stream"),

    /** Legacy-Idle-Preview (`Camera2ApiManager` parallel zum Encoder). */
    IDLE_PREVIEW("idlePreview"),
}

/**
 * Momentaufnahme der Kamera-Umgebung zum Zeitpunkt eines Fehlers.
 *
 * [concurrentGroups] sind die Paare/Gruppen aus
 * `CameraManager.getConcurrentCameraIds()` (ab API 30; darunter leer). Sie
 * beantworten die Frage, die `Camera no detected` und `Open camera … failed`
 * unterscheidet: hat das Gerät die angeforderte Kamera überhaupt, und darf sie
 * gleichzeitig mit einer anderen offen sein?
 */
internal data class CameraEnvironment(
    val availableIds: List<String> = emptyList(),
    val concurrentGroups: List<List<String>> = emptyList(),
)

/**
 * Einzeiler für das In-App-Log und die Sentry-Breadcrumbs.
 *
 * Bewusst **kein** eigener `Timber.e`-Aufruf: Sentry gruppiert Log-Events nach
 * der Nachricht (`sentry-android-timber` 8.58, `minEventLevel = ERROR`). Variable
 * Werte wie Kamera-Ids würden pro Kombination eine neue Gruppe erzeugen. Auf
 * `Timber.w` landet der Kontext als Breadcrumb am nächsten Fehler-Event und im
 * In-App-Log, ohne die Gruppenzahl zu erhöhen.
 */
internal fun describeCameraError(
    path: CameraErrorPath,
    cameraId: String?,
    isStreaming: Boolean,
    isRecording: Boolean,
    environment: CameraEnvironment,
): String = buildString {
    append("path=").append(path.label)
    append(" cameraId=").append(cameraId?.takeIf { it.isNotBlank() } ?: "default")
    append(" available=").append(environment.availableIds.joinToString(",", "[", "]"))
    append(" concurrent=").append(
        environment.concurrentGroups.joinToString(";", "[", "]") { it.joinToString(",") },
    )
    append(" streaming=").append(isStreaming)
    append(" recording=").append(isRecording)
}
