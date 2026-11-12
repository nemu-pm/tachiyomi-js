package tachiyomi.shim.compat

import kotlin.text.RegexOption as KtRegexOption

/*
 * Kotlin/JS only knows RegexOption.IGNORE_CASE and MULTILINE; extensions also reach for
 * DOT_MATCHES_ALL (and the compiler cannot pass the JS `s` flag through kotlin.text.Regex).
 * Extension sources and the verbatim keiyoushi core files star-import this package, which
 * outranks the default kotlin.text imports, so `RegexOption.X`, `Regex(p, opts)` and
 * `p.toRegex(opts)` land here and are mapped onto the stdlib Regex.
 */
enum class RegexOption {
    IGNORE_CASE,
    MULTILINE,
    DOT_MATCHES_ALL,
    LITERAL,
    COMMENTS,
    UNIX_LINES,
    CANON_EQ,
}

@Suppress("FunctionName")
fun Regex(pattern: String, option: RegexOption): Regex = Regex(pattern, setOf(option))

@Suppress("FunctionName")
fun Regex(pattern: String, options: Set<RegexOption>): Regex {
    var source = pattern
    if (RegexOption.LITERAL in options) source = Regex.escape(source)
    if (RegexOption.COMMENTS in options) source = stripComments(source)
    if (RegexOption.DOT_MATCHES_ALL in options) source = dotMatchesAll(source)
    val native = buildSet {
        if (RegexOption.IGNORE_CASE in options) add(KtRegexOption.IGNORE_CASE)
        if (RegexOption.MULTILINE in options) add(KtRegexOption.MULTILINE)
    }
    return Regex(source, native)
}

fun String.toRegex(option: RegexOption): Regex = Regex(this, setOf(option))

fun String.toRegex(options: Set<RegexOption>): Regex = Regex(this, options)

/** Rewrites every unescaped `.` outside a character class to `[\s\S]`. */
internal fun dotMatchesAll(pattern: String): String {
    val out = StringBuilder(pattern.length + 16)
    var inClass = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\\' && i + 1 < pattern.length -> {
                out.append(c).append(pattern[i + 1])
                i += 2
                continue
            }
            inClass -> if (c == ']') inClass = false
            c == '[' -> inClass = true
            c == '.' -> {
                out.append("[\\s\\S]")
                i++
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/** Free-spacing mode: drops unescaped whitespace and `#` comments outside character classes. */
internal fun stripComments(pattern: String): String {
    val out = StringBuilder(pattern.length)
    var inClass = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\\' && i + 1 < pattern.length -> {
                out.append(c).append(pattern[i + 1])
                i += 2
                continue
            }
            inClass -> if (c == ']') inClass = false
            c == '[' -> inClass = true
            c.isWhitespace() -> {
                i++
                continue
            }
            c == '#' -> {
                while (i < pattern.length && pattern[i] != '\n') i++
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}
