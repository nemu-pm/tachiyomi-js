package java.net

/** Minimal java.net.URI: parses scheme, authority, host, port, path, query and fragment. */
class URI(private val uriString: String) {
    val scheme: String?
    // Non-null like the JVM platform type: verbatim keiyoushi code assigns it to a String. Empty when absent.
    val host: String
    val port: Int
    // Also non-null: sources hand `URI(href).path` straight to Regex.matchEntire. Empty when absent.
    val path: String
    val query: String?
    val fragment: String?
    val authority: String?
    val userInfo: String?

    init {
        var rest = uriString
        fragment = rest.substringAfter('#', "").takeIf { rest.contains('#') }
        rest = rest.substringBefore('#')
        query = rest.substringAfter('?', "").takeIf { rest.contains('?') }
        rest = rest.substringBefore('?')
        val colon = rest.indexOf(':')
        scheme = if (colon > 0 && rest.substring(0, colon).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
            rest.substring(0, colon).also { rest = rest.substring(colon + 1) }
        } else {
            null
        }
        if (rest.startsWith("//")) {
            val afterSlashes = rest.substring(2)
            val slash = afterSlashes.indexOf('/')
            authority = if (slash == -1) afterSlashes else afterSlashes.substring(0, slash)
            path = if (slash == -1) "" else afterSlashes.substring(slash)
            val at = authority.lastIndexOf('@')
            userInfo = if (at == -1) null else authority.substring(0, at)
            val hostPort = if (at == -1) authority else authority.substring(at + 1)
            val portColon = hostPort.lastIndexOf(':')
            if (portColon != -1 && hostPort.substring(portColon + 1).all { it.isDigit() } && !hostPort.endsWith("]")) {
                host = hostPort.substring(0, portColon)
                port = hostPort.substring(portColon + 1).toIntOrNull() ?: -1
            } else {
                host = hostPort
                port = -1
            }
        } else {
            authority = null
            userInfo = null
            host = ""
            port = -1
            path = rest
        }
    }

    fun getScheme(): String? = scheme
    fun getHost(): String = host
    fun getPort(): Int = port
    fun getPath(): String = path
    fun getQuery(): String? = query
    fun getFragment(): String? = fragment
    fun getAuthority(): String? = authority
    fun getUserInfo(): String? = userInfo
    fun isAbsolute(): Boolean = scheme != null

    fun resolve(str: String): URI {
        if (str.contains("://")) return URI(str)
        val base = "${scheme ?: "http"}://${authority ?: ""}"
        return when {
            str.startsWith("//") -> URI("${scheme ?: "http"}:$str")
            str.startsWith("/") -> URI(base + str)
            str.startsWith("?") -> URI(base + path + str)
            str.startsWith("#") -> URI(uriString.substringBefore('#') + str)
            else -> {
                val dir = path.ifEmpty { "/" }.substringBeforeLast('/', "")
                URI("$base$dir/$str")
            }
        }
    }

    fun resolve(uri: URI): URI = resolve(uri.toString())

    fun toURL(): URL = URL(uriString)

    override fun toString(): String = uriString
    override fun equals(other: Any?): Boolean = other is URI && other.uriString == uriString
    override fun hashCode(): Int = uriString.hashCode()

    companion object {
        fun create(str: String): URI = URI(str)
    }
}

/** java.net.URL on top of [URI]. */
class URL(private val spec: String) {
    private val uri = URI(spec)
    val protocol: String? get() = uri.scheme
    val host: String? get() = uri.host
    val port: Int get() = uri.port
    val path: String get() = uri.path
    val query: String? get() = uri.query
    val file: String get() = uri.path + (uri.query?.let { "?$it" } ?: "")

    fun getProtocol(): String? = protocol
    fun getHost(): String? = host
    fun getPort(): Int = port
    fun getPath(): String = path
    fun getQuery(): String? = query
    fun getFile(): String = file
    fun toURI(): URI = uri
    fun toExternalForm(): String = spec
    override fun toString(): String = spec

    /** Only `resource:` URLs (from ClassLoader.getResource) open; the sandbox fetches nothing here. */
    fun openStream(): java.io.InputStream {
        require(spec.startsWith("resource:")) { "URL.openStream is only supported for resources: $spec" }
        val path = spec.removePrefix("resource:").trimStart('/')
        val bytes = tachiyomi.shim.ResourceRegistry.get(path) ?: throw java.io.IOException("resource not found: $path")
        return java.io.ByteArrayInputStream(bytes)
    }

    fun readBytes(): ByteArray = openStream().readBytes()
    fun readText(): String = readBytes().decodeToString()
}

class URISyntaxException(input: String, reason: String) : Exception("$reason: $input")
class MalformedURLException(message: String? = null) : java.io.IOException(message)
