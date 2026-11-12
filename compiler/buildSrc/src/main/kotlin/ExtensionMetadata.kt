import java.io.File
import java.security.MessageDigest

/**
 * Metadata parsed from a keiyoushi `build.gradle.kts` (the `keiyoushi { ... }` DSL that
 * `gradle/build-logic` evaluates upstream). This is a small evaluator for the subset of
 * Kotlin the corpus actually uses, not a Kotlin interpreter: literal assignments, `source {}`
 * blocks, `listOf`/`mapOf` vals, `.forEach { x -> }` loops over them, `$var` interpolation and
 * `when (var) { "x" -> id = ... }` inside a source block. Anything else fails loudly so the
 * extension shows up as "unsupported DSL" instead of compiling with wrong ids.
 *
 * Derived values (ids, package, version code/name) follow
 * `gradle/build-logic/src/main/kotlin/io/github/keiyoushi/gradle/ExtensionPlugin.kt`.
 */
// Serializable so the resolved sources can be a Gradle task @Input (configuration cache).
sealed class BaseUrlSpec : java.io.Serializable {
    data class Static(val url: String) : BaseUrlSpec()
    data class Mirrors(val mirrors: List<Pair<String, String>>) : BaseUrlSpec()
    data class Custom(val url: String) : BaseUrlSpec()

    /** URL the source starts on, before any preference override. */
    val defaultUrl: String
        get() = when (this) {
            is Static -> url
            is Mirrors -> mirrors.first().second
            is Custom -> url
        }
}

data class SourceSpec(
    val lang: String,
    val name: String?,
    val baseUrl: BaseUrlSpec,
    val versionId: Int?,
    val id: Long?,
)

data class ResolvedSource(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: BaseUrlSpec,
) : java.io.Serializable

data class Deeplink(val hosts: List<String>, val paths: List<String>, val pathPatterns: List<String>)

data class ThemeMetadata(
    val name: String,
    val baseVersionCode: Int,
    val libDeps: List<String>,
    val deeplink: Deeplink?,
)

data class ExtensionMetadata(
    val name: String,
    val versionCode: Int,
    val contentWarning: String,
    val libVersion: String,
    val theme: String?,
    val pkgName: String?,
    val sources: List<SourceSpec>,
    val deeplink: Deeplink?,
    val libDeps: List<String>,
) {
    companion object {
        fun parse(buildGradleKts: File): ExtensionMetadata {
            require(buildGradleKts.exists()) { "no build.gradle.kts at $buildGradleKts" }
            val text = stripComments(buildGradleKts.readText())
            val block = KtsBlock.find(text, "keiyoushi")
                ?: throw IllegalStateException("no keiyoushi {} block in $buildGradleKts")
            val eval = KtsEvaluator(block.body)
            val scope = eval.topLevelScope()

            return ExtensionMetadata(
                name = scope.string("name") ?: throw IllegalStateException("keiyoushi.name missing in $buildGradleKts"),
                versionCode = scope.int("versionCode") ?: throw IllegalStateException("keiyoushi.versionCode missing in $buildGradleKts"),
                contentWarning = scope.enum("contentWarning") ?: "SAFE",
                libVersion = scope.string("libVersion") ?: "1.6",
                theme = scope.string("theme"),
                pkgName = scope.string("pkgName"),
                sources = eval.sources(),
                deeplink = parseDeeplink(block.body),
                libDeps = parseLibDeps(text),
            )
        }

        fun parseTheme(buildGradleKts: File): ThemeMetadata {
            require(buildGradleKts.exists()) { "no theme build.gradle.kts at $buildGradleKts" }
            val text = stripComments(buildGradleKts.readText())
            val block = KtsBlock.find(text, "keiyoushi")
                ?: throw IllegalStateException("no keiyoushi {} block in $buildGradleKts")
            val scope = KtsEvaluator(block.body).topLevelScope()
            return ThemeMetadata(
                name = buildGradleKts.parentFile.name,
                baseVersionCode = scope.int("baseVersionCode") ?: 0,
                libDeps = parseLibDeps(text),
                deeplink = parseDeeplink(block.body),
            )
        }

        /** `implementation(project(":lib:x"))`, `api(project(":lib:x"))`, `cronetRuntime(...)`. */
        fun parseLibDeps(text: String): List<String> =
            Regex("""\w+\s*\(\s*project\s*\(\s*":lib:([\w-]+)"\s*\)\s*\)""").findAll(text)
                .map { it.groupValues[1] }.distinct().toList()

        private fun parseDeeplink(body: String): Deeplink? {
            val block = KtsBlock.find(body, "deeplink") ?: return null
            fun calls(fn: String) = Regex("""\b$fn\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)""").findAll(block.body)
                .map { unescape(it.groupValues[1]) }.toList()
            return Deeplink(calls("host"), calls("path"), calls("pathPattern"))
        }

        /** Drops `//` and `/* */` comments outside string literals. */
        fun stripComments(src: String): String {
            val out = StringBuilder(src.length)
            var i = 0
            var inString = false
            while (i < src.length) {
                val c = src[i]
                when {
                    inString -> {
                        out.append(c)
                        if (c == '\\' && i + 1 < src.length) { out.append(src[i + 1]); i++ }
                        else if (c == '"') inString = false
                    }
                    c == '"' -> { inString = true; out.append(c) }
                    c == '/' && src.startsWith("//", i) -> { while (i < src.length && src[i] != '\n') i++; continue }
                    c == '/' && src.startsWith("/*", i) -> { val end = src.indexOf("*/", i + 2); i = if (end < 0) src.length else end + 2; continue }
                    else -> out.append(c)
                }
                i++
            }
            return out.toString()
        }

        internal fun unescape(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val n = s[i + 1]) {
                        'n' -> append('\n'); 't' -> append('\t'); 'r' -> append('\r')
                        '$' -> append('$'); '"' -> append('"'); '\\' -> append('\\')
                        else -> append('\\').append(n)
                    }
                    i += 2
                } else { append(c); i++ }
            }
        }

        /** Mirrors `ExtensionPlugin.computeSourceId`: first 8 MD5 bytes of `name/lang/versionId`, sign bit cleared. */
        fun computeSourceId(name: String, lang: String, versionId: Int): Long {
            val key = "${name.lowercase()}/$lang/$versionId"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff }.reduce { acc, l -> (acc shl 8) or l } and Long.MAX_VALUE
        }
    }

    val isNsfw: Boolean get() = contentWarning == "NSFW"

    fun packageName(extensionPath: String): String =
        "eu.kanade.tachiyomi.extension." + (pkgName ?: extensionPath.replace("/", "."))

    /** `theme.baseVersionCode + versionCode` when themed, else `versionCode`. */
    fun resolvedVersionCode(theme: ThemeMetadata?): Int = (theme?.baseVersionCode ?: 0) + versionCode

    fun versionName(theme: ThemeMetadata?): String = "$libVersion.${resolvedVersionCode(theme)}"

    fun resolveSources(): List<ResolvedSource> = sources.map { spec ->
        val sourceName = spec.name ?: name
        ResolvedSource(
            name = sourceName,
            lang = spec.lang,
            id = spec.id ?: computeSourceId(sourceName, spec.lang, spec.versionId ?: 1),
            baseUrl = spec.baseUrl,
        )
    }
}

/** A `name { ... }` block with its balanced body. */
class KtsBlock(val start: Int, val end: Int, val body: String) {
    companion object {
        fun find(text: String, name: String, from: Int = 0): KtsBlock? {
            val m = Regex("""(?<![\w.])$name\s*\{""").find(text, from) ?: return null
            val open = m.range.last
            val close = matchBrace(text, open) ?: return null
            return KtsBlock(m.range.first, close + 1, text.substring(open + 1, close))
        }

        fun findAll(text: String, name: String): List<KtsBlock> {
            val out = mutableListOf<KtsBlock>()
            var from = 0
            while (true) {
                val b = find(text, name, from) ?: break
                out.add(b)
                from = b.end
            }
            return out
        }

        /** Index of the `}` matching the `{` at [open], skipping string and char literals and comments. */
        fun matchBrace(text: String, open: Int): Int? {
            var depth = 0
            var i = open
            while (i < text.length) {
                val c = text[i]
                when {
                    text.startsWith("\"\"\"", i) -> {
                        // Raw strings have no escapes; a run of quotes at the end belongs to the delimiter (`""""x""""`).
                        val close = text.indexOf("\"\"\"", i + 3)
                        if (close < 0) return null
                        i = close + 3
                        while (i < text.length && text[i] == '"') i++
                        continue
                    }
                    c == '"' || c == '\'' -> {
                        i++
                        while (i < text.length && text[i] != c) {
                            if (text[i] == '\\') i++
                            i++
                        }
                    }
                    text.startsWith("//", i) -> {
                        val eol = text.indexOf('\n', i)
                        i = if (eol < 0) text.length else eol
                        continue
                    }
                    text.startsWith("/*", i) -> {
                        val close = text.indexOf("*/", i + 2)
                        if (close < 0) return null
                        i = close + 2
                        continue
                    }
                    c == '{' -> depth++
                    c == '}' -> { depth--; if (depth == 0) return i }
                }
                i++
            }
            return null
        }
    }
}

/** Values the DSL evaluator knows: strings, numbers, lists of values and `a to b` pairs. */
sealed class KtsValue {
    data class Str(val value: String) : KtsValue()
    data class Num(val value: Long) : KtsValue()
    data class Enum(val value: String) : KtsValue()
    data class Pair(val first: KtsValue, val second: KtsValue) : KtsValue()
    data class ListV(val items: List<KtsValue>) : KtsValue()
}

class KtsScope(private val parent: KtsScope? = null) {
    private val vars = mutableMapOf<String, KtsValue>()

    operator fun set(name: String, value: KtsValue) { vars[name] = value }
    operator fun get(name: String): KtsValue? = vars[name] ?: parent?.get(name)

    fun string(name: String): String? = when (val v = get(name)) {
        is KtsValue.Str -> v.value
        null -> null
        else -> throw IllegalStateException("$name is not a string: $v")
    }
    fun int(name: String): Int? = (get(name) as? KtsValue.Num)?.value?.toInt()
    fun long(name: String): Long? = (get(name) as? KtsValue.Num)?.value
    fun enum(name: String): String? = (get(name) as? KtsValue.Enum)?.value
}

/**
 * Evaluates the body of a `keiyoushi {}` block. Statements are recognised line by line;
 * blocks (`source {}`, `baseUrl {}`, `deeplink {}`, `.forEach {}`, `when () {}`) are consumed
 * whole by brace matching.
 */
class KtsEvaluator(private val body: String) {
    private val sources = mutableListOf<SourceSpec>()
    private var evaluated = false
    private val top = KtsScope()

    fun topLevelScope(): KtsScope { ensureEvaluated(); return top }
    fun sources(): List<SourceSpec> { ensureEvaluated(); return sources }

    private fun ensureEvaluated() {
        if (evaluated) return
        evaluated = true
        evalStatements(body, top, sourceBlock = null)
    }

    private class SourceBuilder {
        var lang: String? = null
        var name: String? = null
        var baseUrl: BaseUrlSpec? = null
        var versionId: Int? = null
        var id: Long? = null
    }

    private fun evalStatements(text: String, scope: KtsScope, sourceBlock: SourceBuilder?) {
        var i = 0
        while (i < text.length) {
            while (i < text.length && (text[i].isWhitespace() || text[i] == ';')) i++
            if (i >= text.length) break
            i = evalStatement(text, i, scope, sourceBlock)
        }
    }

    /** Evaluates the statement starting at [i] and returns the index just past it. */
    private fun evalStatement(text: String, i: Int, scope: KtsScope, sourceBlock: SourceBuilder?): Int {
        val rest = text.substring(i)

        fun blockEnd(m: MatchResult, what: String): Int {
            val open = i + m.range.last
            return KtsBlock.matchBrace(text, open) ?: throw IllegalStateException("unbalanced $what block")
        }

        // val x = <expr>
        Regex("""^val\s+(\w+)\s*(?::\s*[\w<>, ]+)?\s*=\s*""").find(rest)?.let { m ->
            val (value, consumed) = parseExpr(rest, m.range.last + 1, scope)
            scope[m.groupValues[1]] = value
            return i + consumed
        }

        // <receiver>.forEach { [params ->] ... }
        Regex("""^(\w+|(?:listOf|mapOf)\s*\((?:[^()]|\([^()]*\))*\))\s*\.forEach\s*\{""").find(rest)?.let { m ->
            val close = blockEnd(m, "forEach")
            val receiver = parseExpr(m.groupValues[1], 0, scope).first
            var lambda = text.substring(i + m.range.last + 1, close)
            var params = listOf("it")
            Regex("""^\s*(\(\s*\w+\s*,\s*\w+\s*\)|\w+)\s*->""").find(lambda)?.let { p ->
                params = p.groupValues[1].removeSurrounding("(", ")").split(",").map { it.trim() }
                lambda = lambda.substring(p.range.last + 1)
            }
            val items = (receiver as? KtsValue.ListV)?.items
                ?: throw IllegalStateException("forEach over non-list: $receiver")
            for (item in items) {
                val inner = KtsScope(scope)
                if (params.size == 2) {
                    val pair = item as? KtsValue.Pair ?: throw IllegalStateException("destructuring a non-pair: $item")
                    inner[params[0]] = pair.first
                    inner[params[1]] = pair.second
                } else {
                    inner[params[0]] = item
                }
                evalStatements(lambda, inner, sourceBlock)
            }
            return close + 1
        }

        // source { ... }
        Regex("""^source\s*\{""").find(rest)?.let { m ->
            val close = blockEnd(m, "source")
            val builder = SourceBuilder()
            evalStatements(text.substring(i + m.range.last + 1, close), KtsScope(scope), builder)
            sources.add(
                SourceSpec(
                    lang = builder.lang ?: throw IllegalStateException("source {} without lang"),
                    name = builder.name,
                    baseUrl = builder.baseUrl ?: throw IllegalStateException("source {} without baseUrl"),
                    versionId = builder.versionId,
                    id = builder.id,
                ),
            )
            return close + 1
        }

        // baseUrl { mirrors(...) | custom(...) }  (inside source)
        Regex("""^baseUrl\s*\{""").find(rest)?.let { m ->
            val close = blockEnd(m, "baseUrl")
            val inner = text.substring(i + m.range.last + 1, close).trim()
            val b = sourceBlock ?: throw IllegalStateException("baseUrl {} outside source {}")
            val call = Regex("""^(mirrors|custom)\s*\(""").find(inner)
                ?: throw IllegalStateException("unsupported baseUrl block: $inner")
            val args = parseArgList(inner, call.range.last, scope)
            b.baseUrl = when (call.groupValues[1]) {
                "custom" -> BaseUrlSpec.Custom((args.single() as KtsValue.Str).value)
                else -> BaseUrlSpec.Mirrors(
                    args.map {
                        when (it) {
                            is KtsValue.Str -> "" to it.value
                            is KtsValue.Pair -> (it.first as KtsValue.Str).value to (it.second as KtsValue.Str).value
                            else -> throw IllegalStateException("bad mirror: $it")
                        }
                    },
                )
            }
            return close + 1
        }

        // deeplink { ... } is parsed separately; skip it here.
        Regex("""^deeplink\s*\{""").find(rest)?.let { m -> return blockEnd(m, "deeplink") + 1 }

        // when (var) { "x", "y" -> stmt ... }
        Regex("""^when\s*\(\s*(\w+)\s*\)\s*\{""").find(rest)?.let { m ->
            val close = blockEnd(m, "when")
            val subject = scope[m.groupValues[1]] ?: throw IllegalStateException("when over unknown ${m.groupValues[1]}")
            val inner = text.substring(i + m.range.last + 1, close)
            val branch = Regex("""^\s*((?:"(?:[^"\\]|\\.)*"\s*,?\s*)+)->\s*(.+)$""", RegexOption.MULTILINE)
            for (b in branch.findAll(inner)) {
                val labels = Regex(""""((?:[^"\\]|\\.)*)"""").findAll(b.groupValues[1])
                    .map { ExtensionMetadata.unescape(it.groupValues[1]) }
                if (labels.any { KtsValue.Str(it) == subject }) {
                    evalStatements(b.groupValues[2], scope, sourceBlock)
                }
            }
            return close + 1
        }

        // if (<expr> == <expr>) stmt | { stmts }   (per-language ids: `if (it == "ko") id = …`)
        Regex("""^if\s*\(""").find(rest)?.let { m ->
            var depth = 1
            var j = m.range.last + 1
            while (j < rest.length && depth > 0) {
                when (rest[j]) { '(' -> depth++; ')' -> depth-- }
                j++
            }
            val cond = rest.substring(m.range.last + 1, j - 1)
            val op = Regex("""\s(==|!=)\s""").find(cond) ?: throw IllegalStateException("unsupported if condition: $cond")
            val left = parseExpr(cond.substring(0, op.range.first).trim(), 0, scope).first
            val right = parseExpr(cond.substring(op.range.last + 1).trim(), 0, scope).first
            val holds = (left == right) == (op.groupValues[1] == "==")
            while (j < rest.length && rest[j] == ' ') j++
            val body: String
            val end: Int
            if (j < rest.length && rest[j] == '{') {
                var d = 1
                var k = j + 1
                while (k < rest.length && d > 0) {
                    when (rest[k]) { '{' -> d++; '}' -> d-- }
                    k++
                }
                body = rest.substring(j + 1, k - 1)
                end = k
            } else {
                val lineEnd = rest.indexOf('\n', j).let { if (it < 0) rest.length else it }
                body = rest.substring(j, lineEnd)
                end = lineEnd
            }
            if (holds) evalStatements(body, scope, sourceBlock)
            return i + end
        }

        // name = <expr>
        Regex("""^(\w+)\s*=\s*""").find(rest)?.let { m ->
            val (value, consumed) = parseExpr(rest, m.range.last + 1, scope)
            assign(m.groupValues[1], value, scope, sourceBlock)
            return i + consumed
        }

        throw IllegalStateException("unsupported keiyoushi DSL statement: ${rest.lineSequence().first().trim()}")
    }

    private fun assign(name: String, value: KtsValue, scope: KtsScope, sourceBlock: SourceBuilder?) {
        if (sourceBlock == null) { scope[name] = value; return }
        when (name) {
            "lang" -> sourceBlock.lang = (value as KtsValue.Str).value
            "name" -> sourceBlock.name = (value as KtsValue.Str).value
            "baseUrl" -> sourceBlock.baseUrl = BaseUrlSpec.Static((value as KtsValue.Str).value)
            "versionId" -> sourceBlock.versionId = (value as KtsValue.Num).value.toInt()
            "id" -> sourceBlock.id = (value as KtsValue.Num).value
            else -> throw IllegalStateException("unsupported source {} property: $name")
        }
    }

    /** Parses one expression starting at [start]; returns the value and the index just past it. */
    private fun parseExpr(text: String, start: Int, scope: KtsScope): Pair<KtsValue, Int> {
        var i = start
        while (i < text.length && text[i] == ' ') i++
        val rest = text.substring(i)
        val (value, end) = when {
            rest.startsWith("\"") -> {
                val m = Regex("""^"((?:[^"\\]|\\.)*)"""").find(rest) ?: throw IllegalStateException("bad string at: ${rest.take(40)}")
                KtsValue.Str(interpolate(ExtensionMetadata.unescape(m.groupValues[1]), scope)) to i + m.range.last + 1
            }
            rest.startsWith("listOf") || rest.startsWith("mapOf") || rest.startsWith("mutableListOf") -> {
                val open = rest.indexOf('(')
                val (args, consumed) = parseArgListWithEnd(rest, open, scope)
                KtsValue.ListV(args) to i + consumed
            }
            Regex("""^-?\d""").containsMatchIn(rest) -> {
                val m = Regex("""^(-?\d[\d_]*)L?""").find(rest)!!
                KtsValue.Num(m.groupValues[1].replace("_", "").toLong()) to i + m.range.last + 1
            }
            Regex("""^ContentWarning\.\w+""").containsMatchIn(rest) -> {
                val m = Regex("""^ContentWarning\.(\w+)""").find(rest)!!
                KtsValue.Enum(m.groupValues[1]) to i + m.range.last + 1
            }
            else -> {
                val m = Regex("""^\w+""").find(rest) ?: throw IllegalStateException("unsupported expression: ${rest.take(40)}")
                val v = scope[m.value] ?: throw IllegalStateException("unknown identifier ${m.value}")
                v to i + m.range.last + 1
            }
        }
        // infix `to`
        val tail = text.substring(end)
        val to = Regex("""^\s+to\s+""").find(tail)
        if (to != null) {
            val (second, end2) = parseExpr(text, end + to.range.last + 1, scope)
            return KtsValue.Pair(value, second) to end2
        }
        return value to end
    }

    private fun parseArgList(text: String, open: Int, scope: KtsScope): List<KtsValue> = parseArgListWithEnd(text, open, scope).first

    private fun parseArgListWithEnd(text: String, open: Int, scope: KtsScope): Pair<List<KtsValue>, Int> {
        require(text[open] == '(')
        val args = mutableListOf<KtsValue>()
        var i = open + 1
        while (true) {
            while (i < text.length && (text[i].isWhitespace() || text[i] == ',')) i++
            if (i >= text.length) throw IllegalStateException("unterminated argument list")
            if (text[i] == ')') return args to i + 1
            val (value, end) = parseExpr(text, i, scope)
            args.add(value)
            i = end
        }
    }

    private fun interpolate(s: String, scope: KtsScope): String =
        Regex("""\$\{(\w+)}|\$(\w+)""").replace(s) { m ->
            val name = m.groupValues[1].ifEmpty { m.groupValues[2] }
            when (val v = scope[name] ?: throw IllegalStateException("unknown identifier $name in \"$s\"")) {
                is KtsValue.Str -> v.value
                is KtsValue.Num -> v.value.toString()
                else -> throw IllegalStateException("cannot interpolate $v")
            }
        }
}
