package com.vivid.core.data

// Diese Datenklasse MUSS alle Felder enthalten, die die UI braucht.
data class AppSettings(
    val streamUrl: String = "",
    val streamKey: String = "",
    val obsHost: String = "",
    val obsPort: String = "",
    val obsPassword: String = "",
)
