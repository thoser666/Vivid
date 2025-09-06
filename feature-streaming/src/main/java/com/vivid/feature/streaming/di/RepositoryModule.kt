package com.vivid.feature.streaming.di

import com.vivid.feature.streaming.data.repository.StreamingRepository
import com.vivid.feature.streaming.data.repository.StreamingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Assuming StreamingRepository and StreamingRepositoryImpl are defined elsewhere
// interface StreamingRepository
// class StreamingRepositoryImpl @Inject constructor() : StreamingRepository

@Module
@InstallIn(SingletonComponent::class) // CRITICAL: Tells Hilt where to install this module
internal abstract class RepositoryModule { // `internal` for better encapsulation

    @Binds
    @Singleton
    abstract fun bindStreamingRepository(
        streamingRepositoryImpl: StreamingRepositoryImpl,
    ): StreamingRepository
}
