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
    val metadata: ExtensionMetadata,
    val theme: ThemeMetadata?,
    val multisrcPath: File?,
    val libPaths: List<File>,
    /** `assets/` folders packaged into the APK upstream (extension, theme, libs); served from ResourceRegistry here. */
    val assetDirs: List<File>,
    val iconFile: File?,
    val translationsFile: File,
) {
    val packageName: String get() = metadata.packageName(extensionPath)
    val versionCode: Int get() = metadata.resolvedVersionCode(theme)
    val versionName: String get() = metadata.versionName(theme)

    companion object {
        /** Libraries the shim already provides; their upstream sources are not compiled in. */
        private val shimmedLibs = setOf("i18n", "cronet")

        /**
         * Resolve extension configuration from DSL, properties, and metadata.
         */
        fun resolve(
            extensionPath: String,
            extensionsRoot: String,
            outputDir: String
        ): ExtensionConfig {
            val extRootFile = File(extensionsRoot)
            val extDir = File(extRootFile, "src/$extensionPath")
            require(extDir.isDirectory) { "extension not found: $extDir" }

            val metadata = ExtensionMetadata.parse(File(extDir, "build.gradle.kts"))
            val theme = metadata.theme?.let { ExtensionMetadata.parseTheme(File(extRootFile, "lib-multisrc/$it/build.gradle.kts")) }

            val extensionSourceDir = File(extDir, "src")
            val multisrcPath = metadata.theme?.let { File(extRootFile, "lib-multisrc/$it/src") }
            val libPaths = (metadata.libDeps + (theme?.libDeps ?: emptyList()))
                .distinct()
                .filter { it !in shimmedLibs }
                .map { File(extRootFile, "lib/$it/src") }
                .filter { it.exists() }

            val assetDirs = (listOf(File(extDir, "assets")) +
                listOfNotNull(metadata.theme?.let { File(extRootFile, "lib-multisrc/$it/assets") }) +
                (metadata.libDeps + (theme?.libDeps ?: emptyList())).distinct().map { File(extRootFile, "lib/$it/assets") })
                .filter { it.isDirectory }

            // Find icon
            val extensionResPath = File(extDir, "res")
            val iconDensities = listOf("xxhdpi", "hdpi", "xhdpi", "xxxhdpi", "mdpi")
            val iconFile = iconDensities
                .map { File(extensionResPath, "mipmap-$it/ic_launcher.png") }
                .firstOrNull { it.exists() }

            return ExtensionConfig(
                extensionPath = extensionPath,
                extensionsRoot = extRootFile,
                outputDir = File(outputDir, extensionPath),
                extensionSourceDir = extensionSourceDir,
                metadata = metadata,
                theme = theme,
                multisrcPath = multisrcPath?.takeIf { it.exists() },
                libPaths = libPaths,
                assetDirs = assetDirs,
                iconFile = iconFile,
                translationsFile = File(extRootFile, "core/translations/strings.json"),
            )
        }
    }
}
