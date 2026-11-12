package tachiyomi.shim.compat

import java.nio.charset.Charset
import java.nio.charset.toByteArray
import java.nio.charset.toString
import java.util.Locale
import java.lang.Class as JClass
import kotlin.reflect.KClass

/*
 * JVM stdlib members that Kotlin/JS lacks. The compiler's preprocessor imports this package
 * into every extension file.
 */

fun String.uppercase(locale: Locale): String = uppercase()
fun String.lowercase(locale: Locale): String = lowercase()
fun Char.uppercase(locale: Locale): String = uppercase()
fun Char.lowercase(locale: Locale): String = lowercase()
fun Char.titlecase(locale: Locale): String = titlecase()
fun String.capitalize(locale: Locale): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
fun String.decapitalize(locale: Locale): String = replaceFirstChar { it.lowercase() }

val <T : Any> KClass<T>.java: JClass<T> get() = JClass.of(this)

@Suppress("UNCHECKED_CAST")
val <T : Any> T.javaClass: JClass<T> get() = JClass.of(this::class) as JClass<T>

/**
 * java.lang.String.format for the conversions sources use: %s %d %x %X %f %e %c %b %n %% with
 * optional flags (`-`, `0`, `+`, `,`), width and precision, and `%1$s` argument indexes.
 */
fun String.format(vararg args: Any?): String = formatWith(args)
fun String.format(locale: Locale?, vararg args: Any?): String = formatWith(args)
/** The Java static form, `String.format(Locale.ROOT, "%.1f", x)`. */
fun String.Companion.format(format: String, vararg args: Any?): String = format.formatWith(args)
fun String.Companion.format(locale: Locale?, format: String, vararg args: Any?): String = format.formatWith(args)

private val FORMAT_SPEC = Regex("""%(?:(\d+)\$)?([-+0, #]*)(\d+)?(?:\.(\d+))?([sdxXfeEcbn%])""")

private fun String.formatWith(args: Array<out Any?>): String {
    var next = 0
    return FORMAT_SPEC.replace(this) { m ->
        val (index, flags, width, precision, conv) = m.destructured
        when (conv) {
            "%" -> return@replace "%"
            "n" -> return@replace "\n"
        }
        val arg = if (index.isNotEmpty()) args.getOrNull(index.toInt() - 1) else args.getOrNull(next++)
        var text = when (conv) {
            "d" -> {
                val n = (arg as? Number)?.toDouble() ?: 0.0
                val digits = if (arg is Long || arg is Int || arg is Short || arg is Byte) arg.toString() else n.toLong().toString()
                val grouped = if (',' in flags) digits.groupThousands() else digits
                if ('+' in flags && n >= 0) "+$grouped" else grouped
            }
            "x" -> ((arg as? Number)?.toLong() ?: 0L).toString(16)
            "X" -> ((arg as? Number)?.toLong() ?: 0L).toString(16).uppercase()
            "f" -> {
                val n = (arg as? Number)?.toDouble() ?: 0.0
                val fixed = n.asDynamic().toFixed(precision.ifEmpty { "6" }.toInt()).unsafeCast<String>()
                val grouped = if (',' in flags) fixed.groupThousands() else fixed
                if ('+' in flags && n >= 0) "+$grouped" else grouped
            }
            "e", "E" -> {
                val n = (arg as? Number)?.toDouble() ?: 0.0
                val s = n.asDynamic().toExponential(precision.ifEmpty { "6" }.toInt()).unsafeCast<String>()
                    .replace(Regex("""e([+-])(\d)$"""), "e$10$2")
                if (conv == "E") s.uppercase() else s
            }
            "c" -> when (arg) { is Char -> arg.toString(); is Number -> arg.toInt().toChar().toString(); else -> arg.toString() }
            "b" -> (arg != null && arg != false).toString()
            else -> if (precision.isNotEmpty()) arg.toString().take(precision.toInt()) else arg.toString()
        }
        if (width.isNotEmpty()) {
            val w = width.toInt()
            text = when {
                '-' in flags -> text.padEnd(w)
                '0' in flags && conv != "s" -> {
                    val sign = if (text.startsWith("-") || text.startsWith("+")) text.take(1) else ""
                    sign + text.drop(sign.length).padStart(w - sign.length, '0')
                }
                else -> text.padStart(w)
            }
        }
        text
    }
}

private fun String.groupThousands(): String {
    val sign = if (startsWith("-")) "-" else ""
    val body = drop(sign.length)
    val intPart = body.substringBefore('.')
    val rest = body.drop(intPart.length)
    return sign + intPart.reversed().chunked(3).joinToString(",").reversed() + rest
}

fun String(bytes: ByteArray): String = bytes.decodeToString()
fun String(bytes: ByteArray, charset: Charset): String = bytes.toString(charset)
fun String(bytes: ByteArray, charsetName: String): String = bytes.toString(Charset.forName(charsetName))
fun String(bytes: ByteArray, offset: Int, length: Int): String = bytes.decodeToString(offset, offset + length)
fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String = bytes.copyOfRange(offset, offset + length).toString(charset)
fun String(chars: CharArray): String = chars.concatToString()
fun String(chars: CharArray, offset: Int, length: Int): String = chars.concatToString(offset, offset + length)

/** `kotlin.synchronized` is JVM-only; the runtime is single-threaded, so the lock is irrelevant. */
inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

/** `kotlin.assert` is JVM-only; here it always checks, as a JVM with `-ea` would. */
fun assert(value: Boolean) {
    if (!value) throw AssertionError("Assertion failed")
}

inline fun assert(value: Boolean, lazyMessage: () -> Any) {
    if (!value) throw AssertionError(lazyMessage())
}

/** `java.lang.String.CASE_INSENSITIVE_ORDER`, imported by the preprocessor in place of the JVM import. */
val CASE_INSENSITIVE_ORDER: Comparator<String> get() = String.CASE_INSENSITIVE_ORDER

/** `Map.getOrDefault` is a JVM-only member. */
fun <K, V> Map<out K, V>.getOrDefault(key: K, defaultValue: V): V {
    @Suppress("UNCHECKED_CAST")
    return if (containsKey(key)) get(key) as V else defaultValue
}

fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
    if (codePoint < 0x10000) return append(codePoint.toChar())
    val offset = codePoint - 0x10000
    append(((offset shr 10) + 0xD800).toChar())
    return append(((offset and 0x3FF) + 0xDC00).toChar())
}

/** JVM `toSortedMap` returns a `SortedMap`; a key-ordered `LinkedHashMap` serves the corpus's reads. */
fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): MutableMap<K, V> =
    entries.sortedBy { it.key }.associateTo(LinkedHashMap()) { it.key to it.value }

fun <K, V> Map<out K, V>.toSortedMap(comparator: Comparator<in K>): MutableMap<K, V> =
    entries.sortedWith(compareBy(comparator) { it.key }).associateTo(LinkedHashMap()) { it.key to it.value }
