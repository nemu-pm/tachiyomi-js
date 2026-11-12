package okhttp3

import okhttp3.MediaType.Companion.toMediaType

abstract class RequestBody {
    abstract val contentType: MediaType?
    abstract val contentLength: Long
    abstract fun writeTo(): ByteArray

    @JsName("getContentType")
    fun contentType(): MediaType? = contentType

    @JsName("getContentLength")
    fun contentLength(): Long = contentLength

    /** Okio-style write for extension code that builds bodies by hand. */
    open fun writeTo(sink: okio.BufferedSink) {
        (sink as? okio.Buffer)?.write(writeTo())
    }

    companion object {
        val EMPTY: RequestBody = ByteArrayRequestBody(ByteArray(0), null)

        fun String.toRequestBody(contentType: MediaType? = null): RequestBody = ByteArrayRequestBody(encodeToByteArray(), contentType)

        fun ByteArray.toRequestBody(contentType: MediaType? = null, offset: Int = 0, byteCount: Int = size - offset): RequestBody =
            ByteArrayRequestBody(copyOfRange(offset, offset + byteCount), contentType)

        fun create(contentType: MediaType?, content: String): RequestBody = content.toRequestBody(contentType)
        fun create(contentType: MediaType?, content: ByteArray): RequestBody = content.toRequestBody(contentType)
    }
}

private class ByteArrayRequestBody(
    private val content: ByteArray,
    override val contentType: MediaType?,
) : RequestBody() {
    override val contentLength: Long = content.size.toLong()
    override fun writeTo(): ByteArray = content
}

class FormBody private constructor(
    private val encodedNames: List<String>,
    private val encodedValues: List<String>,
) : RequestBody() {
    override val contentType: MediaType = "application/x-www-form-urlencoded".toMediaType()
    override val contentLength: Long get() = writeTo().size.toLong()

    val size: Int get() = encodedNames.size
    fun encodedName(index: Int): String = encodedNames[index]
    fun encodedValue(index: Int): String = encodedValues[index]
    fun name(index: Int): String = HttpUrl.percentDecode(encodedNames[index], true)
    fun value(index: Int): String = HttpUrl.percentDecode(encodedValues[index], true)

    override fun writeTo(): ByteArray =
        encodedNames.indices.joinToString("&") { "${encodedNames[it]}=${encodedValues[it]}" }.encodeToByteArray()

    class Builder {
        private val names = mutableListOf<String>()
        private val values = mutableListOf<String>()

        fun add(name: String, value: String): Builder = apply {
            names.add(HttpUrl.canonicalize(name, HttpUrl.FORM_ENCODE_SET, plusIsSpace = true))
            values.add(HttpUrl.canonicalize(value, HttpUrl.FORM_ENCODE_SET, plusIsSpace = true))
        }

        fun addEncoded(name: String, value: String): Builder = apply {
            names.add(HttpUrl.canonicalize(name, HttpUrl.FORM_ENCODE_SET, alreadyEncoded = true, plusIsSpace = true))
            values.add(HttpUrl.canonicalize(value, HttpUrl.FORM_ENCODE_SET, alreadyEncoded = true, plusIsSpace = true))
        }

        fun build(): FormBody = FormBody(names.toList(), values.toList())
    }
}

class MultipartBody private constructor(
    private val boundary: String,
    val type: MediaType,
    val parts: List<Part>,
) : RequestBody() {
    override val contentType: MediaType = "${type}; boundary=$boundary".toMediaType()
    override val contentLength: Long get() = writeTo().size.toLong()

    fun boundary(): String = boundary

    override fun writeTo(): ByteArray {
        val out = okio.Buffer()
        for (part in parts) {
            out.writeUtf8("--$boundary\r\n")
            part.headers?.let { h -> for (i in 0 until h.size) out.writeUtf8("${h.name(i)}: ${h.value(i)}\r\n") }
            part.body.contentType?.let { out.writeUtf8("Content-Type: $it\r\n") }
            out.writeUtf8("\r\n")
            out.write(part.body.writeTo())
            out.writeUtf8("\r\n")
        }
        out.writeUtf8("--$boundary--\r\n")
        return out.readByteArray()
    }

    class Part private constructor(val headers: Headers?, val body: RequestBody) {
        companion object {
            fun create(body: RequestBody): Part = Part(null, body)
            fun create(headers: Headers?, body: RequestBody): Part = Part(headers, body)
            fun createFormData(name: String, value: String): Part = createFormData(name, null, value.toRequestBody())
            fun createFormData(name: String, filename: String?, body: RequestBody): Part {
                val disposition = buildString {
                    append("form-data; name=\"").append(name).append('"')
                    if (filename != null) append("; filename=\"").append(filename).append('"')
                }
                return Part(Headers.headersOf("Content-Disposition", disposition), body)
            }
        }
    }

    class Builder(private val boundary: String = generateBoundary()) {
        private var type: MediaType = MIXED
        private val parts = mutableListOf<Part>()

        fun setType(type: MediaType): Builder = apply { this.type = type }
        fun addPart(body: RequestBody): Builder = addPart(Part.create(body))
        fun addPart(headers: Headers?, body: RequestBody): Builder = addPart(Part.create(headers, body))
        fun addFormDataPart(name: String, value: String): Builder = addPart(Part.createFormData(name, value))
        fun addFormDataPart(name: String, filename: String?, body: RequestBody): Builder = addPart(Part.createFormData(name, filename, body))
        fun addPart(part: Part): Builder = apply { parts.add(part) }
        fun build(): MultipartBody {
            check(parts.isNotEmpty()) { "Multipart body must have at least one part." }
            return MultipartBody(boundary, type, parts.toList())
        }
    }

    companion object {
        val MIXED: MediaType = "multipart/mixed".toMediaType()
        val ALTERNATIVE: MediaType = "multipart/alternative".toMediaType()
        val DIGEST: MediaType = "multipart/digest".toMediaType()
        val PARALLEL: MediaType = "multipart/parallel".toMediaType()
        val FORM: MediaType = "multipart/form-data".toMediaType()

        private fun generateBoundary(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            return buildString { repeat(32) { append(chars[(kotlin.random.Random.nextDouble() * chars.length).toInt()]) } }
        }
    }
}
