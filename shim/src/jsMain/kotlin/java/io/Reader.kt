package java.io

import java.nio.charset.Charset
import java.nio.charset.Charsets
import java.nio.charset.toString

abstract class Reader : Closeable {
    abstract fun read(cbuf: CharArray, off: Int, len: Int): Int

    open fun read(): Int {
        val buf = CharArray(1)
        return if (read(buf, 0, 1) == -1) -1 else buf[0].code
    }

    open fun read(cbuf: CharArray): Int = read(cbuf, 0, cbuf.size)

    open fun readText(): String {
        val sb = StringBuilder()
        val buf = CharArray(4096)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n == -1) break
            sb.appendRange(buf, 0, n)
        }
        return sb.toString()
    }

    open fun ready(): Boolean = true

    override fun close() {}
}

/** Reads over an in-memory string; the base for every reader here since streams are fully buffered. */
open class StringReader(private val str: String) : Reader() {
    private var pos = 0

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (pos >= str.length) return -1
        val n = minOf(len, str.length - pos)
        for (i in 0 until n) cbuf[off + i] = str[pos + i]
        pos += n
        return n
    }

    override fun read(): Int = if (pos >= str.length) -1 else str[pos++].code

    override fun readText(): String {
        val rest = str.substring(pos)
        pos = str.length
        return rest
    }

    fun readLine(): String? {
        if (pos >= str.length) return null
        val nl = str.indexOf('\n', pos)
        val line = if (nl == -1) str.substring(pos) else str.substring(pos, nl)
        pos = if (nl == -1) str.length else nl + 1
        return line.removeSuffix("\r")
    }
}

class InputStreamReader(input: InputStream, charset: Charset = Charsets.UTF_8) :
    StringReader(input.readBytes().toString(charset)) {
    constructor(input: InputStream, charsetName: String) : this(input, Charset.forName(charsetName))
}

class BufferedReader(reader: Reader, @Suppress("UNUSED_PARAMETER") size: Int = 8192) : StringReader(reader.readText()) {
    fun lines(): List<String> = generateSequence { readLine() }.toList()
}
