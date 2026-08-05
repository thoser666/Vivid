plugins {
    alias(libs.plugins.android.library)
    // Das Serialization-Plugin ist das einzige, das hier wirklich gebraucht wird
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vivid.domain"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Dieses Modul braucht NUR die Serialization-Bibliothek, um @Serializable zu kennen.
    implementation(libs.kotlinx.serialization.json)

    // Alle anderen Abhängigkeiten (Hilt, Coroutines etc.) wurden entfernt.

    // Test-Abhängigkeiten sind in Ordnung
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    // kotlinx.coroutines.test kann hier auch weg, da Models keine Coroutinen testen
}
