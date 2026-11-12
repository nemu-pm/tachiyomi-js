package java.io

import java.nio.charset.Charset
import java.nio.charset.Charsets

abstract class InputStream : Closeable {
    abstract fun read(): Int

    open fun read(b: ByteArray): Int = read(b, 0, b.size)

    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var n = 0
        while (n < len) {
            val c = read()
            if (c == -1) break
            b[off + n] = c.toByte()
            n++
        }
        return if (n == 0) -1 else n
    }

    open fun skip(n: Long): Long {
        var skipped = 0L
        while (skipped < n && read() != -1) skipped++
        return skipped
    }

    open fun available(): Int = 0

    open fun markSupported(): Boolean = false

    override fun close() {}

    open fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun readAllBytes(): ByteArray = readBytes()

    fun reader(charset: Charset = Charsets.UTF_8): InputStreamReader = InputStreamReader(this, charset)

    fun bufferedReader(charset: Charset = Charsets.UTF_8): BufferedReader = BufferedReader(InputStreamReader(this, charset))
}

class ByteArrayInputStream(private val buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset) : InputStream() {
    private var pos = offset
    private val end = offset + length
    private var mark = offset

    override fun read(): Int = if (pos < end) buf[pos++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= end) return -1
        val n = minOf(len, end - pos)
        buf.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }

    override fun skip(n: Long): Long {
        val k = minOf(n, (end - pos).toLong()).coerceAtLeast(0L)
        pos += k.toInt()
        return k
    }

    override fun available(): Int = end - pos

    override fun markSupported(): Boolean = true

    fun mark(readLimit: Int) {
        mark = pos
    }

    fun reset() {
        pos = mark
    }

    override fun readBytes(): ByteArray {
        val rest = buf.copyOfRange(pos, end)
        pos = end
        return rest
    }
}
