allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when (requested.group to requested.name) {
                "io.netty" to "netty-handler",
                "io.netty" to "netty-codec-http2",
                "io.netty" to "netty-codec-http",
                "io.netty" to "netty-common",
                "io.netty" to "netty-buffer",
                "io.netty" to "netty-transport" -> useVersion("4.1.137.Final")
                "org.apache.commons" to "commons-lang3" -> useVersion("3.18.0")
                "org.bouncycastle" to "bcprov-jdk18on",
                "org.bouncycastle" to "bcpkix-jdk18on",
                "org.bouncycastle" to "bcutil-jdk18on" -> useVersion("1.85")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}
