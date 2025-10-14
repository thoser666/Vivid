package com.vivid.core.di

import com.vivid.core.network.VividApi
import com.vivid.core.network.VividApiImpl
import com.vivid.core.repository.StreamingRepository
import com.vivid.core.repository.StreamingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    @Singleton
    abstract fun bindStreamingRepository(
        impl: StreamingRepositoryImpl,
    ): StreamingRepository

    @Binds
    @Singleton
    abstract fun bindVividApi(
        impl: VividApiImpl,
    ): VividApi
}
