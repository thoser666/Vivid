package com.vivid.core.network.obs.requests

/**
 * OBS WebSocket 5.x-Requests für die OBS-Steuerung (PARITY Row „Snapshot /
 * Audio-Levels / Audio-Sync auslesen" → Mute/Unmute + Screen-black).
 *
 * `Map<String, Any>`-Felder (z. B. `inputSettings`) werden von Gson direkt
 * in ein JSON-Objekt serialisiert; `null`-Felder (hier z. B. [TakeSourceScreenshot.imageWidth])
 * werden von Gson standardmäßig weggelassen.
 */

class GetInputList : Request

data class GetInputMute(
    val inputName: String,
) : Request

data class SetInputMute(
    val inputName: String,
    val inputMuted: Boolean,
) : Request

/** Antwort liefert den neuen Zustand `inputMuted` → fließt in die Mute-Flow. */
data class ToggleInputMute(
    val inputName: String,
) : Request

data class GetInputSettings(
    val inputName: String,
) : Request

data class SetInputSettings(
    val inputName: String,
    val inputSettings: Map<String, Any>,
    val overlay: Boolean = false,
) : Request

class GetSceneList : Request

class GetCurrentProgramScene : Request

data class SetCurrentProgramScene(
    val sceneName: String,
) : Request

data class CreateScene(
    val sceneName: String,
) : Request

data class CreateInput(
    val sceneName: String,
    val inputName: String,
    val inputKind: String,
    val inputSettings: Map<String, Any> = emptyMap(),
    val sceneItemEnabled: Boolean = true,
) : Request

/**
 * Snapshot des aktuellen Szenen-Inhalts. Antwort `img` (Base64-PNG bzw.
 * -JPEG je [imageFormat]) fließt in die Snapshot-Flow des Clients.
 */
data class TakeSourceScreenshot(
    val sourceName: String,
    val imageFormat: String = "png",
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
) : Request