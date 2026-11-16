package com.vivid.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Ersatz-Daten für eine unlesbare `settings.preferences_pb`.
 *
 * DataStore meldet eine beschädigte Datei als `CorruptionException` — der
 * Top-Frame ist Bibliothekscode (`FileStorage.kt#runFileDiagnosticsIfNotCorruption`,
 * Sentry VIVID-5F/5G/5K → #432). Ohne Handler ist der Default der
 * ReThrow-Handler: die Ausnahme erreicht jeden Leser, und weder
 * `SettingsRepository` noch `SceneRepository` fangen sie an `dataStore.data`
 * ab — der Prozess stirbt. Mit diesem Handler wird die Datei einmal durch
 * Defaults ersetzt und der Start läuft weiter.
 *
 * `internal` statt privat, damit der Test denselben Handler fährt, den der
 * Delegate unten registriert (Verdrahtung, nicht nur Verhalten).
 */
internal val settingsCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
    Timber.w(it, "settings.preferences_pb was corrupt — file replaced with defaults")
    emptyPreferences()
}

// Definiert den Namen der Einstellungsdatei
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler,
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext appContext: Context): DataStore<Preferences> {
        return appContext.dataStore
    }
}
