package tasks

import CodeTransformations
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Preprocesses extension source files: adds imports, applies transformations.
 * Configuration-cache compatible.
 */
abstract class PreprocessSourceTask : DefaultTask() {
    
    @get:Input
    abstract val extensionPath: Property<String>
    
    @get:Input
    @get:Optional
    abstract val themePkg: Property<String>
    
    @get:Input
    abstract val libDeps: ListProperty<String>
    
    @get:InputDirectory
    abstract val extensionSourceDir: DirectoryProperty
    
    @get:InputDirectory
    @get:Optional
    abstract val multisrcPath: DirectoryProperty
    
    @get:Input
    abstract val libPaths: ListProperty<String>
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @TaskAction
    fun preprocess() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        
        val theme = themePkg.orNull
        val libs = libDeps.get()
        val multisrc = multisrcPath.orNull?.asFile
        
        if (theme != null) {
            println("  themePkg: $theme -> ${multisrc?.path}")
        }
        if (libs.isNotEmpty()) {
            println("  lib deps: ${libs.joinToString(", ")}")
        }
        
        val shimmedFiles = CodeTransformations.shimmedFiles + 
            CodeTransformations.shimmedFiles.map { it.replace("/", File.separator) }
        val imports = CodeTransformations.jsImports
        
        fun processSourceDir(sourceDir: File) {
            if (!sourceDir.exists()) {
                println("  WARNING: Source dir not found: $sourceDir")
                return
            }
            sourceDir.walk().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    if (file.name.contains("Activity")) return@forEach
                    
                    val relativePath = file.relativeTo(sourceDir)
                    if (relativePath.path in shimmedFiles || 
                        relativePath.path.replace(File.separator, "/") in shimmedFiles) {
                        println("  Skipping shimmed file: ${relativePath.path}")
                        return@forEach
                    }
                    
                    val outFile = File(outDir, relativePath.path)
                    outFile.parentFile.mkdirs()
                    
                    var content = file.readText()
                    
                    // Add imports after package declaration
                    val packageMatch = Regex("""^(package\s+[\w.]+)\s*\n""", RegexOption.MULTILINE).find(content)
                    if (packageMatch != null) {
                        val insertPos = packageMatch.range.last + 1
                        val importsToAdd = imports.filter { imp -> !content.contains(imp) }.joinToString("\n")
                        if (importsToAdd.isNotEmpty()) {
                            content = content.substring(0, insertPos) + "\n" + importsToAdd + "\n" + content.substring(insertPos)
                        }
                    }
                    
                    // Apply transformations
                    for ((pattern, replacement) in CodeTransformations.transforms) {
                        content = pattern.replace(content, replacement)
                    }
                    
                    outFile.writeText(content)
                }
            }
        }
        
        // Process in order: libs, multisrc, extension
        // Note: keiyoushi/utils comes from our shim (JS-compatible implementations)
        libPaths.get().map { File(it) }.forEach { processSourceDir(it) }
        multisrc?.let { processSourceDir(it) }
        processSourceDir(extensionSourceDir.get().asFile)
    }
}

