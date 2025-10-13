package com.vivid.feature.streaming.di

import com.vivid.feature.streaming.CameraFactory
import com.vivid.feature.streaming.RtmpCamera2Factory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingFeatureBindingModule {

    @Binds
    @Singleton
    abstract fun bindCameraFactory(
        factoryImpl: RtmpCamera2Factory
    ): CameraFactory
}