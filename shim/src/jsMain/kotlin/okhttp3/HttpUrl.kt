package okhttp3

// Top-level extension functions for compatibility
fun String.toHttpUrl(): HttpUrl = HttpUrl.run { toHttpUrl() }
fun String.toHttpUrlOrNull(): HttpUrl? = HttpUrl.run { toHttpUrlOrNull() }

/**
 * OkHttp's HttpUrl, ported closely enough for extension code: percent-encoding follows OkHttp's
 * encode sets (so query values keep `/` and `:` and encode a space as `%20`), `queryParameter`
 * treats `+` as a space, and `resolve` handles relative links the way browsers do.
 */
class HttpUrl private constructor(
    val scheme: String,
    val username: String,
    val password: String,
    val host: String,
    val port: Int,
    val encodedPathSegments: List<String>,
    private val encodedQueryNamesAndValues: List<String?>?,
    val encodedFragment: String?,
) {
    val isHttps: Boolean get() = scheme == "https"

    val encodedUsername: String get() = username
    val encodedPassword: String get() = password

    val encodedPath: String get() = "/" + encodedPathSegments.joinToString("/")

    val pathSegments: List<String> get() = encodedPathSegments.map { percentDecode(it, false) }

    val pathSize: Int get() = encodedPathSegments.size

    val encodedQuery: String?
        get() = encodedQueryNamesAndValues?.let { toQueryString(it) }

    val query: String?
        get() = encodedQueryNamesAndValues?.let { toQueryString(it.map { v -> v?.let { s -> percentDecode(s, true) } }) }

    val querySize: Int get() = (encodedQueryNamesAndValues?.size ?: 0) / 2

    val queryParameterNames: Set<String>
        get() {
            val list = encodedQueryNamesAndValues ?: return emptySet()
            val result = LinkedHashSet<String>()
            for (i in list.indices step 2) result.add(percentDecode(list[i]!!, true))
            return result
        }

    val fragment: String? get() = encodedFragment?.let { percentDecode(it, false) }

    fun queryParameter(name: String): String? {
        val list = encodedQueryNamesAndValues ?: return null
        for (i in list.indices step 2) {
            if (percentDecode(list[i]!!, true) == name) return list[i + 1]?.let { percentDecode(it, true) }
        }
        return null
    }

    fun queryParameterValues(name: String): List<String?> {
        val list = encodedQueryNamesAndValues ?: return emptyList()
        val result = mutableListOf<String?>()
        for (i in list.indices step 2) {
            if (percentDecode(list[i]!!, true) == name) result.add(list[i + 1]?.let { percentDecode(it, true) })
        }
        return result
    }

    fun queryParameterName(index: Int): String = percentDecode(encodedQueryNamesAndValues!![index * 2]!!, true)

    fun queryParameterValue(index: Int): String? = encodedQueryNamesAndValues!![index * 2 + 1]?.let { percentDecode(it, true) }

    /** Public-suffix aware in OkHttp; here the last two labels of a dotted host (good enough for Referer/Origin use). */
    fun topPrivateDomain(): String? {
        if (host.isEmpty() || host.last().isDigit()) return null
        val labels = host.split('.')
        if (labels.size < 2) return null
        val twoLevelSuffix = labels.size >= 3 && labels[labels.size - 1].length == 2 && labels[labels.size - 2].length <= 3
        return if (twoLevelSuffix) labels.takeLast(3).joinToString(".") else labels.takeLast(2).joinToString(".")
    }

    fun resolve(link: String): HttpUrl? = newBuilder(link)?.build()

    fun newBuilder(): Builder = Builder().also {
        it.scheme = scheme
        it.encodedUsername = username
        it.encodedPassword = password
        it.host = host
        it.port = if (port != defaultPort(scheme)) port else -1
        it.encodedPathSegments.clear()
        it.encodedPathSegments.addAll(encodedPathSegments)
        it.encodedQueryNamesAndValues = encodedQueryNamesAndValues?.toMutableList()
        it.encodedFragment = encodedFragment
    }

    fun newBuilder(link: String): Builder? = try {
        Builder().parse(this, link)
    } catch (_: IllegalArgumentException) {
        null
    }

    fun toUri(): java.net.URI = java.net.URI.create(toString())

    fun redact(): String = newBuilder("/...")!!.username("").password("").build().toString()

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(scheme).append("://")
        if (username.isNotEmpty() || password.isNotEmpty()) {
            sb.append(username)
            if (password.isNotEmpty()) sb.append(':').append(password)
            sb.append('@')
        }
        if (':' in host) sb.append('[').append(host).append(']') else sb.append(host)
        if (port != defaultPort(scheme)) sb.append(':').append(port)
        sb.append(encodedPath)
        encodedQueryNamesAndValues?.let { sb.append('?').append(toQueryString(it)) }
        encodedFragment?.let { sb.append('#').append(it) }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean = other is HttpUrl && other.toString() == toString()
    override fun hashCode(): Int = toString().hashCode()

    class Builder {
        internal var scheme: String? = null
        internal var encodedUsername: String = ""
        internal var encodedPassword: String = ""
        internal var host: String? = null
        internal var port: Int = -1
        internal val encodedPathSegments = mutableListOf("")
        internal var encodedQueryNamesAndValues: MutableList<String?>? = null
        internal var encodedFragment: String? = null

        fun scheme(scheme: String): Builder = apply {
            this.scheme = when {
                scheme.equals("http", ignoreCase = true) -> "http"
                scheme.equals("https", ignoreCase = true) -> "https"
                else -> throw IllegalArgumentException("unexpected scheme: $scheme")
            }
        }

        fun username(username: String): Builder = apply { encodedUsername = canonicalize(username, USERNAME_ENCODE_SET) }
        fun password(password: String): Builder = apply { encodedPassword = canonicalize(password, PASSWORD_ENCODE_SET) }
        fun encodedUsername(encodedUsername: String): Builder = apply { this.encodedUsername = canonicalize(encodedUsername, USERNAME_ENCODE_SET, alreadyEncoded = true) }
        fun encodedPassword(encodedPassword: String): Builder = apply { this.encodedPassword = canonicalize(encodedPassword, PASSWORD_ENCODE_SET, alreadyEncoded = true) }

        fun host(host: String): Builder = apply {
            require(host.isNotEmpty()) { "unexpected host: $host" }
            this.host = host.lowercase()
        }

        fun port(port: Int): Builder = apply {
            require(port in 1..65535) { "unexpected port: $port" }
            this.port = port
        }

        fun addPathSegment(pathSegment: String): Builder = apply { push(pathSegment, 0, pathSegment.length, addTrailingSlash = false, alreadyEncoded = false) }

        fun addPathSegments(pathSegments: String): Builder = addPathSegments(pathSegments, false)

        fun addEncodedPathSegment(encodedPathSegment: String): Builder = apply { push(encodedPathSegment, 0, encodedPathSegment.length, addTrailingSlash = false, alreadyEncoded = true) }

        fun addEncodedPathSegments(encodedPathSegments: String): Builder = addPathSegments(encodedPathSegments, true)

        private fun addPathSegments(pathSegments: String, alreadyEncoded: Boolean): Builder = apply {
            var offset = 0
            do {
                var segmentEnd = pathSegments.indexOfAny(charArrayOf('/', '\\'), offset)
                if (segmentEnd == -1) segmentEnd = pathSegments.length
                val addTrailingSlash = segmentEnd < pathSegments.length
                push(pathSegments, offset, segmentEnd, addTrailingSlash, alreadyEncoded)
                offset = segmentEnd + 1
            } while (offset <= pathSegments.length)
        }

        fun setPathSegment(index: Int, pathSegment: String): Builder = apply {
            val canonical = canonicalize(pathSegment, PATH_SEGMENT_ENCODE_SET)
            require(!isDot(canonical) && !isDotDot(canonical)) { "unexpected path segment: $pathSegment" }
            encodedPathSegments[index] = canonical
        }

        fun setEncodedPathSegment(index: Int, encodedPathSegment: String): Builder = apply {
            val canonical = canonicalize(encodedPathSegment, PATH_SEGMENT_ENCODE_SET, alreadyEncoded = true)
            encodedPathSegments[index] = canonical
            require(!isDot(canonical) && !isDotDot(canonical)) { "unexpected path segment: $encodedPathSegment" }
        }

        fun removePathSegment(index: Int): Builder = apply {
            encodedPathSegments.removeAt(index)
            if (encodedPathSegments.isEmpty()) encodedPathSegments.add("")
        }

        fun encodedPath(encodedPath: String): Builder = apply {
            require(encodedPath.startsWith("/")) { "unexpected encodedPath: $encodedPath" }
            resolvePath(encodedPath, 0, encodedPath.length)
        }

        fun query(query: String?): Builder = apply {
            encodedQueryNamesAndValues = query?.let { toQueryNamesAndValues(canonicalize(it, QUERY_ENCODE_SET, plusIsSpace = true)) }
        }

        fun encodedQuery(encodedQuery: String?): Builder = apply {
            encodedQueryNamesAndValues = encodedQuery?.let { toQueryNamesAndValues(canonicalize(it, QUERY_ENCODE_SET, alreadyEncoded = true, plusIsSpace = true)) }
        }

        fun addQueryParameter(name: String, value: String?): Builder = apply {
            if (encodedQueryNamesAndValues == null) encodedQueryNamesAndValues = mutableListOf()
            encodedQueryNamesAndValues!!.add(canonicalize(name, QUERY_COMPONENT_ENCODE_SET, plusIsSpace = true))
            encodedQueryNamesAndValues!!.add(value?.let { canonicalize(it, QUERY_COMPONENT_ENCODE_SET, plusIsSpace = true) })
        }

        /** Convenience kept from the earlier shim: one parameter per value. Non-null so `addQueryParameter(name, null)` resolves to the OkHttp overload. */
        fun addQueryParameter(name: String, values: Iterable<String>): Builder = apply {
            values.forEach { addQueryParameter(name, it) }
        }

        fun addEncodedQueryParameter(encodedName: String, encodedValue: String?): Builder = apply {
            if (encodedQueryNamesAndValues == null) encodedQueryNamesAndValues = mutableListOf()
            encodedQueryNamesAndValues!!.add(canonicalize(encodedName, QUERY_COMPONENT_REENCODE_SET, alreadyEncoded = true, plusIsSpace = true))
            encodedQueryNamesAndValues!!.add(encodedValue?.let { canonicalize(it, QUERY_COMPONENT_REENCODE_SET, alreadyEncoded = true, plusIsSpace = true) })
        }

        fun setQueryParameter(name: String, value: String?): Builder = apply {
            removeAllQueryParameters(name)
            addQueryParameter(name, value)
        }

        fun setEncodedQueryParameter(encodedName: String, encodedValue: String?): Builder = apply {
            removeAllEncodedQueryParameters(encodedName)
            addEncodedQueryParameter(encodedName, encodedValue)
        }

        fun removeAllQueryParameters(name: String): Builder = apply {
            if (encodedQueryNamesAndValues == null) return this
            removeAllCanonicalQueryParameters(canonicalize(name, QUERY_COMPONENT_ENCODE_SET, plusIsSpace = true))
        }

        fun removeAllEncodedQueryParameters(encodedName: String): Builder = apply {
            if (encodedQueryNamesAndValues == null) return this
            removeAllCanonicalQueryParameters(canonicalize(encodedName, QUERY_COMPONENT_REENCODE_SET, alreadyEncoded = true, plusIsSpace = true))
        }

        private fun removeAllCanonicalQueryParameters(canonicalName: String) {
            val list = encodedQueryNamesAndValues!!
            var i = list.size - 2
            while (i >= 0) {
                if (list[i] == canonicalName) {
                    list.removeAt(i + 1)
                    list.removeAt(i)
                    if (list.isEmpty()) {
                        encodedQueryNamesAndValues = null
                        return
                    }
                }
                i -= 2
            }
        }

        fun fragment(fragment: String?): Builder = apply {
            encodedFragment = fragment?.let { canonicalize(it, FRAGMENT_ENCODE_SET, unicodeAllowed = true) }
        }

        fun encodedFragment(encodedFragment: String?): Builder = apply {
            this.encodedFragment = encodedFragment?.let { canonicalize(it, FRAGMENT_ENCODE_SET, alreadyEncoded = true, unicodeAllowed = true) }
        }

        fun build(): HttpUrl {
            val scheme = this.scheme ?: throw IllegalStateException("scheme == null")
            val host = this.host ?: throw IllegalStateException("host == null")
            return HttpUrl(
                scheme = scheme,
                username = encodedUsername,
                password = encodedPassword,
                host = host,
                port = if (port != -1) port else defaultPort(scheme),
                encodedPathSegments = encodedPathSegments.toList(),
                encodedQueryNamesAndValues = encodedQueryNamesAndValues?.toList(),
                encodedFragment = encodedFragment,
            )
        }

        override fun toString(): String = build().toString()

        private fun push(input: String, pos: Int, limit: Int, addTrailingSlash: Boolean, alreadyEncoded: Boolean) {
            val segment = canonicalize(input.substring(pos, limit), PATH_SEGMENT_ENCODE_SET, alreadyEncoded = alreadyEncoded)
            if (isDot(segment)) return
            if (isDotDot(segment)) {
                pop()
                return
            }
            if (encodedPathSegments[encodedPathSegments.size - 1].isEmpty()) {
                encodedPathSegments[encodedPathSegments.size - 1] = segment
            } else {
                encodedPathSegments.add(segment)
            }
            if (addTrailingSlash) encodedPathSegments.add("")
        }

        private fun pop() {
            val removed = encodedPathSegments.removeAt(encodedPathSegments.size - 1)
            if (removed.isEmpty() && encodedPathSegments.isNotEmpty()) {
                encodedPathSegments[encodedPathSegments.size - 1] = ""
            } else {
                encodedPathSegments.add("")
            }
        }

        private fun isDot(input: String) = input == "." || input.equals("%2e", ignoreCase = true)

        private fun isDotDot(input: String) =
            input == ".." || input.equals("%2e.", ignoreCase = true) || input.equals(".%2e", ignoreCase = true) || input.equals("%2e%2e", ignoreCase = true)

        private fun resolvePath(input: String, startPos: Int, limit: Int) {
            var pos = startPos
            val c = input[pos]
            if (c == '/' || c == '\\') {
                encodedPathSegments.clear()
                encodedPathSegments.add("")
                pos++
            } else {
                encodedPathSegments[encodedPathSegments.size - 1] = ""
            }
            var i = pos
            while (i < limit) {
                var segmentEnd = input.indexOfAny(charArrayOf('/', '\\'), i)
                if (segmentEnd == -1 || segmentEnd > limit) segmentEnd = limit
                val segmentHasTrailingSlash = segmentEnd < limit
                push(input, i, segmentEnd, segmentHasTrailingSlash, true)
                i = segmentEnd
                if (segmentHasTrailingSlash) i++
            }
        }

        internal fun parse(base: HttpUrl?, rawInput: String): Builder {
            var pos = skipLeadingAsciiWhitespace(rawInput)
            val limit = skipTrailingAsciiWhitespace(rawInput, pos)
            val input = rawInput

            val schemeDelimiterOffset = schemeDelimiterOffset(input, pos, limit)
            if (schemeDelimiterOffset != -1) {
                val s = input.substring(pos, schemeDelimiterOffset)
                scheme = when {
                    s.equals("https", ignoreCase = true) -> "https"
                    s.equals("http", ignoreCase = true) -> "http"
                    else -> throw IllegalArgumentException("Expected URL scheme 'http' or 'https' but was '$s'")
                }
                pos += s.length + 1
            } else if (base != null) {
                scheme = base.scheme
            } else {
                throw IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found for ${input.take(6)}...")
            }

            var hasUsername = false
            var hasPassword = false
            val slashCount = slashCount(input, pos, limit)
            if (slashCount >= 2 || base == null || base.scheme != scheme) {
                pos += slashCount
                authority@ while (true) {
                    val componentDelimiterOffset = delimiterOffset(input, pos, limit, "@/\\?#")
                    val c = if (componentDelimiterOffset != limit) input[componentDelimiterOffset] else null
                    when (c) {
                        '@' -> {
                            if (!hasPassword) {
                                val passwordColonOffset = delimiterOffset(input, pos, componentDelimiterOffset, ":")
                                val canonicalUsername = canonicalize(input.substring(pos, passwordColonOffset), USERNAME_ENCODE_SET, alreadyEncoded = true)
                                encodedUsername = if (hasUsername) "$encodedUsername%40$canonicalUsername" else canonicalUsername
                                if (passwordColonOffset != componentDelimiterOffset) {
                                    hasPassword = true
                                    encodedPassword = canonicalize(input.substring(passwordColonOffset + 1, componentDelimiterOffset), PASSWORD_ENCODE_SET, alreadyEncoded = true)
                                }
                                hasUsername = true
                            } else {
                                encodedPassword = encodedPassword + "%40" + canonicalize(input.substring(pos, componentDelimiterOffset), PASSWORD_ENCODE_SET, alreadyEncoded = true)
                            }
                            pos = componentDelimiterOffset + 1
                        }
                        null, '/', '\\', '?', '#' -> {
                            val portColonOffset = portColonOffset(input, pos, componentDelimiterOffset)
                            if (portColonOffset + 1 < componentDelimiterOffset) {
                                host = parseHost(input.substring(pos, portColonOffset))
                                port = input.substring(portColonOffset + 1, componentDelimiterOffset).toIntOrNull()
                                    ?: throw IllegalArgumentException("Invalid URL port: \"${input.substring(portColonOffset + 1, componentDelimiterOffset)}\"")
                                require(port in 1..65535) { "Invalid URL port: $port" }
                            } else {
                                host = parseHost(input.substring(pos, portColonOffset))
                                port = defaultPort(scheme!!)
                            }
                            require(!host.isNullOrEmpty()) { "Invalid URL host: \"${input.substring(pos, portColonOffset)}\"" }
                            pos = componentDelimiterOffset
                            break@authority
                        }
                    }
                }
            } else {
                encodedUsername = base.username
                encodedPassword = base.password
                host = base.host
                port = base.port
                encodedPathSegments.clear()
                encodedPathSegments.addAll(base.encodedPathSegments)
                if (pos == limit || input[pos] == '#') {
                    encodedQuery(base.encodedQuery)
                }
            }

            val pathDelimiterOffset = delimiterOffset(input, pos, limit, "?#")
            resolvePath(input, pos, pathDelimiterOffset)
            pos = pathDelimiterOffset

            if (pos < limit && input[pos] == '?') {
                val queryDelimiterOffset = delimiterOffset(input, pos, limit, "#")
                encodedQueryNamesAndValues = toQueryNamesAndValues(
                    canonicalize(input.substring(pos + 1, queryDelimiterOffset), QUERY_ENCODE_SET, alreadyEncoded = true, plusIsSpace = true),
                )
                pos = queryDelimiterOffset
            }

            if (pos < limit && input[pos] == '#') {
                encodedFragment = canonicalize(input.substring(pos + 1, limit), FRAGMENT_ENCODE_SET, alreadyEncoded = true, unicodeAllowed = true)
            }

            return this
        }

        private fun parseHost(input: String): String {
            val decoded = percentDecode(input, false)
            val host = if (decoded.startsWith("[") && decoded.endsWith("]")) decoded.substring(1, decoded.length - 1) else decoded
            return host.lowercase()
        }

        private fun skipLeadingAsciiWhitespace(input: String): Int {
            var i = 0
            while (i < input.length && input[i] in " \t\n\r\u000c") i++
            return i
        }

        private fun skipTrailingAsciiWhitespace(input: String, pos: Int): Int {
            var i = input.length
            while (i > pos && input[i - 1] in " \t\n\r\u000c") i--
            return i
        }

        private fun schemeDelimiterOffset(input: String, pos: Int, limit: Int): Int {
            if (limit - pos < 2) return -1
            val c0 = input[pos]
            if ((c0 < 'a' || c0 > 'z') && (c0 < 'A' || c0 > 'Z')) return -1
            for (i in pos + 1 until limit) {
                val c = input[i]
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '+' || c == '-' || c == '.') continue
                return if (c == ':') i else -1
            }
            return -1
        }

        private fun slashCount(input: String, pos: Int, limit: Int): Int {
            var count = 0
            var i = pos
            while (i < limit && (input[i] == '/' || input[i] == '\\')) {
                count++
                i++
            }
            return count
        }

        private fun delimiterOffset(input: String, pos: Int, limit: Int, delimiters: String): Int {
            for (i in pos until limit) if (input[i] in delimiters) return i
            return limit
        }

        private fun portColonOffset(input: String, pos: Int, limit: Int): Int {
            var i = pos
            while (i < limit) {
                when (input[i]) {
                    '[' -> while (++i < limit) if (input[i] == ']') break
                    ':' -> return i
                }
                i++
            }
            return limit
        }
    }

    companion object {
        private const val USERNAME_ENCODE_SET = " \"':;<=>@[]^`{}|/\\?#"
        private const val PASSWORD_ENCODE_SET = " \"':;<=>@[]^`{}|/\\?#"
        private const val PATH_SEGMENT_ENCODE_SET = " \"<>^`{}|/\\?#"
        private const val QUERY_ENCODE_SET = " \"'<>#"
        private const val QUERY_COMPONENT_REENCODE_SET = " \"'<>#&="
        private const val QUERY_COMPONENT_ENCODE_SET = " !\"#$&'(),/:;<=>?@[]\\^`{|}~"
        private const val FRAGMENT_ENCODE_SET = ""
        internal const val FORM_ENCODE_SET = " !\"#$&'()+,/:;<=>?@[\\]^`{|}~"

        fun defaultPort(scheme: String): Int = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }

        fun String.toHttpUrl(): HttpUrl = Builder().parse(null, this).build()

        fun String.toHttpUrlOrNull(): HttpUrl? = try {
            toHttpUrl()
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun toQueryString(list: List<String?>): String {
            val sb = StringBuilder()
            for (i in list.indices step 2) {
                if (i > 0) sb.append('&')
                sb.append(list[i])
                list[i + 1]?.let { sb.append('=').append(it) }
            }
            return sb.toString()
        }

        private fun toQueryNamesAndValues(encodedQuery: String): MutableList<String?> {
            val result = mutableListOf<String?>()
            var pos = 0
            while (pos <= encodedQuery.length) {
                var ampersandOffset = encodedQuery.indexOf('&', pos)
                if (ampersandOffset == -1) ampersandOffset = encodedQuery.length
                val equalsOffset = encodedQuery.indexOf('=', pos)
                if (equalsOffset == -1 || equalsOffset > ampersandOffset) {
                    result.add(encodedQuery.substring(pos, ampersandOffset))
                    result.add(null)
                } else {
                    result.add(encodedQuery.substring(pos, equalsOffset))
                    result.add(encodedQuery.substring(equalsOffset + 1, ampersandOffset))
                }
                pos = ampersandOffset + 1
            }
            return result
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        private fun hexValue(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }

        internal fun percentDecode(encoded: String, plusIsSpace: Boolean): String {
            var needsDecode = false
            for (c in encoded) if (c == '%' || (c == '+' && plusIsSpace)) {
                needsDecode = true
                break
            }
            if (!needsDecode) return encoded
            val out = ArrayList<Byte>(encoded.length)
            var i = 0
            while (i < encoded.length) {
                val c = encoded[i]
                if (c == '%' && i + 2 < encoded.length + 0 && i + 2 <= encoded.length - 1 && isHex(encoded[i + 1]) && isHex(encoded[i + 2])) {
                    out.add(((hexValue(encoded[i + 1]) shl 4) + hexValue(encoded[i + 2])).toByte())
                    i += 3
                    continue
                }
                if (c == '+' && plusIsSpace) {
                    out.add(' '.code.toByte())
                    i++
                    continue
                }
                for (b in c.toString().encodeToByteArray()) out.add(b)
                i++
            }
            return out.toByteArray().decodeToString()
        }

        internal fun canonicalize(
            input: String,
            encodeSet: String,
            alreadyEncoded: Boolean = false,
            plusIsSpace: Boolean = false,
            unicodeAllowed: Boolean = false,
        ): String {
            val sb = StringBuilder()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                val code = c.code
                val encode = when {
                    c == '%' && alreadyEncoded -> !(i + 2 < input.length && isHex(input[i + 1]) && isHex(input[i + 2]))
                    c == '%' -> true
                    c == '+' && plusIsSpace -> true
                    code < 0x20 || code == 0x7f -> true
                    code >= 0x80 -> !unicodeAllowed
                    else -> c in encodeSet
                }
                if (!encode) {
                    sb.append(c)
                    i++
                    continue
                }
                if (c == ' ' && encodeSet === FORM_ENCODE_SET) {
                    sb.append('+')
                    i++
                    continue
                }
                // Encode the whole code point (surrogate pairs) as UTF-8
                val end = if (c.isHighSurrogate() && i + 1 < input.length && input[i + 1].isLowSurrogate()) i + 2 else i + 1
                for (b in input.substring(i, end).encodeToByteArray()) {
                    val v = b.toInt() and 0xff
                    sb.append('%').append(HEX_DIGITS[v shr 4]).append(HEX_DIGITS[v and 0xf])
                }
                i = end
            }
            return sb.toString()
        }

        private val HEX_DIGITS = "0123456789ABCDEF"
    }
}
