package keiyoushi.source

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

/** Base url preferences that take the urls from `fetchBaseUrls`. */
interface FetchableBaseUrl {
    fun update(urls: List<String>)
}
