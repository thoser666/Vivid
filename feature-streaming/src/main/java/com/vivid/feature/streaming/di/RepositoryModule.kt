package com.vivid.feature.streaming.di

import com.vivid.feature.streaming.data.repository.StreamingRepository
import com.vivid.feature.streaming.data.repository.StreamingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStreamingRepository(
        streamingRepositoryImpl: StreamingRepositoryImpl,
    ): StreamingRepository
}
