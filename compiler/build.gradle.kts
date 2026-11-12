// Toolchain pins are documented in shim/build.gradle.kts; keep both files in sync.
plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
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
    js {
        if (isExtensionBuild) {
            // Kotlin 2.4 defaults the JS module name to "{group}:{project}"; BundleTask reads
            // the file named after the extension path.
            outputModuleName.set(extensionPath!!.replace("/", "-"))
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

    // Upstream build-logic opts every extension into ExperimentalSerializationApi; the
    // other covers kotlin.time.Instant/Clock.
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        val jsMain by getting {
            if (isExtensionBuild) {
                kotlin.srcDir(generatedSrcDir)
                kotlin.srcDir(preprocessedSrcDir)
            }

            dependencies {
                implementation("tachiyomi.shim:tachiyomi-shim")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.11.0")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.4")
            }
        }
    }
}
