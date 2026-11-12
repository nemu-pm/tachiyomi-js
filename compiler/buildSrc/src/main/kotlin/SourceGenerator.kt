import java.io.File

/**
 * Emits `keiyoushi/source/Generated.kt`, the class upstream's KSP `SourceProcessor` produces
 * from the `@Source` class and the resolved `source {}` specs. Replicates
 * `compiler/src/main/kotlin/keiyoushi/processor/SourceProcessor.kt` in extensions-source:
 *
 * - one source and an abstract `@Source` class: `Generated : Ext()` with the members below;
 * - otherwise `Generated : SourceFactory` creating one anonymous subclass (abstract class) or
 *   constructor call (concrete class, args for whichever of name/lang/id/baseUrl are primary
 *   constructor params) per source;
 * - members (only when nothing between the class and `HttpSource` already overrides them):
 *   `filterFetchHint`, `name`, `lang`, `id`, `baseUrl` (static) or `MirrorPreferences` /
 *   `CustomUrlPreferences` wiring with `setupPreferenceScreen` and `fetchableBaseUrl`.
 *
 * Class hierarchies are read textually from the preprocessed sources, so a member overridden
 * by a theme (e.g. Madara's `setupPreferenceScreen`) is seen the same way KSP sees it.
 */
object SourceGenerator {
    data class SourceClass(
        val packageName: String,
        val className: String,
        val isAbstract: Boolean,
        /** Primary constructor parameter names, in order. */
        val ctorParams: List<String>,
        /** Members already overridden anywhere between the class and the shim base classes. */
        val overridden: Set<String>,
        /** Name of the first superclass that is not in the sources (expected: KeiSource). */
        val root: String?,
    )

    private val trackedMembers = listOf(
        "name", "lang", "id", "versionId", "baseUrl", "filterFetchHint", "fetchableBaseUrl",
        "fetchBaseUrls", "setupPreferenceScreen",
    )

    private class ClassDecl(val file: File, val packageName: String, val name: String, val header: String, val body: String) {
        val isAbstract: Boolean get() = Regex("""\babstract\s+class\b""").containsMatchIn(header)

        val superClass: String?
            get() {
                val supers = header.substringAfter(":", "").substringBeforeLast("{")
                return Regex("""(?<![\w.])(\w+)(?:<[^>]*>)?\s*\(""").find(supers)?.groupValues?.get(1)
            }

        val ctorParams: List<String>
            get() {
                val ctor = Regex("""^[^:{]*?\bclass\s+\w+\s*(?:<[^>]*>)?\s*\(((?:[^()]|\([^()]*\))*)\)""", RegexOption.DOT_MATCHES_ALL)
                    .find(header) ?: return emptyList()
                return Regex("""(?:override\s+)?(?:val|var)?\s*(\w+)\s*:""").findAll(ctor.groupValues[1]).map { it.groupValues[1] }.toList()
            }

        fun overriddenMembers(): Set<String> {
            val found = mutableSetOf<String>()
            val text = header + body
            for (m in trackedMembers) {
                val isFun = m == "fetchBaseUrls" || m == "setupPreferenceScreen"
                val pattern = if (isFun) """\boverride\s+(?:suspend\s+)?fun\s+$m\b""" else """\boverride\s+(?:val|var)\s+$m\b"""
                if (Regex(pattern).containsMatchIn(text)) found.add(m)
            }
            return found
        }
    }

    private fun findClass(sourceDir: File, name: String): ClassDecl? {
        val decl = Regex("""(?:^|\n)((?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|internal|private|abstract|open|sealed)\s+)*class\s+$name\b)""")
        for (file in sourceDir.walk().filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText()
            val m = decl.find(text) ?: continue
            val start = m.groups[1]!!.range.first
            val end = headerEnd(text, m.range.last + 1)
            val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1) ?: ""
            // `class Foo : Theme("…")` with no body is common for theme extensions.
            val body = if (end < text.length && text[end] == '{') {
                val close = KtsBlock.matchBrace(text, end) ?: continue
                text.substring(end + 1, close)
            } else ""
            return ClassDecl(file, pkg, name, text.substring(start, end), body)
        }
        return null
    }

    /** Index of the `{` opening the class body, or of the newline that ends a body-less declaration. */
    private fun headerEnd(text: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            when (text[i]) {
                '(', '<' -> depth++
                ')', '>' -> depth--
                '{' -> if (depth == 0) return i
                '\n' -> if (depth == 0) {
                    val prev = text.substring(from, i).trimEnd().lastOrNull()
                    val next = text.substring(i + 1).trimStart().firstOrNull()
                    if (prev != ':' && prev != ',' && next != ':' && next != ',' && next != '{') return i
                }
            }
            i++
        }
        return text.length
    }

    /** Locates the single `@Source`-annotated class under [extensionSourceDir] and walks its superclasses in [allSourcesDir]. */
    fun findSourceClass(extensionSourceDir: File, allSourcesDir: File): SourceClass {
        val annotated = Regex("""@Source\s*\n\s*(?:(?:public|internal|abstract|open)\s+)*class\s+(\w+)""")
        val matches = extensionSourceDir.walk().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> annotated.findAll(f.readText()).map { f to it.groupValues[1] } }
            .toList()
        require(matches.size == 1) {
            if (matches.isEmpty()) "no @Source class under $extensionSourceDir"
            else "more than one @Source class: ${matches.map { it.second }}"
        }
        val (_, className) = matches.single()

        val first = findClass(allSourcesDir, className) ?: throw IllegalStateException("cannot read class $className")
        val overridden = mutableSetOf<String>()
        var current: ClassDecl? = first
        var root: String? = null
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.name)) {
            overridden.addAll(current.overriddenMembers())
            val next = current.superClass
            if (next == null) { root = null; break }
            val decl = findClass(allSourcesDir, next)
            if (decl == null) { root = next; break }
            current = decl
        }
        return SourceClass(
            packageName = first.packageName,
            className = className,
            isAbstract = first.isAbstract,
            ctorParams = first.ctorParams,
            overridden = overridden,
            root = root,
        )
    }

    /** `core/translations/strings.json`: lang -> key -> text; lookup falls back to the base language, then English. */
    class Translations(private val table: Map<String, Map<String, String>>) {
        fun get(lang: String, key: String): String =
            table[lang]?.get(key) ?: table[lang.substringBefore("-")]?.get(key) ?: table["en"]?.get(key)
                ?: throw IllegalStateException("no translation for $key")

        companion object {
            fun load(file: File): Translations {
                @Suppress("UNCHECKED_CAST")
                val parsed = groovy.json.JsonSlurper().parse(file) as Map<String, Map<String, String>>
                return Translations(parsed)
            }
        }
    }

    private fun kotlinString(s: String): String = "\"" + s
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    fun generate(cls: SourceClass, sources: List<ResolvedSource>, translations: Translations): String {
        require(sources.isNotEmpty()) { "no sources resolved for ${cls.className}" }
        val isKei = cls.root == "KeiSource"
        val out = StringBuilder()
        out.appendLine("// Generated by tachiyomi-js SourceGenerator; mirrors keiyoushi's KSP SourceProcessor output.")
        out.appendLine("package keiyoushi.source")
        out.appendLine()
        out.appendLine("import androidx.preference.PreferenceScreen")
        out.appendLine("import eu.kanade.tachiyomi.source.ConfigurableSource")
        out.appendLine("import eu.kanade.tachiyomi.source.Source")
        out.appendLine("import eu.kanade.tachiyomi.source.SourceFactory")
        out.appendLine("import keiyoushi.utils.getPreferences")
        out.appendLine("import ${cls.packageName}.${cls.className}")
        out.appendLine()

        // Preference holders for mirrors/custom base urls, one per source.
        val prefsVar = mutableMapOf<Int, String>()
        sources.forEachIndexed { i, src ->
            val suffix = if (sources.size == 1) "" else i.toString()
            when (val url = src.baseUrl) {
                is BaseUrlSpec.Mirrors -> {
                    val name = "mirrorPrefs$suffix"
                    prefsVar[i] = name
                    out.appendLine("private val $name = MirrorPreferences(")
                    out.appendLine("    preferences = getPreferences(${src.id}L),")
                    out.appendLine("    defaultMirrors = arrayOf(${url.mirrors.joinToString(", ") { kotlinString(it.first) + " to " + kotlinString(it.second) }}),")
                    out.appendLine("    title = ${kotlinString(translations.get(src.lang, "mirrorTitle"))},")
                    out.appendLine(")")
                }
                is BaseUrlSpec.Custom -> {
                    val name = "customUrlPrefs$suffix"
                    prefsVar[i] = name
                    out.appendLine("private val $name = CustomUrlPreferences(")
                    out.appendLine("    preferences = getPreferences(${src.id}L),")
                    out.appendLine("    hardcodedUrl = ${kotlinString(url.url)},")
                    out.appendLine("    title = ${kotlinString(translations.get(src.lang, "customUrlTitle"))},")
                    out.appendLine("    dialogMessage = ${kotlinString(translations.get(src.lang, "customUrlDialogMessage"))},")
                    out.appendLine(")")
                }
                is BaseUrlSpec.Static -> {}
            }
        }
        if (prefsVar.isNotEmpty()) out.appendLine()

        fun members(src: ResolvedSource, index: Int, indent: String): String {
            val b = StringBuilder()
            val ov = cls.overridden
            if (isKei) {
                if ("filterFetchHint" in ov) throw IllegalStateException("${cls.className} overrides filterFetchHint; keiyoushi generates it")
                b.appendLine("${indent}protected override val filterFetchHint: String get() = ${kotlinString(translations.get(src.lang, "filterFetchHint"))}")
            }
            if ("name" !in ov) b.appendLine("${indent}override val name: String get() = ${kotlinString(src.name)}")
            if ("lang" !in ov) b.appendLine("${indent}override val lang: String get() = ${kotlinString(src.lang)}")
            if ("id" !in ov) b.appendLine("${indent}override val id: Long get() = ${src.id}L")
            val prefs = prefsVar[index]
            if ("baseUrl" !in ov) {
                if (prefs != null) b.appendLine("${indent}override val baseUrl: String get() = $prefs.baseUrl")
                else b.appendLine("${indent}override val baseUrl: String get() = ${kotlinString(src.baseUrl.defaultUrl)}")
            }
            if (prefs != null) {
                b.appendLine("${indent}override fun setupPreferenceScreen(screen: PreferenceScreen) {")
                b.appendLine("$indent    $prefs.setupPreferenceScreen(screen)")
                if ("setupPreferenceScreen" in ov) b.appendLine("$indent    super.setupPreferenceScreen(screen)")
                b.appendLine("$indent}")
                if ("fetchBaseUrls" in ov && "fetchableBaseUrl" !in ov) {
                    b.appendLine("${indent}protected override val fetchableBaseUrl: FetchableBaseUrl get() = $prefs")
                }
            }
            return b.toString()
        }

        val configurable = prefsVar.isNotEmpty() && "setupPreferenceScreen" !in cls.overridden
        val supertypes = "${cls.className}()" + if (configurable) ", ConfigurableSource" else ""

        if (sources.size == 1 && cls.isAbstract) {
            out.appendLine("internal class Generated : $supertypes {")
            out.append(members(sources[0], 0, "    "))
            out.appendLine("}")
            return out.toString()
        }

        out.appendLine("internal class Generated : SourceFactory {")
        out.appendLine("    override fun createSources(): List<Source> = listOf(")
        sources.forEachIndexed { i, src ->
            if (cls.isAbstract) {
                out.appendLine("        object : $supertypes {")
                out.append(members(src, i, "            "))
                out.appendLine("        },")
            } else {
                require(src.baseUrl is BaseUrlSpec.Static) { "concrete @Source class ${cls.className} needs a static baseUrl" }
                val args = cls.ctorParams.map { p ->
                    when (p) {
                        "name" -> "name = ${kotlinString(src.name)}"
                        "lang" -> "lang = ${kotlinString(src.lang)}"
                        "id" -> "id = ${src.id}L"
                        "baseUrl" -> "baseUrl = ${kotlinString(src.baseUrl.defaultUrl)}"
                        else -> throw IllegalStateException("unsupported constructor parameter $p on ${cls.className}")
                    }
                }
                out.appendLine("        ${cls.className}(${args.joinToString(", ")}),")
            }
        }
        out.appendLine("    )")
        out.appendLine("}")
        return out.toString()
    }
}
