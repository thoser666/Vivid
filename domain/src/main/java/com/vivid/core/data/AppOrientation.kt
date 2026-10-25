package com.vivid.core.data

/** Fixed streaming-screen orientation, controlled from Appearance; menus follow device rotation. */
enum class AppOrientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun fromName(name: String?): AppOrientation = entries.firstOrNull { it.name == name } ?: PORTRAIT
    }
}
