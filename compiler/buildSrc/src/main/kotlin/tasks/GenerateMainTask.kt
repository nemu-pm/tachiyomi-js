package tasks

import MainKtTemplate
import ResolvedSource
import SourceGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Generates the entry points for an extension build:
 *  - `keiyoushi/source/Generated.kt`, what upstream's KSP processor derives from the `@Source`
 *    class and the build.gradle.kts `source {}` specs;
 *  - `Main.kt`, the JS bridge over `Generated`.
 * Configuration-cache compatible.
 */
abstract class GenerateMainTask : DefaultTask() {

    @get:Input
    abstract val extensionPath: Property<String>

    @get:Input
    abstract val sources: ListProperty<ResolvedSource>

    @get:InputDirectory
    abstract val preprocessedDir: DirectoryProperty

    @get:InputFile
    abstract val translationsFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val preprocessed = preprocessedDir.get().asFile
        val extensionDir = File(preprocessed, "eu/kanade/tachiyomi/extension")
        val cls = SourceGenerator.findSourceClass(extensionDir, preprocessed)
        if (cls.root != "KeiSource") {
            println("  WARNING: ${cls.className} does not derive from KeiSource (root: ${cls.root}); filterFetchHint not generated")
        }
        val translations = SourceGenerator.Translations.load(translationsFile.get().asFile)
        val generated = SourceGenerator.generate(cls, sources.get(), translations)
        File(outDir, "keiyoushi/source/Generated.kt").apply { parentFile.mkdirs() }.writeText(generated)

        File(outDir, "Main.kt").writeText(MainKtTemplate.generate())
    }
}
