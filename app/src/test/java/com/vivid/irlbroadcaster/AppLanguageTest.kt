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
            assertEquals("Darstellung", AppLanguage.wrapBaseContext(context).getString(R.string.cat_appearance_title))
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

            AppLanguage.select(context, AppLanguage.SYSTEM)
            assertEquals("", localeManager.applicationLocales.toLanguageTags())
            assertEquals(AppLanguage.SYSTEM, AppLanguage.current(context))
        } finally {
            AppLanguage.select(context, AppLanguage.SYSTEM)
        }
    }
}
