package com.vivid.feature.widget.di

import android.content.Context
import com.vivid.core.data.SpeechToTextEngine
import com.vivid.feature.widget.AndroidSpeechToTextEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetSpeech

@Module
@InstallIn(ViewModelComponent::class)
object WidgetSpeechModule {
    @Provides @ViewModelScoped @WidgetSpeech
    fun speech(@ApplicationContext context: Context): SpeechToTextEngine = AndroidSpeechToTextEngine(context)
}
