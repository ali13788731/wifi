pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")   // Poolakey (Cafe Bazaar billing)
    }
}
rootProject.name = "NetSpeed"
include(":app")
