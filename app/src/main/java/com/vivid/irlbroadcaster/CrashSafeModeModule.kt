package com.vivid.irlbroadcaster

import android.content.Context
import com.vivid.core.startup.CrashLoopGuard
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * DI-Bindung des Startversuch-Zählers (Startup-Safe-Mode): Eine App-weite
 * [CrashLoopGuard]-Instanz auf `filesDir/startup_attempts` — Application
 * (Entscheidung) und MainActivity (UI-Reset) teilen denselben Zähler.
 */
@Module
@InstallIn(SingletonComponent::class)
object CrashSafeModeModule {

    @Provides
    @Singleton
    fun provideCrashLoopGuard(
        @ApplicationContext context: Context,
    ): CrashLoopGuard = CrashLoopGuard(File(context.filesDir, "startup_attempts"))
}
