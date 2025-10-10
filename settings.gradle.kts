pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Für RootEncoder
    }
}

rootProject.name = "Vivid"
include(":app")
include(":core")
include(":domain")
include(":data")
include(":feature-streaming")
include(":feature-settings")
include(":feature-chat")
include(":feature-widgets")
include(":feature-obs-control")