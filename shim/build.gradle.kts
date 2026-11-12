// Toolchain pins (keep in sync with compiler/build.gradle.kts):
// - Kotlin 2.4.10: matches keiyoushi/extensions-source build-logic; extensions use
//   context parameters (stable 2.4.0), multi-dollar strings and kotlin.time.Instant (stable 2.3.0).
// - kotlinx.serialization 1.11.0: upstream core opts into ExperimentalSerializationApi globally.
// - ksoup 0.2.4: Jsoup port; bump only when an extension needs a newer API.
plugins {
    kotlin("multiplatform") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
}

group = "tachiyomi.shim"
version = "1.0.0"

kotlin {
    js {
        // Kotlin 2.4 defaults the JS module name to "{group}:{project}"; pin the file name the
        // compiler build and BundleTask look for.
        outputModuleName.set("tachiyomi-shim")
        browser {
            testTask {
                enabled = false
            }
        }
        // Produce a library (.klib) that can be consumed by other Kotlin/JS projects
        binaries.library()
    }

    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.11.0")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.4")
            }
        }
    }
}

// Pre-compile task for CI warmup
tasks.register("compileShim") {
    group = "build"
    description = "Compile the shim library for caching"
    dependsOn("compileKotlinJs")
}

