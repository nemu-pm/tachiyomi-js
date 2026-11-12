package okhttp3

import java.lang.Class as JClass
import tachiyomi.shim.compat.java

class Request internal constructor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
    private val lazyCacheControl: CacheControl?,
    val tags: Map<JClass<*>, Any>,
) {
    val cacheControl: CacheControl get() = lazyCacheControl ?: CacheControl.parse(headers)

    val isHttps: Boolean get() = url.isHttps

    fun header(name: String): String? = headers[name]

    fun headers(name: String): List<String> = headers.values(name)

    fun tag(): Any? = tag(Any::class.java)

    @Suppress("UNCHECKED_CAST")
    fun <T> tag(type: JClass<T>): T? = tags[type] as T?

    fun newBuilder(): Builder = Builder(this)

    override fun toString(): String = "Request{method=$method, url=$url}"

    class Builder {
        private var url: HttpUrl? = null
        private var method: String = "GET"
        private var headers: Headers.Builder = Headers.Builder()
        private var body: RequestBody? = null
        private var tags: MutableMap<JClass<*>, Any> = mutableMapOf()

        constructor()

        internal constructor(request: Request) {
            url = request.url
            method = request.method
            body = request.body
            headers = request.headers.newBuilder()
            tags = request.tags.toMutableMap()
        }

        fun url(url: String): Builder = apply { this.url = url.toHttpUrl() }

        fun url(url: HttpUrl): Builder = apply { this.url = url }

        fun url(url: HttpUrl.Builder): Builder = apply { this.url = url.build() }

        fun header(name: String, value: String): Builder = apply { headers.set(name, value) }

        fun addHeader(name: String, value: String): Builder = apply { headers.add(name, value) }

        fun removeHeader(name: String): Builder = apply { headers.removeAll(name) }

        fun headers(headers: Headers): Builder = apply { this.headers = headers.newBuilder() }

        fun cacheControl(cacheControl: CacheControl): Builder {
            val value = cacheControl.toString()
            return if (value.isEmpty()) removeHeader("Cache-Control") else header("Cache-Control", value)
        }

        fun get(): Builder = method("GET", null)

        fun head(): Builder = method("HEAD", null)

        fun post(body: RequestBody): Builder = method("POST", body)

        fun put(body: RequestBody): Builder = method("PUT", body)

        fun patch(body: RequestBody): Builder = method("PATCH", body)

        fun delete(body: RequestBody? = null): Builder = method("DELETE", body)

        fun method(method: String, body: RequestBody?): Builder = apply {
            require(method.isNotEmpty()) { "method.isEmpty() == true" }
            this.method = method
            this.body = body
        }

        fun tag(tag: Any?): Builder = tag(Any::class.java, tag)

        fun <T> tag(type: JClass<in T>, tag: T?): Builder = apply {
            if (tag == null) tags.remove(type) else tags[type] = tag
        }

        fun build(): Request = Request(
            url = url ?: throw IllegalStateException("url == null"),
            method = method,
            headers = headers.build(),
            body = body,
            lazyCacheControl = null,
            tags = tags.toMap(),
        )
    }
}
