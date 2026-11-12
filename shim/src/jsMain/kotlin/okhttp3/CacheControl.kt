package okhttp3

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class CacheControl private constructor(
    val noCache: Boolean,
    val noStore: Boolean,
    val maxAgeSeconds: Int,
    val sMaxAgeSeconds: Int,
    val isPrivate: Boolean,
    val isPublic: Boolean,
    val mustRevalidate: Boolean,
    val maxStaleSeconds: Int,
    val minFreshSeconds: Int,
    val onlyIfCached: Boolean,
    val noTransform: Boolean,
    val immutable: Boolean,
) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (noCache) parts.add("no-cache")
        if (noStore) parts.add("no-store")
        if (maxAgeSeconds != -1) parts.add("max-age=$maxAgeSeconds")
        if (sMaxAgeSeconds != -1) parts.add("s-maxage=$sMaxAgeSeconds")
        if (isPrivate) parts.add("private")
        if (isPublic) parts.add("public")
        if (mustRevalidate) parts.add("must-revalidate")
        if (maxStaleSeconds != -1) parts.add("max-stale=$maxStaleSeconds")
        if (minFreshSeconds != -1) parts.add("min-fresh=$minFreshSeconds")
        if (onlyIfCached) parts.add("only-if-cached")
        if (noTransform) parts.add("no-transform")
        if (immutable) parts.add("immutable")
        return parts.joinToString(", ")
    }

    class Builder {
        private var noCache = false
        private var noStore = false
        private var maxAgeSeconds = -1
        private var maxStaleSeconds = -1
        private var minFreshSeconds = -1
        private var onlyIfCached = false
        private var noTransform = false
        private var immutable = false

        fun noCache(): Builder = apply { noCache = true }
        fun noStore(): Builder = apply { noStore = true }
        fun maxAge(maxAge: Int, unit: TimeUnit): Builder = apply { maxAgeSeconds = clamp(unit.toSeconds(maxAge.toLong())) }
        fun maxAge(maxAge: Duration): Builder = apply { maxAgeSeconds = clamp(maxAge.inWholeSeconds) }
        fun maxStale(maxStale: Int, unit: TimeUnit): Builder = apply { maxStaleSeconds = clamp(unit.toSeconds(maxStale.toLong())) }
        fun maxStale(maxStale: Duration): Builder = apply { maxStaleSeconds = clamp(maxStale.inWholeSeconds) }
        fun minFresh(minFresh: Int, unit: TimeUnit): Builder = apply { minFreshSeconds = clamp(unit.toSeconds(minFresh.toLong())) }
        fun minFresh(minFresh: Duration): Builder = apply { minFreshSeconds = clamp(minFresh.inWholeSeconds) }
        fun onlyIfCached(): Builder = apply { onlyIfCached = true }
        fun noTransform(): Builder = apply { noTransform = true }
        fun immutable(): Builder = apply { immutable = true }

        private fun clamp(seconds: Long): Int = if (seconds > Int.MAX_VALUE) Int.MAX_VALUE else seconds.toInt()

        fun build(): CacheControl = CacheControl(
            noCache = noCache,
            noStore = noStore,
            maxAgeSeconds = maxAgeSeconds,
            sMaxAgeSeconds = -1,
            isPrivate = false,
            isPublic = false,
            mustRevalidate = false,
            maxStaleSeconds = maxStaleSeconds,
            minFreshSeconds = minFreshSeconds,
            onlyIfCached = onlyIfCached,
            noTransform = noTransform,
            immutable = immutable,
        )
    }

    companion object {
        val FORCE_NETWORK = Builder().noCache().build()
        val FORCE_CACHE = Builder().onlyIfCached().maxStale(Int.MAX_VALUE, TimeUnit.SECONDS).build()

        fun parse(headers: Headers): CacheControl {
            var noCache = false
            var noStore = false
            var maxAgeSeconds = -1
            var sMaxAgeSeconds = -1
            var isPrivate = false
            var isPublic = false
            var mustRevalidate = false
            var maxStaleSeconds = -1
            var minFreshSeconds = -1
            var onlyIfCached = false
            var noTransform = false
            var immutable = false
            for (value in headers.values("Cache-Control") + headers.values("Pragma")) {
                for (directive in value.split(',')) {
                    val d = directive.trim()
                    val name = d.substringBefore('=').trim().lowercase()
                    val arg = d.substringAfter('=', "").trim('"', ' ')
                    when (name) {
                        "no-cache" -> noCache = true
                        "no-store" -> noStore = true
                        "max-age" -> maxAgeSeconds = arg.toIntOrNull() ?: -1
                        "s-maxage" -> sMaxAgeSeconds = arg.toIntOrNull() ?: -1
                        "private" -> isPrivate = true
                        "public" -> isPublic = true
                        "must-revalidate" -> mustRevalidate = true
                        "max-stale" -> maxStaleSeconds = arg.toIntOrNull() ?: Int.MAX_VALUE
                        "min-fresh" -> minFreshSeconds = arg.toIntOrNull() ?: -1
                        "only-if-cached" -> onlyIfCached = true
                        "no-transform" -> noTransform = true
                        "immutable" -> immutable = true
                    }
                }
            }
            return CacheControl(
                noCache, noStore, maxAgeSeconds, sMaxAgeSeconds, isPrivate, isPublic, mustRevalidate,
                maxStaleSeconds, minFreshSeconds, onlyIfCached, noTransform, immutable,
            )
        }
    }
}
