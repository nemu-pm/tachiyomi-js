package java.nio.charset

/**
 * Charset shim for Kotlin/JS. Only UTF-8, Latin-1 and ASCII are real codecs; any other name
 * decodes as UTF-8 so sources that request e.g. windows-1251 still get text back.
 */
open class Charset(val name: String) {
    fun name(): String = name
    fun displayName(): String = name
    override fun toString() = name
    override fun equals(other: Any?): Boolean = other is Charset && other.name.equals(name, ignoreCase = true)
    override fun hashCode(): Int = name.uppercase().hashCode()

    companion object {
        fun forName(charsetName: String): Charset = when (charsetName.uppercase().replace("_", "-")) {
            "UTF-8", "UTF8" -> Charsets.UTF_8
            "ISO-8859-1", "ISO8859-1", "LATIN1", "LATIN-1" -> Charsets.ISO_8859_1
            "US-ASCII", "ASCII" -> Charsets.US_ASCII
            "UTF-16" -> Charsets.UTF_16
            else -> Charset(charsetName)
        }

        fun defaultCharset(): Charset = Charsets.UTF_8

        fun isSupported(charsetName: String): Boolean = charsetName.isNotEmpty()
    }
}

object Charsets {
    val UTF_8: Charset = Charset("UTF-8")
    val ISO_8859_1: Charset = Charset("ISO-8859-1")
    val US_ASCII: Charset = Charset("US-ASCII")
    val UTF_16: Charset = Charset("UTF-16")
}

object StandardCharsets {
    val UTF_8: Charset get() = Charsets.UTF_8
    val ISO_8859_1: Charset get() = Charsets.ISO_8859_1
    val US_ASCII: Charset get() = Charsets.US_ASCII
    val UTF_16: Charset get() = Charsets.UTF_16
}

fun ByteArray.toString(charset: Charset): String = when (charset.name.uppercase()) {
    "ISO-8859-1" -> {
        val sb = StringBuilder(size)
        for (b in this) sb.append((b.toInt() and 0xFF).toChar())
        sb.toString()
    }
    "US-ASCII" -> {
        val sb = StringBuilder(size)
        for (b in this) sb.append((b.toInt() and 0x7F).toChar())
        sb.toString()
    }
    else -> decodeToString()
}

// The JVM stdlib defaults to UTF-8; `password.toByteArray()` is everywhere in the corpus.
fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray = when (charset.name.uppercase()) {
    "ISO-8859-1" -> ByteArray(length) { this[it].code.toByte() }
    "US-ASCII" -> ByteArray(length) { (this[it].code and 0x7F).toByte() }
    else -> encodeToByteArray()
}
