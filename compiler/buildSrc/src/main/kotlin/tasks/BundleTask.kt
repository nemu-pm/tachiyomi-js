package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Bundles Kotlin/JS output using Bun's bundler (faster than webpack).
 * Configuration-cache compatible via @Inject ExecOperations.
 */
abstract class BundleTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {
    
    @get:InputFile
    abstract val inputFile: RegularFileProperty
    
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
    
    @TaskAction
    fun bundle() {
        val input = inputFile.get().asFile
        val output = outputFile.get().asFile
        
        output.parentFile.mkdirs()
        
        val result = execOps.exec {
            commandLine(
                "bun", "build",
                input.absolutePath,
                "--outfile", output.absolutePath,
                "--minify",
                "--target", "node",
                "--format", "cjs"
            )
            isIgnoreExitValue = true
        }
        
        if (result.exitValue != 0) {
            throw RuntimeException("Bun bundling failed with exit code ${result.exitValue}")
        }
    }
}

