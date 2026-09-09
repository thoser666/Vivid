package com.vivid.feature.widget.di

import com.vivid.feature.widget.AndroidGeocoderResolver
import com.vivid.feature.widget.GeocoderResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DI-Bindung für das Reverse-Geocoding des Text-Widgets. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetsBindingModule {

    @Binds
    @Singleton
    abstract fun bindGeocoderResolver(
        impl: AndroidGeocoderResolver,
    ): GeocoderResolver
}
