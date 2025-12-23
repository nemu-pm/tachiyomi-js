import org.gradle.api.provider.Property
import java.io.File

/**
 * DSL extension for configuring Tachiyomi extension builds.
 * 
 * Usage in build.gradle.kts:
 * ```
 * tachiyomi {
 *     extensionsRoot.set("/path/to/extensions-source")
 *     outputDir.set("/path/to/output")
 * }
 * ```
 */
abstract class TachiyomiExtension {
    /** Root of keiyoushi extensions-source repo (contains src/, lib/, lib-multisrc/, core/) */
    abstract val extensionsRoot: Property<String>
    
    /** Output directory for built extensions */
    abstract val outputDir: Property<String>
    
    /** Optional path to authors script (for tachiyomi-js-sources) */
    abstract val authorsScript: Property<String>
}

/**
 * Resolved configuration for building a specific extension.
 * Created by resolving TachiyomiExtension + command-line properties + metadata parsing.
 */
data class ExtensionConfig(
    val extensionPath: String,
    val extensionsRoot: File,
    val outputDir: File,
    val extensionSourceDir: File,
    val extClassName: String,
    val extName: String,
    val extVersionCode: Int,
    val isNsfw: Boolean,
    val themePkg: String?,
    val multisrcPath: File?,
    val libPaths: List<File>,
    val iconFile: File?
) {
    companion object {
        /**
         * Resolve extension configuration from DSL, properties, and metadata.
         */
        fun resolve(
            extensionPath: String,
            extensionsRoot: String,
            outputDir: String
        ): ExtensionConfig {
            val extRootFile = File(extensionsRoot)
            val extName = extensionPath.substringAfterLast("/")
            
            // Parse metadata from extension's build.gradle
            val buildGradleFile = File(extRootFile, "src/$extensionPath/build.gradle")
            val metadata = ExtensionMetadata.parse(buildGradleFile)
            
            // Resolve paths
            val extensionSourceDir = File(extRootFile, "src/$extensionPath/src")
            val multisrcPath = metadata.themePkg?.let { File(extRootFile, "lib-multisrc/$it/src") }
            val libPaths = metadata.libDeps.map { libName -> 
                File(extRootFile, "lib/$libName/src/main/java") 
            }.filter { it.exists() }
            
            // Find icon
            val extensionResPath = File(extRootFile, "src/$extensionPath/res")
            val iconDensities = listOf("xxhdpi", "hdpi", "xhdpi", "xxxhdpi", "mdpi")
            val iconFile = iconDensities
                .map { File(extensionResPath, "mipmap-$it/ic_launcher.png") }
                .firstOrNull { it.exists() }
            
            return ExtensionConfig(
                extensionPath = extensionPath,
                extensionsRoot = extRootFile,
                outputDir = File(outputDir, extensionPath),
                extensionSourceDir = extensionSourceDir,
                extClassName = metadata.extClass ?: extName.replaceFirstChar { it.uppercase() },
                extName = metadata.extName ?: extName,
                extVersionCode = metadata.extVersionCode ?: 1,
                isNsfw = metadata.isNsfw ?: false,
                themePkg = metadata.themePkg,
                multisrcPath = multisrcPath?.takeIf { it.exists() },
                libPaths = libPaths,
                iconFile = iconFile
            )
        }
    }
}

