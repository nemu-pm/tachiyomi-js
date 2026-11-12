package okhttp3

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.internal.SyncHttpResult
import okhttp3.internal.base64ToBytes
import okhttp3.internal.syncHttpRequestBytes
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

// Top-level type alias for OkHttpClient.Call
typealias Call = OkHttpClient.Call

enum class Protocol(private val protocol: String) {
    HTTP_1_0("http/1.0"),
    HTTP_1_1("http/1.1"),
    HTTP_2("h2"),
    H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),
    QUIC("quic"),
    HTTP_3("h3"),
    ;

    override fun toString(): String = protocol

    companion object {
        fun get(protocol: String): Protocol = entries.firstOrNull { it.protocol == protocol }
            ?: throw java.io.IOException("Unexpected protocol: $protocol")
    }
}

/** No request queue in a single-threaded runtime; the limits are kept for callers that set them. */
class Dispatcher {
    var maxRequests: Int = 64
    var maxRequestsPerHost: Int = 5
}

interface CookieJar {
    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)
    fun loadForRequest(url: HttpUrl): List<Cookie>

    companion object {
        val NO_COOKIES: CookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
        }
    }
}

interface Dns {
    fun lookup(hostname: String): List<java.net.InetAddress>

    companion object {
        val SYSTEM: Dns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = emptyList()
        }
    }
}

/** The host fetch layer owns caching; this only satisfies `cache(...)` calls. */
class Cache(val directory: java.io.File, val maxSize: Long)

/**
 * OkHttpClient shim for Kotlin/JS.
 * HTTP requests go through the host's synchronous bridge (sync XHR in a Web Worker, or a sync
 * fetch shim under node/bun); interceptors and network interceptors run in order around it.
 */
class OkHttpClient private constructor(
    val interceptors: List<Interceptor>,
    val networkInterceptors: List<Interceptor>,
    val cookieJar: CookieJar,
    val cache: Cache?,
    val dispatcher: Dispatcher,
    val followRedirects: Boolean,
    val callTimeoutMillis: Int,
) {
    constructor() : this(emptyList(), emptyList(), CookieJar.NO_COOKIES, null, Dispatcher(), true, 0)

    fun newCall(request: Request): Call = Call(request, this)

    fun newBuilder(): Builder = Builder(this)

    class Builder {
        private val _interceptors: MutableList<Interceptor> = mutableListOf()
        private val _networkInterceptors: MutableList<Interceptor> = mutableListOf()
        private var cookieJar: CookieJar = CookieJar.NO_COOKIES
        private var cache: Cache? = null
        private var dispatcher: Dispatcher = Dispatcher()
        private var followRedirects: Boolean = true
        private var callTimeoutMillis: Int = 0

        constructor()

        internal constructor(client: OkHttpClient) {
            _interceptors.addAll(client.interceptors)
            _networkInterceptors.addAll(client.networkInterceptors)
            cookieJar = client.cookieJar
            cache = client.cache
            dispatcher = client.dispatcher
            followRedirects = client.followRedirects
            callTimeoutMillis = client.callTimeoutMillis
        }

        /** The application interceptors, in order; mutable so callers can insert at an index. */
        fun interceptors(): MutableList<Interceptor> = _interceptors

        fun networkInterceptors(): MutableList<Interceptor> = _networkInterceptors

        fun addInterceptor(interceptor: Interceptor): Builder = apply { _interceptors.add(interceptor) }

        fun addInterceptor(block: (Interceptor.Chain) -> Response): Builder = addInterceptor(Interceptor(block))

        fun addNetworkInterceptor(interceptor: Interceptor): Builder = apply { _networkInterceptors.add(interceptor) }

        fun addNetworkInterceptor(block: (Interceptor.Chain) -> Response): Builder = addNetworkInterceptor(Interceptor(block))

        fun cookieJar(cookieJar: CookieJar): Builder = apply { this.cookieJar = cookieJar }
        fun cache(cache: Cache?): Builder = apply { this.cache = cache }
        fun dispatcher(dispatcher: Dispatcher): Builder = apply { this.dispatcher = dispatcher }
        fun dns(dns: Dns): Builder = this
        /** TLS is the host's business; trust-all configurations compile and change nothing. */
        fun sslSocketFactory(sslSocketFactory: javax.net.ssl.SSLSocketFactory, trustManager: javax.net.ssl.X509TrustManager): Builder = this
        fun hostnameVerifier(hostnameVerifier: javax.net.ssl.HostnameVerifier): Builder = this
        fun followRedirects(followRedirects: Boolean): Builder = apply { this.followRedirects = followRedirects }
        fun followSslRedirects(followProtocolRedirects: Boolean): Builder = this
        fun retryOnConnectionFailure(retryOnConnectionFailure: Boolean): Builder = this
        fun protocols(protocols: List<Protocol>): Builder = this
        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun connectTimeout(duration: Duration): Builder = this
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun readTimeout(duration: Duration): Builder = this
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun writeTimeout(duration: Duration): Builder = this
        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = apply { callTimeoutMillis = unit.toMillis(timeout).toInt() }
        fun callTimeout(duration: Duration): Builder = apply { callTimeoutMillis = duration.inWholeMilliseconds.toInt() }

        fun build(): OkHttpClient = OkHttpClient(
            _interceptors.toList(), _networkInterceptors.toList(), cookieJar, cache, dispatcher, followRedirects, callTimeoutMillis,
        )
    }

    class Call internal constructor(
        private val originalRequest: Request,
        private val client: OkHttpClient,
    ) {
        private var executed = false
        private var canceled = false

        fun request(): Request = originalRequest

        fun isExecuted(): Boolean = executed

        fun isCanceled(): Boolean = canceled

        fun cancel() {
            canceled = true
        }

        fun clone(): Call = Call(originalRequest, client)

        /** Runs the interceptor chain and the host bridge synchronously. */
        fun execute(): Response {
            check(!executed) { "Already Executed" }
            executed = true
            if (canceled) throw java.io.IOException("Canceled")
            val chain = RealInterceptorChain(originalRequest, client.interceptors + client.networkInterceptors, 0, this) { req ->
                makeNetworkCall(req, client)
            }
            return chain.proceed(originalRequest)
        }

        /** Single-threaded: executes now and invokes the callback before returning. */
        fun enqueue(callback: Callback) {
            try {
                val response = execute()
                callback.onResponse(this, response)
            } catch (e: java.io.IOException) {
                callback.onFailure(this, e)
            } catch (e: Exception) {
                callback.onFailure(this, java.io.IOException(e.message ?: "Unknown error", e))
            }
        }
    }
}

fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
        fun call(): Call
        fun connection(): Connection? = null
        fun connectTimeoutMillis(): Int = 0
        fun readTimeoutMillis(): Int = 0
        fun writeTimeoutMillis(): Int = 0
        fun withConnectTimeout(timeout: Int, unit: TimeUnit): Chain = this
        fun withReadTimeout(timeout: Int, unit: TimeUnit): Chain = this
        fun withWriteTimeout(timeout: Int, unit: TimeUnit): Chain = this
    }
}

interface Connection {
    fun protocol(): Protocol
}

private class RealInterceptorChain(
    private val _request: Request,
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val _call: Call,
    private val networkCall: (Request) -> Response,
) : Interceptor.Chain {
    override fun request(): Request = _request
    override fun call(): Call = _call
    override fun proceed(request: Request): Response {
        if (index >= interceptors.size) return networkCall(request)
        val next = RealInterceptorChain(request, interceptors, index + 1, _call, networkCall)
        return interceptors[index].intercept(next)
    }
}

/**
 * Make the actual HTTP request through the host bridge. Always fetches as binary (base64) so
 * text and binary responses are handled alike. Cookies from the client's jar go out as `Cookie`.
 */
internal fun makeNetworkCall(request: Request, client: OkHttpClient): Response {
    val url = request.url.toString()
    val method = request.method

    val headersMap = linkedMapOf<String, String>()
    for (name in request.headers.names()) {
        headersMap[name] = request.headers.values(name).joinToString("; ")
    }

    val jarCookies = client.cookieJar.loadForRequest(request.url)
    if (jarCookies.isNotEmpty() && headersMap.keys.none { it.equals("Cookie", ignoreCase = true) }) {
        headersMap["Cookie"] = jarCookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    val requestBody = request.body
    if (requestBody != null && headersMap.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
        requestBody.contentType?.let { headersMap["Content-Type"] = it.toString() }
    }

    val headersJson = Json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), headersMap)

    val body = requestBody?.writeTo()?.decodeToString()

    val started = kotlin.js.Date.now().toLong()
    val result: SyncHttpResult = syncHttpRequestBytes(url, method, headersJson, body)
    val finished = kotlin.js.Date.now().toLong()

    if (result.error != null) {
        throw java.io.IOException("HTTP request failed: ${result.error}")
    }

    val responseHeaders = parseHeaders(result.headersJson)

    val setCookies = responseHeaders.values("Set-Cookie").mapNotNull { Cookie.parse(request.url, it) }
    if (setCookies.isNotEmpty()) client.cookieJar.saveFromResponse(request.url, setCookies)

    val bodyBytes = base64ToBytes(result.body)

    val contentType = responseHeaders["content-type"]?.let { MediaType.run { it.toMediaTypeOrNull() } }
    val responseBody = object : ResponseBody() {
        override fun string(): String = bodyBytes.decodeToString()
        override fun bytes(): ByteArray = bodyBytes
        override val contentType: MediaType? = contentType
        override val contentLength: Long = bodyBytes.size.toLong()
    }

    return Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(result.status)
        .message(result.statusText)
        .headers(responseHeaders)
        .body(responseBody)
        .sentRequestAtMillis(started)
        .receivedResponseAtMillis(finished)
        .build()
}

private fun parseHeaders(json: String): Headers {
    val builder = Headers.Builder()
    try {
        val jsonObj = Json.parseToJsonElement(json) as JsonObject
        for ((key, value) in jsonObj) {
            if (value is JsonArray) {
                for (v in value.jsonArray) builder.add(key, v.jsonPrimitive.content)
            } else {
                builder.add(key, value.jsonPrimitive.content)
            }
        }
    } catch (_: Exception) {
        // Malformed header JSON from the bridge: treat as no headers
    }
    return builder.build()
}
