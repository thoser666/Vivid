package com.vivid.core.data

// Diese Datenklasse MUSS alle Felder enthalten, die die UI braucht.
data class AppSettings(
    val streamUrl: String = "",
    val streamKey: String = "",
    // false = rtmp:// (Klartext), true = rtmps:// (RTMP über TLS)
    val streamUseTls: Boolean = false,
    val obsHost: String = "localhost",
    val obsPort: String = "4455",
    val obsPassword: String = "",
    // false = ws:// (Standard-OBS-LAN ohne TLS), true = wss:// (Remote mit TLS)
    val obsUseTls: Boolean = false,
)
