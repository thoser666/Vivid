package com.vivid.core.i18n

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** The languages shipped by Vivid, plus the device's language preference. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    GERMAN("de"),
    ENGLISH("en"),
    FRENCH("fr"),
    RUSSIAN("ru");

    companion object {
        private const val PREFS_NAME = "app_language"
        private const val PREF_KEY = "selected_language"

        fun current(context: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(PREF_KEY, "").orEmpty()
            }
            if (tag.isBlank()) return SYSTEM
            val language = Locale.forLanguageTag(tag.substringBefore(',')).language
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }

        /** Android 13+ keeps this in sync with the system's per-app language setting. */
        fun select(context: Context, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language.tag)
            } else {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit { putString(PREF_KEY, language.tag) }
            }
        }

        /** Before Android 13, give each Activity localized resources at creation. */
        // Suppress ist bewusst begruendet: AppBundleLocaleChanges verlangt
        // Play-Core-Sprach-Downloads ODER deaktiviertes Locale-Splitting.
        // Die Loesung lebt im App-Modul (app/build.gradle.kts, bundle {
        // language { enableSplit = false }}) — dort ist sie fuer beide
        // Distributionen (Play/AAB + F-Droid/APK) korrekt; dieses
        // Library-Modul kann die Bundle-Konfiguration nicht tragen.
        @SuppressLint("AppBundleLocaleChanges")
        fun wrapBaseContext(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val language = current(base)
            if (language == SYSTEM) return base

            val locale = Locale.forLanguageTag(language.tag)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return base.createConfigurationContext(configuration)
        }
    }
}
