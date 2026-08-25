package com.vivid.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.gson.Gson
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogStore
import com.vivid.core.network.KtorClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.vivid.core.update.DataStoreUpdateCheckCache
import com.vivid.core.update.GitHubReleasesApi
import com.vivid.core.update.GitHubReleasesApiImpl
import com.vivid.core.update.UpdateCheckCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProvisionModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideKtorHttpClient(): HttpClient {
        return KtorClientFactory.create()
    }

    @Provides
    @Singleton
    fun provideGitHubReleasesApi(client: HttpClient): GitHubReleasesApi {
        return GitHubReleasesApiImpl(client)
    }

    @Provides
    @Singleton
    fun provideUpdateCheckCache(dataStore: DataStore<Preferences>): UpdateCheckCache {
        return DataStoreUpdateCheckCache(dataStore)
    }

    @Provides
    @Singleton
    fun provideLogBuffer(): LogBuffer {
        // Dieselbe Instanz, die VividApplication in den Timber-Tree pflanzt.
        return LogBuffer.instance
    }

    @Provides
    @Singleton
    fun provideLogStore(@ApplicationContext context: Context): LogStore {
        // Tägliche Log-Dateien im App-internen Verzeichnis (kein externer Zugriff).
        return LogStore(File(context.filesDir, "logs"))
    }
}
