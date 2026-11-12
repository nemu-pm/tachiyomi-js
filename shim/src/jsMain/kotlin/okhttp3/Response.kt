package okhttp3

class Response internal constructor(
    val request: Request,
    val protocol: Protocol,
    val message: String,
    val code: Int,
    val headers: Headers,
    private val _body: ResponseBody?,
    val networkResponse: Response?,
    val cacheResponse: Response?,
    val priorResponse: Response?,
    val sentRequestAtMillis: Long,
    val receivedResponseAtMillis: Long,
) {
    constructor(request: Request, code: Int, message: String, headers: Headers, _body: ResponseBody?) :
        this(request, Protocol.HTTP_1_1, message, code, headers, _body, null, null, null, 0L, 0L)

    val body: ResponseBody
        get() = _body ?: EmptyResponseBody

    private object EmptyResponseBody : ResponseBody() {
        override fun string(): String = ""
        override fun bytes(): ByteArray = ByteArray(0)
        override val contentType: MediaType? = null
        override val contentLength: Long = 0
    }

    val isSuccessful: Boolean get() = code in 200..299

    val isRedirect: Boolean
        get() = when (code) {
            300, 301, 302, 303, 307, 308 -> true
            else -> false
        }

    val cacheControl: CacheControl get() = CacheControl.parse(headers)

    fun header(name: String, defaultValue: String? = null): String? = headers[name] ?: defaultValue

    fun headers(name: String): List<String> = headers.values(name)

    inline fun <T> use(block: (Response) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }

    fun close() {
        body.close()
    }

    /** The full body, re-readable; [byteCount] is ignored because the body is already in memory. */
    fun peekBody(byteCount: Long): ResponseBody {
        val bytes = body.bytes()
        val type = body.contentType
        return object : ResponseBody() {
            override fun string(): String = bytes.decodeToString()
            override fun bytes(): ByteArray = bytes
            override val contentType: MediaType? = type
            override val contentLength: Long = bytes.size.toLong()
        }
    }

    fun newBuilder(): Builder = Builder(this)

    override fun toString(): String = "Response{protocol=$protocol, code=$code, message=$message, url=${request.url}}"

    class Builder {
        private var request: Request? = null
        private var protocol: Protocol = Protocol.HTTP_1_1
        private var code: Int = -1
        private var message: String = ""
        private var headers: Headers.Builder = Headers.Builder()
        private var body: ResponseBody? = null
        private var networkResponse: Response? = null
        private var cacheResponse: Response? = null
        private var priorResponse: Response? = null
        private var sentRequestAtMillis: Long = 0
        private var receivedResponseAtMillis: Long = 0

        constructor()

        internal constructor(response: Response) {
            request = response.request
            protocol = response.protocol
            code = response.code
            message = response.message
            headers = response.headers.newBuilder()
            body = response._body
            networkResponse = response.networkResponse
            cacheResponse = response.cacheResponse
            priorResponse = response.priorResponse
            sentRequestAtMillis = response.sentRequestAtMillis
            receivedResponseAtMillis = response.receivedResponseAtMillis
        }

        fun request(request: Request): Builder = apply { this.request = request }
        fun protocol(protocol: Protocol): Builder = apply { this.protocol = protocol }
        fun code(code: Int): Builder = apply { this.code = code }
        fun message(message: String): Builder = apply { this.message = message }
        fun header(name: String, value: String): Builder = apply { headers.set(name, value) }
        fun addHeader(name: String, value: String): Builder = apply { headers.add(name, value) }
        fun removeHeader(name: String): Builder = apply { headers.removeAll(name) }
        fun headers(headers: Headers): Builder = apply { this.headers = headers.newBuilder() }
        fun body(body: ResponseBody?): Builder = apply { this.body = body }
        fun networkResponse(networkResponse: Response?): Builder = apply { this.networkResponse = networkResponse }
        fun cacheResponse(cacheResponse: Response?): Builder = apply { this.cacheResponse = cacheResponse }
        fun priorResponse(priorResponse: Response?): Builder = apply { this.priorResponse = priorResponse }
        fun sentRequestAtMillis(sentRequestAtMillis: Long): Builder = apply { this.sentRequestAtMillis = sentRequestAtMillis }
        fun receivedResponseAtMillis(receivedResponseAtMillis: Long): Builder = apply { this.receivedResponseAtMillis = receivedResponseAtMillis }

        fun build(): Response = Response(
            request = request ?: throw IllegalStateException("request == null"),
            protocol = protocol,
            message = message,
            code = code.also { check(it >= 0) { "code < 0: $it" } },
            headers = headers.build(),
            _body = body,
            networkResponse = networkResponse,
            cacheResponse = cacheResponse,
            priorResponse = priorResponse,
            sentRequestAtMillis = sentRequestAtMillis,
            receivedResponseAtMillis = receivedResponseAtMillis,
        )
    }
}

abstract class ResponseBody : java.io.Closeable {
    abstract fun string(): String
    abstract fun bytes(): ByteArray
    abstract val contentType: MediaType?
    abstract val contentLength: Long

    @JsName("getContentType")
    fun contentType(): MediaType? = contentType

    @JsName("getContentLength")
    fun contentLength(): Long = contentLength

    open fun byteStream(): java.io.InputStream = java.io.ByteArrayInputStream(bytes())

    open fun charStream(): java.io.Reader = java.io.StringReader(string())

    open fun source(): okio.BufferedSource {
        val buffer = okio.Buffer()
        buffer.write(bytes())
        return buffer
    }

    override fun close() {}

    companion object {
        fun String.toResponseBody(contentType: MediaType? = null): ResponseBody = StringResponseBody(this, contentType)

        fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody = ByteArrayResponseBody(this, contentType)

        fun okio.BufferedSource.asResponseBody(contentType: MediaType? = null, contentLength: Long = -1L): ResponseBody =
            ByteArrayResponseBody(readByteArray(), contentType)
    }
}

private class StringResponseBody(
    private val content: String,
    override val contentType: MediaType?,
) : ResponseBody() {
    override fun string(): String = content
    override fun bytes(): ByteArray = content.encodeToByteArray()
    override val contentLength: Long get() = bytes().size.toLong()
}

private class ByteArrayResponseBody(
    private val content: ByteArray,
    override val contentType: MediaType?,
) : ResponseBody() {
    override fun string(): String = content.decodeToString()
    override fun bytes(): ByteArray = content
    override val contentLength: Long = content.size.toLong()
}
