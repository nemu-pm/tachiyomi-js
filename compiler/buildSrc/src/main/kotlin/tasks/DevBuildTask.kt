package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Generates the extension manifest and copies assets.
 * Configuration-cache compatible.
 */
abstract class DevBuildTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val extensionPath: Property<String>

    @get:Input
    abstract val extName: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    /** `theme.baseVersionCode + versionCode`, the number upstream bumps per release. */
    @get:Input
    abstract val versionCode: Property<Int>

    /** `"$libVersion.$versionCode"`, upstream's `versionName`. */
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val libVersion: Property<String>

    /** SAFE, MIXED or NSFW. */
    @get:Input
    abstract val contentWarning: Property<String>

    @get:Input
    @get:Optional
    abstract val theme: Property<String>

    @get:Input
    abstract val deeplinkHosts: ListProperty<String>

    @get:InputDirectory
    abstract val preprocessedDir: DirectoryProperty

    @get:InputFile
    @get:Optional
    abstract val iconFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val extPath = extensionPath.get()
        val lang = extPath.substringBefore("/")
        val outDir = outputDir.get().asFile
        val ppDir = preprocessedDir.get().asFile

        outDir.mkdirs()

        // Copy icon if available
        val icon = iconFile.orNull?.asFile
        val hasIcon = icon?.exists() == true
        if (hasIcon) {
            icon!!.copyTo(File(outDir, "icon.png"), overwrite = true)
        }

        // Detect WebView and Cloudflare usage
        var usesWebView = false
        var usesCloudflare = false
        ppDir.walk().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            if (content.contains("WebView") || content.contains("evaluateJavascript") || content.contains("localStorage")) {
                usesWebView = true
            }
            if (content.contains("cloudflareClient")) {
                usesCloudflare = true
            }
        }

        if (usesWebView) println("  ⚠️  Uses WebView (may require auth)")
        if (usesCloudflare) println("  ⚠️  Uses Cloudflare client (may have challenges)")

        // Extract sources from compiled JS
        val extractScript = """const ext = require('./extension.js').tachiyomi.generated; const r = JSON.parse(ext.getManifest()); if (r.ok) console.log(JSON.stringify(r.data)); else { console.error(JSON.stringify(r.error)); process.exit(1); }"""
        val sourcesOutput = ByteArrayOutputStream()
        val result = execOps.exec {
            workingDir(outDir)
            commandLine("bun", "-e", extractScript)
            standardOutput = sourcesOutput
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw RuntimeException("extension.js failed to load (getManifest), see output above")
        }
        val sourcesJson = sourcesOutput.toString().trim().let {
            if (it.isNotEmpty() && it.startsWith("[")) it else "[]"
        }

        // Android versionCode as upstream computes it: libVersion packed as two-digit parts * 1000 + versionCode.
        val code = libVersion.get().split(".").joinToString("") { it.padStart(2, '0') }.toInt() * 1000 + versionCode.get()

        // Generate manifest.json (authors added by tachiyomi-js-sources post-processing)
        val manifestFile = File(outDir, "manifest.json")
        val manifestContent = buildString {
            appendLine("{")
            appendLine("  \"name\": ${jsonString(extName.get())},")
            appendLine("  \"pkg\": ${jsonString(packageName.get())},")
            appendLine("  \"lang\": ${jsonString(lang)},")
            appendLine("  \"version\": ${versionCode.get()},")
            appendLine("  \"versionName\": ${jsonString(versionName.get())},")
            appendLine("  \"code\": $code,")
            appendLine("  \"contentWarning\": ${jsonString(contentWarning.get())},")
            appendLine("  \"nsfw\": ${contentWarning.get() == "NSFW"},")
            theme.orNull?.let { appendLine("  \"theme\": ${jsonString(it)},") }
            appendLine("  \"hasWebView\": $usesWebView,")
            appendLine("  \"hasCloudflare\": $usesCloudflare,")
            appendLine("  \"deeplinkHosts\": [${deeplinkHosts.get().joinToString(", ") { jsonString(it) }}],")
            if (hasIcon) {
                appendLine("  \"icon\": \"icon.png\",")
            }
            appendLine("  \"jsPath\": \"extension.js\",")
            appendLine("  \"sources\": $sourcesJson")
            append("}")
        }
        manifestFile.writeText(manifestContent)

        val sourceCount = sourcesJson.count { it == '{' }
        println("\n✓ Built to: $extPath/")
        println("  - extension.js")
        println("  - manifest.json ($sourceCount sources)")
        if (hasIcon) println("  - icon.png")
        println("\n  Test: tachiyomi test popular $extPath\n")
    }

    private fun jsonString(s: String): String = "\"" + s
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
}
