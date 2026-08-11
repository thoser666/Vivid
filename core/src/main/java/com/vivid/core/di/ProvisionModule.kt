package com.vivid.core.di

import com.google.gson.Gson
import com.vivid.core.network.KtorClientFactory
import com.vivid.core.update.GitHubReleasesApi
import com.vivid.core.update.GitHubReleasesApiImpl
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
}
