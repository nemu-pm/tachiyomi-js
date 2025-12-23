pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Include the shim library as a composite build
// This allows Gradle to build and cache it separately, then reuse for all extensions
includeBuild("../shim")

rootProject.name = "tachiyomi-js"

