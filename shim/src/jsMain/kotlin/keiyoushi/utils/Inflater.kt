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

import okio.Buffer
import okio.InflaterSource
import okio.Source
import java.util.zip.Inflater

/**
 * Inflates this source, returning the decompressed bytes in a [Buffer]. The source is read fully and closed.
 *
 * @param nowrap `true` for a raw DEFLATE stream, `false` (default) for zlib
 * @return a [Buffer] holding the decompressed bytes
 */
fun Source.inflate(nowrap: Boolean = false): Buffer = InflaterSource(this, Inflater(nowrap)).use { Buffer().apply { writeAll(it) } }

/**
 * Inflates this byte array and returns the decompressed bytes.
 *
 * @param nowrap `true` for a raw DEFLATE stream, `false` (default) for zlib
 * @return the decompressed bytes
 */
fun ByteArray.inflate(nowrap: Boolean = false): ByteArray = Buffer().write(this).inflate(nowrap).readByteArray()
