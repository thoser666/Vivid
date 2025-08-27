package com.vivid.irlbroadcaster // Or your app's base package

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VividApplication : Application() {
    // You can add onCreate logic here if needed
    // For example, initializing other libraries
    override fun onCreate() {
        super.onCreate()
        // Initialize Sentry or other global services here if you wish
        //io.sentry.Sentry.init(...)
    }
}