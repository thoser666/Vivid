package com.vivid.core.network.obs.requests

// Alle Request-Typen, die der Client senden kann (OBS WebSocket 5.x).
// Der Env-Name ist zugleich der `requestType`, der im Wire-Format transportiert wird.
enum class RequestType {
    GetVersion,

    // Inputs / Mute / Audio
    GetInputList,
    GetInputMute,
    SetInputMute,
    ToggleInputMute,
    GetInputSettings,
    SetInputSettings,

    // Szenen / Bild
    GetSceneList,
    GetCurrentProgramScene,
    SetCurrentProgramScene,
    CreateScene,
    CreateInput,
    TakeSourceScreenshot,
}