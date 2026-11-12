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

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Returns the [SharedPreferences] associated with current source id
 */
inline fun HttpSource.getPreferences(
    migration: SharedPreferences.() -> Unit = { },
): SharedPreferences = getPreferences(id).also(migration)

/**
 * Lazily returns the [SharedPreferences] associated with current source id
 */
inline fun HttpSource.getPreferencesLazy(
    crossinline migration: SharedPreferences.() -> Unit = { },
) = lazy { getPreferences(migration) }

/**
 * Returns the [SharedPreferences] associated with passed source id
 */
fun getPreferences(sourceId: Long): SharedPreferences = applicationContext.getSharedPreferences("source_$sourceId", 0x0000)
