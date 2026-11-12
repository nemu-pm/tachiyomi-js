package android.webkit

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}

/**
 * In-memory stand-in for the WebView cookie store. Sources write cookies here so that later
 * requests to the same host can read them back through [getCookie]; nothing persists.
 */
class CookieManager private constructor() {
    private val store = HashMap<String, LinkedHashMap<String, String>>()
    private var acceptCookie = true

    fun setAcceptCookie(accept: Boolean) {
        acceptCookie = accept
    }

    fun acceptCookie(): Boolean = acceptCookie

    fun setAcceptThirdPartyCookies(webView: Any?, accept: Boolean) {}

    fun setCookie(url: String, value: String) {
        if (!acceptCookie) return
        val host = hostOf(url) ?: return
        val nameValue = value.substringBefore(';').trim()
        val eq = nameValue.indexOf('=')
        if (eq <= 0) return
        val domain = value.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("Domain=", ignoreCase = true) }
            ?.substringAfter('=')?.trim()?.removePrefix(".")?.lowercase() ?: host
        store.getOrPut(domain) { LinkedHashMap() }[nameValue.substring(0, eq).trim()] = nameValue.substring(eq + 1).trim()
    }

    fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?) {
        setCookie(url, value)
        callback?.onReceiveValue(true)
    }

    fun getCookie(url: String): String? {
        val host = hostOf(url) ?: return null
        val cookies = LinkedHashMap<String, String>()
        for ((domain, values) in store) {
            if (host == domain || host.endsWith(".$domain")) cookies.putAll(values)
        }
        if (cookies.isEmpty()) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun hasCookies(): Boolean = store.values.any { it.isNotEmpty() }

    fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        store.clear()
        callback?.onReceiveValue(true)
    }

    @Suppress("DEPRECATION")
    fun removeAllCookie() {
        store.clear()
    }

    fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        callback?.onReceiveValue(false)
    }

    fun flush() {}

    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", url)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.lowercase().ifEmpty { null }
    }

    companion object {
        private val instance = CookieManager()
        fun getInstance(): CookieManager = instance
    }
}
