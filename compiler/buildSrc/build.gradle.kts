plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        register("tachiyomi") {
            id = "tachiyomi"
            implementationClass = "TachiyomiPlugin"
        }
    }
}
