package okhttp3

class MediaType private constructor(
    private val mediaType: String,
    val type: String,
    val subtype: String,
    private val parameterNamesAndValues: List<String>,
) {
    fun parameter(name: String): String? {
        for (i in parameterNamesAndValues.indices step 2) {
            if (parameterNamesAndValues[i].equals(name, ignoreCase = true)) return parameterNamesAndValues[i + 1]
        }
        return null
    }

    fun charset(): java.nio.charset.Charset? = charset(null)

    fun charset(defaultValue: java.nio.charset.Charset?): java.nio.charset.Charset? =
        parameter("charset")?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: defaultValue

    override fun toString(): String = mediaType

    override fun equals(other: Any?): Boolean = other is MediaType && other.mediaType == mediaType
    override fun hashCode(): Int = mediaType.hashCode()

    companion object {
        fun String.toMediaType(): MediaType {
            val parts = split(';').map { it.trim() }
            val typeSubtype = parts[0].split('/')
            require(typeSubtype.size == 2 && typeSubtype[0].isNotEmpty() && typeSubtype[1].isNotEmpty()) { "No subtype found for: \"$this\"" }
            val params = mutableListOf<String>()
            for (i in 1 until parts.size) {
                val p = parts[i]
                if (p.isEmpty()) continue
                val eq = p.indexOf('=')
                if (eq == -1) continue
                params.add(p.substring(0, eq).trim())
                params.add(p.substring(eq + 1).trim().trim('"'))
            }
            return MediaType(this, typeSubtype[0].lowercase(), typeSubtype[1].lowercase(), params)
        }

        fun String.toMediaTypeOrNull(): MediaType? = try {
            toMediaType()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
