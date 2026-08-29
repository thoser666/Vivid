plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    // Sentry plugin only for standard builds (FOSS builds don't use Sentry)
    // The fossBuild property is set via -PfossBuild=true for F-Droid builds
    alias(libs.plugins.sentry.android.gradle)
    // Roborazzi plugin temporarily disabled due to AGP 9.x compatibility
    // alias(libs.plugins.roborazzi)
}

// Sentry configuration - only active for standard builds
// FOSS builds will have Sentry disabled at runtime
sentry {
    // Skip ProGuard mapping upload when token is missing (e.g. nightly CI builds).
    // Token is provided by android_fastlane.yml and android.yml workflows.
    autoUploadProguardMapping.set(System.getenv("SENTRY_AUTH_TOKEN") != null)
}

android {
    namespace = "com.vivid"

    lint {
        // Blocking-Check: Lint bricht den Build bei jeder Warnung/Fehler ab.
        // Versionshinweise (GradleDependency/NewerVersionAvailable/AndroidGradlePluginVersion)
        // sind informativ — Dependency-Updates laufen über Dependabot.
        // OldTargetApi: SDK-Upgrade bewusst.
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            // values-fr/ ist bewusst partiell (nice-to-have, siehe docs/i18n-plan.md §3) —
            // die Pflicht-Sprachen sind values/ (de) + values-en/ (vollständig, CI-geprüft).
            "MissingTranslation",
        )
        warningsAsErrors = true
    }

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.vivid"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Overridable via -PversionCode= / -PversionName= (CI nightly/stable builds);
        // defaults keep local builds stable.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Nur warnen, wenn tatsächlich ein Release-Build (Signing nötig)
                // angefordert wird — Tests/Lint/Debug laufen ohne KEYSTORE_PATH und
                // sind kein Fehler (kein Warn-Rauschen in jedem CI-Schritt).
                val wantsReleaseBuild = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (wantsReleaseBuild) {
                    println("⚠️ Release signing not configured - using debug keystore")
                }
            }
        }
        create("upload") {
            // Play-Kanal: signiert das AAB fuer Google Play App Signing, bewusst
            // getrennt vom Release-Key (UPLOAD_*-Secrets, nie KEYSTORE_*).
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_PATH")
            val keystorePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            val keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            // Standard build with Sentry for GitHub/Obtainium/Play Store
            isDefault = true
            buildConfigField("Boolean", "FOSS_BUILD", "false")
        }
        create("foss") {
            dimension = "distribution"
            // FOSS build without Sentry for F-Droid main repository
            // No tracking, no telemetry, fully open-source
            applicationIdSuffix = ".foss"
            buildConfigField("Boolean", "FOSS_BUILD", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile != null) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("playRelease") {
            initWith(getByName("release"))
            // AAB fuer den Play-Kanal: Upload-Key statt Release-Key.
            // Library-Module (core, data, domain, feature-*) kennen nur debug/release:
            // ohne matchingFallbacks schlaegt die Varianten-Aufloesung fehl (kein
            // playRelease-Variant in den Libraries). Fallback auf deren release-Variante.
            matchingFallbacks += listOf("release")
            val uploadConfig = signingConfigs.getByName("upload")
            signingConfig = if (uploadConfig.storeFile != null) {
                uploadConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    // Core Modules
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    // Feature Modules
    implementation(project(":feature-streaming"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-chat"))
    implementation(project(":feature-widgets"))
    implementation(project(":feature-obs-control"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler.ksp)
    implementation(libs.androidx.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)

    // Image Loading (Coil für Inline-Emotes im Chat-Overlay)
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    // fastlane screengrab: erzeugt die Play-Store-Screenshots per UI-Test
    // (Lane `capture_play_screenshots`, siehe fastlane/Fastfile)
    androidTestImplementation(libs.screengrab)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
