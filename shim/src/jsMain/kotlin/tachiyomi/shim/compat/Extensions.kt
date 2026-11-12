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

fun String(bytes: ByteArray): String = bytes.decodeToString()
fun String(bytes: ByteArray, charset: Charset): String = bytes.toString(charset)
fun String(bytes: ByteArray, charsetName: String): String = bytes.toString(Charset.forName(charsetName))
fun String(bytes: ByteArray, offset: Int, length: Int): String = bytes.decodeToString(offset, offset + length)
fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String = bytes.copyOfRange(offset, offset + length).toString(charset)
fun String(chars: CharArray): String = chars.concatToString()
fun String(chars: CharArray, offset: Int, length: Int): String = chars.concatToString(offset, offset + length)
