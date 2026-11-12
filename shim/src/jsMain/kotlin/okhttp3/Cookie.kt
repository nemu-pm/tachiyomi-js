package okhttp3

/** OkHttp's Cookie, enough to parse `Set-Cookie` and build a `Cookie` header. */
class Cookie private constructor(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val persistent: Boolean,
    val hostOnly: Boolean,
) {
    fun matches(url: HttpUrl): Boolean {
        val domainMatch = if (hostOnly) url.host == domain else url.host == domain || url.host.endsWith(".$domain")
        if (!domainMatch) return false
        val urlPath = url.encodedPath
        val pathMatch = urlPath == path || (urlPath.startsWith(path) && (path.endsWith("/") || urlPath[path.length] == '/'))
        if (!pathMatch) return false
        return !secure || url.isHttps
    }

    override fun toString(): String = "$name=$value"

    class Builder {
        private var name: String? = null
        private var value: String? = null
        private var expiresAt = MAX_DATE
        private var domain: String? = null
        private var path = "/"
        private var secure = false
        private var httpOnly = false
        private var persistent = false
        private var hostOnly = false

        fun name(name: String) = apply { this.name = name.trim() }
        fun value(value: String) = apply { this.value = value.trim() }
        fun expiresAt(expiresAt: Long) = apply {
            this.expiresAt = if (expiresAt <= 0L) Long.MIN_VALUE else minOf(expiresAt, MAX_DATE)
            persistent = true
        }
        fun domain(domain: String) = apply {
            this.domain = domain.lowercase()
            hostOnly = false
        }
        fun hostOnlyDomain(domain: String) = apply {
            this.domain = domain.lowercase()
            hostOnly = true
        }
        fun path(path: String) = apply { this.path = if (path.startsWith("/")) path else "/" }
        fun secure() = apply { secure = true }
        fun httpOnly() = apply { httpOnly = true }

        fun build(): Cookie = Cookie(
            name ?: throw NullPointerException("builder.name == null"),
            value ?: throw NullPointerException("builder.value == null"),
            expiresAt, domain ?: throw NullPointerException("builder.domain == null"),
            path, secure, httpOnly, persistent, hostOnly,
        )
    }

    companion object {
        private const val MAX_DATE = 253402300799999L

        fun parse(url: HttpUrl, setCookie: String): Cookie? {
            val parts = setCookie.split(';')
            val nameValue = parts[0]
            val eq = nameValue.indexOf('=')
            if (eq <= 0) return null
            val name = nameValue.substring(0, eq).trim()
            val value = nameValue.substring(eq + 1).trim()
            if (name.isEmpty()) return null
            val builder = Builder().name(name).value(value)
            var hasDomain = false
            for (attr in parts.drop(1)) {
                val a = attr.trim()
                val aeq = a.indexOf('=')
                val attrName = (if (aeq == -1) a else a.substring(0, aeq)).trim().lowercase()
                val attrValue = if (aeq == -1) "" else a.substring(aeq + 1).trim()
                when (attrName) {
                    "domain" -> {
                        val d = attrValue.removePrefix(".").lowercase()
                        if (d.isNotEmpty() && (url.host == d || url.host.endsWith(".$d"))) {
                            builder.domain(d)
                            hasDomain = true
                        }
                    }
                    "path" -> if (attrValue.startsWith("/")) builder.path(attrValue)
                    "secure" -> builder.secure()
                    "httponly" -> builder.httpOnly()
                    "max-age" -> attrValue.toLongOrNull()?.let { builder.expiresAt(if (it <= 0) Long.MIN_VALUE else kotlin.js.Date.now().toLong() + it * 1000) }
                    "expires" -> {
                        val ms = kotlin.js.Date(attrValue).getTime()
                        if (!ms.isNaN()) builder.expiresAt(ms.toLong())
                    }
                }
            }
            if (!hasDomain) builder.hostOnlyDomain(url.host)
            return builder.build()
        }

        fun parseAll(url: HttpUrl, headers: Headers): List<Cookie> = headers.values("Set-Cookie").mapNotNull { parse(url, it) }
    }
}
