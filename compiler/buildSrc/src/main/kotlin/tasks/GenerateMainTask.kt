package tasks

import MainKtTemplate
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Generates Main.kt entry point for the extension.
 * Configuration-cache compatible.
 */
abstract class GenerateMainTask : DefaultTask() {
    
    @get:Input
    abstract val extensionPath: Property<String>
    
    @get:Input
    abstract val extClassName: Property<String>
    
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    
    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()
        
        val extPath = extensionPath.get()
        val packageName = "eu.kanade.tachiyomi.extension.${extPath.replace("/", ".")}"
        
        val outputFile = File(outDir, "Main.kt")
        val content = MainKtTemplate.generate(packageName, extClassName.get())
        
        outputFile.writeText(content)
    }
}

