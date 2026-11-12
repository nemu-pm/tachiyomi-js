package keiyoushi.utils

// tachiyomi-js: shim imports (added by scripts/sync-core.sh)
import java.lang.System
import java.lang.Integer
import java.lang.Class
import java.lang.Character
import java.nio.charset.Charsets
import java.nio.charset.toString
import java.nio.charset.toByteArray
import java.util.Arrays
import tachiyomi.shim.compat.*

/**
 * Returns the first element that is an instances of specified type parameter T.
 *
 * @throws [NoSuchElementException] if no such element is found.
 */
inline fun <reified T> Iterable<*>.firstInstance(): T = first { it is T } as T

/**
 * Returns the first element that is an instances of specified type parameter T, or `null` if element was not found.
 */
inline fun <reified T> Iterable<*>.firstInstanceOrNull(): T? = firstOrNull { it is T } as? T
