package okio

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.toByteArray
import java.nio.charset.toString

/** A growable byte array with a read cursor; both ends of the okio buffered interfaces. */
class Buffer : BufferedSource, BufferedSink {
    private var data = ByteArray(0)
    private var head = 0
    private var tail = 0

    val size: Long get() = (tail - head).toLong()

    override val buffer: Buffer get() = this

    override fun timeout(): Timeout = Timeout.NONE

    private val remaining: Int get() = tail - head

    private fun ensureWritable(count: Int) {
        if (tail + count <= data.size) return
        if (head > 0) {
            data.copyInto(data, 0, head, tail)
            tail -= head
            head = 0
            if (tail + count <= data.size) return
        }
        data = data.copyOf(maxOf(data.size * 2, tail + count, 64))
    }

    fun clear() {
        head = 0
        tail = 0
    }

    fun copy(): Buffer = Buffer().also { it.write(data, head, remaining) }

    fun snapshot(): ByteString = ByteString(data.copyOfRange(head, tail))

    fun snapshot(byteCount: Int): ByteString {
        require(byteCount.toLong())
        return ByteString(data.copyOfRange(head, head + byteCount))
    }

    fun getByte(pos: Long): Byte {
        require(pos in 0 until size) { "pos=$pos size=$size" }
        return data[head + pos.toInt()]
    }

    /** okio's `buffer[pos]` index operator. */
    operator fun get(pos: Long): Byte = getByte(pos)

    fun completeSegmentByteCount(): Long = size

    fun readFrom(input: InputStream): Buffer = write(input.readBytes())

    fun writeTo(out: OutputStream, byteCount: Long = size): Buffer {
        require(byteCount)
        out.write(data, head, byteCount.toInt())
        head += byteCount.toInt()
        return this
    }

    fun md5(): ByteString = snapshot().md5()
    fun sha1(): ByteString = snapshot().sha1()
    fun sha256(): ByteString = snapshot().sha256()

    // Sink

    override fun write(source: Buffer, byteCount: Long) {
        val n = byteCount.toInt()
        require(n <= source.remaining) { "size=${source.size} byteCount=$byteCount" }
        write(source.data, source.head, n)
        source.head += n
    }

    override fun flush() {}

    override fun close() {}

    // Source

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (remaining == 0) return -1L
        val n = minOf(byteCount, size).toInt()
        sink.write(data, head, n)
        head += n
        return n.toLong()
    }

    // BufferedSink

    override fun write(source: ByteArray): Buffer = write(source, 0, source.size)

    override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer {
        ensureWritable(byteCount)
        source.copyInto(data, tail, offset, offset + byteCount)
        tail += byteCount
        return this
    }

    override fun write(byteString: ByteString): Buffer = write(byteString.data, 0, byteString.size)

    override fun write(source: Source, byteCount: Long): Buffer {
        var left = byteCount
        while (left > 0) {
            val n = source.read(this, left)
            if (n == -1L) throw EOFException()
            left -= n
        }
        return this
    }

    override fun writeAll(source: Source): Long {
        var total = 0L
        while (true) {
            val n = source.read(this, 8192L)
            if (n == -1L) break
            total += n
        }
        return total
    }

    override fun writeByte(b: Int): Buffer {
        ensureWritable(1)
        data[tail++] = b.toByte()
        return this
    }

    override fun writeShort(s: Int): Buffer = writeByte(s ushr 8).writeByte(s)
    override fun writeShortLe(s: Int): Buffer = writeByte(s).writeByte(s ushr 8)
    override fun writeInt(i: Int): Buffer = writeByte(i ushr 24).writeByte(i ushr 16).writeByte(i ushr 8).writeByte(i)
    override fun writeIntLe(i: Int): Buffer = writeByte(i).writeByte(i ushr 8).writeByte(i ushr 16).writeByte(i ushr 24)
    override fun writeLong(v: Long): Buffer = writeInt((v ushr 32).toInt()).writeInt(v.toInt())
    override fun writeLongLe(v: Long): Buffer = writeIntLe(v.toInt()).writeIntLe((v ushr 32).toInt())

    override fun writeUtf8(string: String): Buffer = write(string.encodeToByteArray())

    override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer =
        writeUtf8(string.substring(beginIndex, endIndex))

    override fun writeUtf8CodePoint(codePoint: Int): Buffer {
        when {
            codePoint < 0x80 -> writeByte(codePoint)
            codePoint < 0x800 -> writeByte(0xC0 or (codePoint shr 6)).writeByte(0x80 or (codePoint and 0x3F))
            codePoint < 0x10000 -> writeByte(0xE0 or (codePoint shr 12)).writeByte(0x80 or ((codePoint shr 6) and 0x3F)).writeByte(0x80 or (codePoint and 0x3F))
            else -> writeByte(0xF0 or (codePoint shr 18)).writeByte(0x80 or ((codePoint shr 12) and 0x3F)).writeByte(0x80 or ((codePoint shr 6) and 0x3F)).writeByte(0x80 or (codePoint and 0x3F))
        }
        return this
    }

    override fun writeString(string: String, charset: Charset): Buffer = write(string.toByteArray(charset))

    override fun emit(): Buffer = this

    override fun emitCompleteSegments(): Buffer = this

    override fun outputStream(): OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            writeByte(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            this@Buffer.write(b, off, len)
        }
    }

    // BufferedSource

    override fun exhausted(): Boolean = remaining == 0

    override fun request(byteCount: Long): Boolean = size >= byteCount

    override fun require(byteCount: Long) {
        if (size < byteCount) throw EOFException("size=$size byteCount=$byteCount")
    }

    override fun readByte(): Byte {
        require(1)
        return data[head++]
    }

    override fun readShort(): Short {
        require(2)
        return (((data[head].toInt() and 0xFF) shl 8) or (data[head + 1].toInt() and 0xFF)).toShort().also { head += 2 }
    }

    override fun readShortLe(): Short {
        require(2)
        return ((data[head].toInt() and 0xFF) or ((data[head + 1].toInt() and 0xFF) shl 8)).toShort().also { head += 2 }
    }

    override fun readInt(): Int {
        require(4)
        val v = ((data[head].toInt() and 0xFF) shl 24) or ((data[head + 1].toInt() and 0xFF) shl 16) or
            ((data[head + 2].toInt() and 0xFF) shl 8) or (data[head + 3].toInt() and 0xFF)
        head += 4
        return v
    }

    override fun readIntLe(): Int {
        require(4)
        val v = (data[head].toInt() and 0xFF) or ((data[head + 1].toInt() and 0xFF) shl 8) or
            ((data[head + 2].toInt() and 0xFF) shl 16) or ((data[head + 3].toInt() and 0xFF) shl 24)
        head += 4
        return v
    }

    override fun readLong(): Long {
        val hi = readInt().toLong() and 0xFFFFFFFFL
        val lo = readInt().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    override fun readLongLe(): Long {
        val lo = readIntLe().toLong() and 0xFFFFFFFFL
        val hi = readIntLe().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    override fun readByteArray(): ByteArray {
        val result = data.copyOfRange(head, tail)
        head = tail
        return result
    }

    override fun readByteArray(byteCount: Long): ByteArray {
        require(byteCount)
        val n = byteCount.toInt()
        val result = data.copyOfRange(head, head + n)
        head += n
        return result
    }

    override fun readByteString(): ByteString = ByteString(readByteArray())

    override fun readByteString(byteCount: Long): ByteString = ByteString(readByteArray(byteCount))

    override fun read(sink: ByteArray): Int = read(sink, 0, sink.size)

    override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
        if (remaining == 0) return -1
        val n = minOf(byteCount, remaining)
        data.copyInto(sink, offset, head, head + n)
        head += n
        return n
    }

    override fun readFully(sink: ByteArray) {
        require(sink.size.toLong())
        read(sink, 0, sink.size)
    }

    override fun readFully(sink: Buffer, byteCount: Long) {
        require(byteCount)
        sink.write(this, byteCount)
    }

    override fun readAll(sink: Sink): Long {
        val n = size
        if (n > 0) sink.write(this, n)
        return n
    }

    override fun readUtf8(): String = readByteArray().decodeToString()

    override fun readUtf8(byteCount: Long): String = readByteArray(byteCount).decodeToString()

    override fun readUtf8Line(): String? {
        if (remaining == 0) return null
        val newline = indexOf('\n'.code.toByte())
        return if (newline == -1L) readUtf8() else readLineAt(newline)
    }

    override fun readUtf8LineStrict(): String {
        val newline = indexOf('\n'.code.toByte())
        if (newline == -1L) throw EOFException("\\n not found: size=$size")
        return readLineAt(newline)
    }

    private fun readLineAt(newline: Long): String {
        val line = readUtf8(newline)
        skip(1)
        return line.removeSuffix("\r")
    }

    override fun readString(charset: Charset): String = readByteArray().toString(charset)

    override fun readString(byteCount: Long, charset: Charset): String = readByteArray(byteCount).toString(charset)

    override fun skip(byteCount: Long) {
        require(byteCount)
        head += byteCount.toInt()
    }

    override fun indexOf(b: Byte): Long = indexOf(b, 0L)

    override fun indexOf(b: Byte, fromIndex: Long): Long {
        for (i in head + fromIndex.toInt() until tail) {
            if (data[i] == b) return (i - head).toLong()
        }
        return -1L
    }

    override fun rangeEquals(offset: Long, bytes: ByteString): Boolean = rangeEquals(offset, bytes, 0, bytes.size)

    override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean {
        if (offset < 0 || bytesOffset < 0 || byteCount < 0 || offset + byteCount > size || bytesOffset + byteCount > bytes.size) return false
        val start = head + offset.toInt()
        for (i in 0 until byteCount) {
            if (data[start + i] != bytes[bytesOffset + i]) return false
        }
        return true
    }

    override fun peek(): BufferedSource = copy()

    override fun inputStream(): InputStream = object : InputStream() {
        override fun read(): Int = if (exhausted()) -1 else readByte().toInt() and 0xFF
        override fun read(b: ByteArray, off: Int, len: Int): Int = this@Buffer.read(b, off, len)
        override fun available(): Int = remaining
    }

    override fun toString(): String = "[size=$size]"
}
