package java.net

import java.nio.charset.Charset

/** Percent-decoding via `decodeURIComponent`; `+` is a space as in `application/x-www-form-urlencoded`. The charset is taken to be UTF-8. */
object URLDecoder {
    fun decode(s: String): String = decode(s, "UTF-8")

    fun decode(s: String, charset: String): String {
        val plus = s.replace('+', ' ')
        return try {
            js("decodeURIComponent(plus)") as String
        } catch (e: Throwable) {
            plus
        }
    }

    fun decode(s: String, charset: Charset): String = decode(s, "UTF-8")
}
