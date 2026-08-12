package com.vivid.feature.streaming.di

import com.vivid.core.remote.StreamControl
import com.vivid.feature.streaming.CameraFactory
import com.vivid.feature.streaming.RtmpCamera2Factory
import com.vivid.feature.streaming.StreamingEngineStreamControl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingFeatureBindingModule {

    @Binds
    @Singleton
    abstract fun bindCameraFactory(
        factoryImpl: RtmpCamera2Factory,
    ): CameraFactory

    @Binds
    @Singleton
    abstract fun bindStreamControl(
        impl: StreamingEngineStreamControl,
    ): StreamControl
}

@Module
@InstallIn(SingletonComponent::class)
object StreamingFeatureScopeModule {

    @Provides
    @Singleton
    fun provideStreamControlScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
