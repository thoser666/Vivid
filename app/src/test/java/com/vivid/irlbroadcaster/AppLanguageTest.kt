package com.vivid.irlbroadcaster

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vivid.core.i18n.AppLanguage
import com.vivid.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "ru", application = Application::class)
class AppLanguageTest {

    @Test
    fun `legacy language choice localizes activity resources and can follow system again`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            assertEquals(AppLanguage.SYSTEM, AppLanguage.current(context))
            AppLanguage.select(context, AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, AppLanguage.current(context))
            assertEquals(
                "Appearance",
                AppLanguage.wrapBaseContext(context).getString(R.string.cat_appearance_title),
            )

            AppLanguage.select(context, AppLanguage.SYSTEM)
            assertEquals(AppLanguage.SYSTEM, AppLanguage.current(context))
            assertEquals("Внешний вид", AppLanguage.wrapBaseContext(context).getString(R.string.cat_appearance_title))

            AppLanguage.select(context, AppLanguage.RUSSIAN)
            assertEquals(AppLanguage.RUSSIAN, AppLanguage.current(context))
            assertEquals("Внешний вид", AppLanguage.wrapBaseContext(context).getString(R.string.cat_appearance_title))
        } finally {
            AppLanguage.select(context, AppLanguage.SYSTEM)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru", application = Application::class)
class AppLanguagePlatformTest {

    @Test
    fun `language choice updates Android per-app locale and can return to system`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localeManager = context.getSystemService(LocaleManager::class.java)
        try {
            AppLanguage.select(context, AppLanguage.ENGLISH)
            assertEquals("en", localeManager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.ENGLISH, AppLanguage.current(context))

            AppLanguage.select(context, AppLanguage.RUSSIAN)
            assertEquals("ru", localeManager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.RUSSIAN, AppLanguage.current(context))

            AppLanguage.select(context, AppLanguage.SYSTEM)
            assertEquals("", localeManager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.SYSTEM, AppLanguage.current(context))
        } finally {
            AppLanguage.select(context, AppLanguage.SYSTEM)
        }
    }
}


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en", application = Application::class)
class AppLanguageResolutionTest {

    @Test
    fun `german and french selections resolve their enum and localized resources`() {
        // Ergaenzung zum Bestandstest (EN/RU): Auch die DE-/FR-Zweige muessen
        // Legacy-Rundtrips sauber aufloesen (Picker bietet alle vier Sprachen).
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            AppLanguage.select(context, AppLanguage.GERMAN)
            assertEquals(AppLanguage.GERMAN, AppLanguage.current(context))
            assertEquals(
                "Darstellung",
                AppLanguage.wrapBaseContext(context).getString(com.vivid.feature.settings.R.string.cat_appearance_title),
            )

            AppLanguage.select(context, AppLanguage.FRENCH)
            assertEquals(AppLanguage.FRENCH, AppLanguage.current(context))
            assertEquals(
                "Apparence",
                AppLanguage.wrapBaseContext(context).getString(com.vivid.feature.settings.R.string.cat_appearance_title),
            )
        } finally {
            AppLanguage.select(context, AppLanguage.SYSTEM)
        }
    }

    @Test
    fun `foreign language tag falls back to system`() {
        // Nicht unterstuetzte Sprache (z. B. nach Downgrade einer alten
        // Installation): current() muss auf SYSTEM fallen, nicht crashen.
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
            .edit().putString("selected_language", "es").commit()

        assertEquals(AppLanguage.SYSTEM, AppLanguage.current(context))
    }

    @Test
    fun `multi-language tag resolves by first segment`() {
        // "de-DE,fr" (Multi-Tag, wie vom System gespeichert): entscheidend ist
        // das erste Segment inkl. Regions-Präfix (de-DE -> de -> GERMAN).
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
            .edit().putString("selected_language", "de-DE,fr").commit()

        assertEquals(AppLanguage.GERMAN, AppLanguage.current(context))
    }
}
