plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("tachiyomi")
}

group = "tachiyomi.js"
version = "1.0.0"

// Extension path from CLI (e.g., -Pextension=en/mangapill)
val extensionPath: String? = project.findProperty("extension")?.toString()
val isExtensionBuild = extensionPath != null

// Generated/preprocessed directories (per-extension for proper incremental compilation)
val generatedSrcDir = layout.buildDirectory.dir("generated/$extensionPath/src/jsMain/kotlin")
val preprocessedSrcDir = layout.buildDirectory.dir("preprocessed/$extensionPath/src")

kotlin {
    js(IR) {
        if (isExtensionBuild) {
            moduleName = extensionPath!!.replace("/", "-")
        }
        
        browser {
            webpackTask { enabled = false }  // We use bun bundler
            testTask { enabled = false }
        }
        
        if (isExtensionBuild) {
            binaries.executable()
        } else {
            binaries.library()
        }
    }
    
    sourceSets {
        val jsMain by getting {
            if (isExtensionBuild) {
                kotlin.srcDir(generatedSrcDir)
                kotlin.srcDir(preprocessedSrcDir)
            }
            
            dependencies {
                implementation("tachiyomi.shim:tachiyomi-shim")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.4")
            }
        }
    }
}
