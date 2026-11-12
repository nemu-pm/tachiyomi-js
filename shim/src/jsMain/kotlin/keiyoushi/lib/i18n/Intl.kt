package keiyoushi.lib.i18n

// Ported from keiyoushi lib/i18n Intl.kt. PropertyResourceBundle is replaced by a small
// .properties parser over the bytes the shim's ClassLoader serves from ResourceRegistry, and
// String.format(locale, ...) by a formatter that covers the %s / %d / %1$s / %% subset the
// message files use. The @PropertyKey IDE annotations are dropped.

import java.lang.ClassLoader
import java.text.Collator
import java.util.Locale

/**
 * A simple wrapper to make internationalization easier to use in sources.
 *
 * Message files should be put in the `assets/i18n` folder, with the name
 * `messages_{iso_639_1}.properties`, where `iso_639_1` should be using
 * snake case and be in lowercase.
 */
class Intl(
    language: String,
    availableLanguages: Set<String>,
    private val baseLanguage: String,
    private val classLoader: ClassLoader,
    private val createMessageFileName: (String) -> String = { createDefaultMessageFileName(it) },
) {

    val chosenLanguage: String = when (language) {
        in availableLanguages -> language
        else -> baseLanguage
    }

    private val locale: Locale = Locale.forLanguageTag(chosenLanguage)

    val collator: Collator = Collator.getInstance(locale)

    private val baseBundle: Map<String, String> by lazy { createBundle(baseLanguage) }

    private val bundle: Map<String, String> by lazy {
        if (chosenLanguage == baseLanguage) baseBundle else createBundle(chosenLanguage)
    }

    /**
     * Returns the string from the message file. If the [key] is not present
     * in the current language, the English value will be returned. If the [key]
     * is also not present in English, the [key] surrounded by brackets will be returned.
     */
    operator fun get(key: String): String = bundle[key] ?: baseBundle[key] ?: "[$key]"

    /**
     * Uses the string as a format string and returns a string obtained by
     * substituting the specified arguments, using the instance locale.
     */
    fun format(key: String, vararg args: Any?): String = formatMessage(get(key), args)

    fun languageDisplayName(localeCode: String): String = Locale.forLanguageTag(localeCode)
        .getDisplayName(locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /**
     * Loads the message file for [lang] through the class loader, as the JVM version does with
     * a PropertyResourceBundle. A missing file yields an empty bundle rather than an exception.
     */
    private fun createBundle(lang: String): Map<String, String> {
        val fileName = createMessageFileName(lang)
        val content = classLoader.getResourceAsStream(fileName)?.readBytes()?.decodeToString() ?: return emptyMap()
        return parseProperties(content)
    }

    companion object {
        fun createDefaultMessageFileName(lang: String): String {
            val langSnakeCase = lang.replace("-", "_").lowercase()

            return "assets/i18n/messages_$langSnakeCase.properties"
        }

        /** Parses java.util.Properties text: `#`/`!` comments, `=`/`:`/space separators, `\` continuations and escapes. */
        internal fun parseProperties(content: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            val lines = content.lines()
            var i = 0
            while (i < lines.size) {
                var line = lines[i].trimStart()
                i++
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
                // A trailing odd run of backslashes continues the logical line
                while (trailingBackslashes(line) % 2 == 1 && i < lines.size) {
                    line = line.dropLast(1) + lines[i].trimStart()
                    i++
                }
                val (key, value) = splitKeyValue(line)
                result[unescape(key)] = unescape(value)
            }
            return result
        }

        private fun trailingBackslashes(line: String): Int {
            var n = 0
            var j = line.length - 1
            while (j >= 0 && line[j] == '\\') {
                n++
                j--
            }
            return n
        }

        private fun splitKeyValue(line: String): Pair<String, String> {
            var j = 0
            while (j < line.length) {
                val c = line[j]
                if (c == '\\') {
                    j += 2
                    continue
                }
                if (c == '=' || c == ':' || c == ' ' || c == '\t') break
                j++
            }
            val key = line.substring(0, minOf(j, line.length))
            var k = j
            while (k < line.length && (line[k] == ' ' || line[k] == '\t')) k++
            if (k < line.length && (line[k] == '=' || line[k] == ':')) k++
            while (k < line.length && (line[k] == ' ' || line[k] == '\t')) k++
            return key to line.substring(minOf(k, line.length))
        }

        private fun unescape(text: String): String {
            if (!text.contains('\\')) return text
            val out = StringBuilder(text.length)
            var j = 0
            while (j < text.length) {
                val c = text[j]
                if (c != '\\' || j + 1 >= text.length) {
                    out.append(c)
                    j++
                    continue
                }
                val next = text[j + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        val hex = text.substring(j + 2, minOf(j + 6, text.length))
                        val code = hex.toIntOrNull(16)
                        if (hex.length == 4 && code != null) {
                            out.append(code.toChar())
                            j += 6
                            continue
                        }
                        out.append('u')
                    }
                    else -> out.append(next)
                }
                j += 2
            }
            return out.toString()
        }

        /** The java.util.Formatter subset message files use: %s, %d, %f-ish, positional %1$s, and %%. */
        internal fun formatMessage(template: String, args: Array<out Any?>): String {
            val out = StringBuilder(template.length + 16)
            var next = 0
            var j = 0
            while (j < template.length) {
                val c = template[j]
                if (c != '%' || j + 1 >= template.length) {
                    out.append(c)
                    j++
                    continue
                }
                if (template[j + 1] == '%') {
                    out.append('%')
                    j += 2
                    continue
                }
                var k = j + 1
                var index: Int? = null
                val digits = StringBuilder()
                while (k < template.length && template[k].isDigit()) digits.append(template[k++])
                if (k < template.length && template[k] == '$' && digits.isNotEmpty()) {
                    index = digits.toString().toInt() - 1
                    k++
                } else {
                    k = j + 1
                }
                // flags, width and precision are accepted and ignored
                while (k < template.length && (template[k] in "-#+ 0,(" || template[k].isDigit() || template[k] == '.')) k++
                if (k >= template.length) {
                    out.append(template, j, template.length)
                    break
                }
                val conversion = template[k]
                val arg = args.getOrNull(index ?: next++)
                out.append(
                    when (conversion) {
                        'd', 'x', 'X', 'o' -> arg?.toString() ?: "null"
                        'S' -> arg?.toString()?.uppercase() ?: "NULL"
                        'n' -> "\n"
                        else -> arg?.toString() ?: "null"
                    },
                )
                j = k + 1
            }
            return out.toString()
        }
    }
}
