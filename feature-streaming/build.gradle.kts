plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.feature.streaming"

    lint {
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        warningsAsErrors = true
    }
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        // Robolectric: gemergtes Manifest + Ressourcen in die JVM-Tests laden
        // (nötig für FileProvider-Metadaten im shareIntent-Test)
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Abhängigkeit zum Core-Modul, das die Repositories etc. bereitstellt
    implementation(project(":core"))
    // Domain-Modell (AppSettings) für die Stream-Einstellungen
    implementation(project(":domain"))
    // Chat-Overlay über der Streaming-Vorschau
    implementation(project(":feature-chat"))
    // Text-/Info-Widget über der Streaming-Vorschau (Uhrzeit/GPS/Geschwindigkeit)
    implementation(project(":feature-widgets"))

    // AndroidX Core (ContextCompat u. a. für Runtime-Permissions)
    implementation(libs.androidx.core.ktx)

    // UI und Navigation für dieses Feature
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)


    // Hilt für das ViewModel
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // =============================================================
    // HIER SIND DIE KORREKTEN, AUFGETEILTEN ABHÄNGIGKEITEN
    // WIE IM GITHUB-ISSUE BESCHRIEBEN.
    // 'ConnectCheckerRtmp' ist im ':rtmp'-Modul.
    implementation(libs.rootencoder.encoder)
    implementation(libs.rootencoder.library)
    implementation(libs.rootencoder.rtmp)
    // =============================================================

    // Media3 / ExoPlayer für die Wiedergabe
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Logging (Timber, statt android.util.Log)
    implementation(libs.timber)

    // Test-Abhängigkeiten
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.core.testing)

    // Robolectric: echte android.graphics.Bitmap/Intent/FileProvider-APIs auf der JVM.
    // Robolectric ist JUnit4-basiert -> läuft hier über den Vintage-Engine auf der
    // JUnit-Platform (das Modul testet ansonsten mit Jupiter).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric.core)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.vintage.engine)
}
