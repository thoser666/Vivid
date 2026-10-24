package com.vivid.core.data

/** Fixed app orientation, controlled exclusively from Appearance settings. */
enum class AppOrientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun fromName(name: String?): AppOrientation = entries.firstOrNull { it.name == name } ?: PORTRAIT
    }
}
