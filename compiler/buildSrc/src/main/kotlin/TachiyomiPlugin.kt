import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import tasks.*
import java.io.File

/**
 * Convention plugin for building Tachiyomi extensions.
 * 
 * Registers tasks: preprocessSource, generateMain, bundleExtension, devBuild
 * 
 * Usage:
 *   ./gradlew devBuild -Pextension=en/mangapill -PextensionsRoot=/path/to/source -PoutputDir=/path/to/output
 */
class TachiyomiPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Create DSL extension
        val extension = project.extensions.create<TachiyomiExtension>("tachiyomi")
        
        // Only register tasks if building a specific extension
        project.afterEvaluate {
            val extensionPath = project.findProperty("extension")?.toString() ?: return@afterEvaluate
            
            // Resolve config from properties (CLI takes precedence over DSL)
            val extensionsRoot = project.findProperty("extensionsRoot")?.toString()
                ?: extension.extensionsRoot.orNull
                ?: error("extensionsRoot not set. Pass -PextensionsRoot=/path/to/extensions-source")
            
            val outputDir = project.findProperty("outputDir")?.toString()
                ?: extension.outputDir.orNull
                ?: error("outputDir not set. Pass -PoutputDir=/path/to/output")
            
            val config = ExtensionConfig.resolve(extensionPath, extensionsRoot, outputDir)
            
            registerTasks(project, config)
        }
    }
    
    private fun registerTasks(project: Project, config: ExtensionConfig) {
        val generatedSrcDir = project.layout.buildDirectory.dir("generated/${config.extensionPath}/src/jsMain/kotlin")
        val preprocessedSrcDir = project.layout.buildDirectory.dir("preprocessed/${config.extensionPath}/src")
        
        // Preprocess source files
        val preprocessSource = project.tasks.register<PreprocessSourceTask>("preprocessSource") {
            extensionPath.set(config.extensionPath)
            themePkg.set(config.themePkg)
            libDeps.set(config.libPaths.map { it.name })
            extensionSourceDir.set(config.extensionSourceDir)
            config.multisrcPath?.let { multisrcPath.set(it) }
            libPaths.set(config.libPaths.map { it.absolutePath })
            outputDir.set(preprocessedSrcDir)
        }
        
        // Generate Main.kt
        val generateMain = project.tasks.register<GenerateMainTask>("generateMain") {
            extensionPath.set(config.extensionPath)
            extClassName.set(config.extClassName)
            outputDir.set(generatedSrcDir)
        }
        
        // Hook into Kotlin compilation
        project.tasks.named("compileKotlinJs") {
            dependsOn(generateMain)
            dependsOn(preprocessSource)
        }
        
        // Module name uses dash separator
        val moduleNameDashed = config.extensionPath.replace("/", "-")
        
        // Bundle with Bun
        val bundleExtension = project.tasks.register<BundleTask>("bundleExtension") {
            dependsOn("compileProductionExecutableKotlinJs")
            inputFile.set(project.layout.buildDirectory.file("compileSync/js/main/productionExecutable/kotlin/$moduleNameDashed.js"))
            outputFile.set(File(config.outputDir, "extension.js"))
        }
        
        // Final build task
        project.tasks.register<DevBuildTask>("devBuild") {
            dependsOn(bundleExtension)
            extensionPath.set(config.extensionPath)
            extName.set(config.extName)
            extVersionCode.set(config.extVersionCode)
            nsfw.set(config.isNsfw)
            preprocessedDir.set(preprocessedSrcDir)
            config.iconFile?.let { iconFile.set(it) }
            this.outputDir.set(config.outputDir)
        }
    }
}
