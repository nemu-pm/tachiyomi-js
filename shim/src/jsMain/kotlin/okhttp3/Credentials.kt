package okhttp3

import java.nio.charset.Charset
import java.nio.charset.Charsets
import java.nio.charset.toByteArray

/** okhttp3.Credentials: the HTTP Basic `Authorization` header value. */
object Credentials {
    fun basic(username: String, password: String, charset: Charset = Charsets.ISO_8859_1): String =
        "Basic " + kotlin.io.encoding.Base64.encode("$username:$password".toByteArray(charset))
}
