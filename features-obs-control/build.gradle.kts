plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vivid.features.obs.control"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk =  rootProject.extra["minSdkVersion"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // Preferences DataStore
    implementation(libs.androidx.datastore.preferences)

    // Jetpack Compose dependencies
    implementation(platform(libs.androidx.compose.bom)) // BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3) // Using Material 3 as per your SettingsScreen
    implementation(libs.androidx.activity.compose)


    // Hilt Navigation Compose (already in your SettingsScreen.kt imports)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.lifecycle.viewmodel.ktx) // Or use libs.androidx.lifecycle.viewmodel.ktx if defined in libs.versions.toml

    // Hilt dependencies (if you added the Hilt plugin)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio) // Oder eine andere Engine wie OkHttp
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)

    // Wenn du JSON mit Ktor serialisieren willst (was sehr wahrscheinlich ist):
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.timberkt)

    // You should also have the base Timber library here
    implementation(libs.timber)

    implementation(libs.obs.ws.kotlin)
    implementation(libs.obs.ws.client)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ... test dependencies
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(project(":core"))
}
