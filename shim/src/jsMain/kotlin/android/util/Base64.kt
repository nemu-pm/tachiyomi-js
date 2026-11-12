package android.util

/**
 * android.util.Base64 in pure Kotlin. Bytes map 1:1 (the previous atob/btoa version re-encoded
 * the decoded "binary string" as UTF-8, corrupting anything above 0x7F).
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val WEB_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val decodeTable = IntArray(128) { -1 }.also { table ->
        for (i in STANDARD.indices) table[STANDARD[i].code] = i
        table['-'.code] = 62
        table['_'.code] = 63
    }

    fun decode(input: String, flags: Int): ByteArray = decode(input.encodeToByteArray(), flags)

    fun decode(input: ByteArray, flags: Int): ByteArray = decode(input, 0, input.size, flags)

    fun decode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        val out = ByteArray(len * 3 / 4 + 3)
        var outPos = 0
        var acc = 0
        var bits = 0
        for (i in offset until offset + len) {
            val c = input[i].toInt() and 0xFF
            if (c == '='.code) break
            if (c == '\n'.code || c == '\r'.code || c == ' '.code || c == '\t'.code) continue
            val v = if (c < 128) decodeTable[c] else -1
            if (v < 0) throw IllegalArgumentException("bad base-64")
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[outPos++] = ((acc shr bits) and 0xFF).toByte()
            }
        }
        return out.copyOf(outPos)
    }

    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).encodeToByteArray()

    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray =
        encodeToString(input, offset, len, flags).encodeToByteArray()

    fun encodeToString(input: ByteArray, flags: Int): String = encodeToString(input, 0, input.size, flags)

    fun encodeToString(input: ByteArray, offset: Int, len: Int, flags: Int): String {
        val alphabet = if (flags and URL_SAFE != 0) WEB_SAFE else STANDARD
        val pad = flags and NO_PADDING == 0
        val wrap = flags and NO_WRAP == 0
        val lineEnd = if (flags and CRLF != 0) "\r\n" else "\n"
        val sb = StringBuilder(len * 4 / 3 + 4)
        var lineLength = 0
        var i = offset
        val end = offset + len
        while (i < end) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < end) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < end) input[i + 2].toInt() and 0xFF else -1
            sb.append(alphabet[b0 shr 2])
            sb.append(alphabet[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) {
                sb.append(alphabet[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            } else if (pad) {
                sb.append('=')
            }
            if (b2 >= 0) {
                sb.append(alphabet[b2 and 0x3F])
            } else if (pad) {
                sb.append('=')
            }
            i += 3
            lineLength += 4
            if (wrap && lineLength >= 76 && i < end) {
                sb.append(lineEnd)
                lineLength = 0
            }
        }
        if (wrap && sb.isNotEmpty()) sb.append(lineEnd)
        return sb.toString()
    }
}
