package okio

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.toByteArray
import java.nio.charset.toString
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import javax.crypto.Cipher

/*
 * Okio for the sandbox. Everything is in memory: a `Source` is read to exhaustion by `buffer()`,
 * sinks accumulate in a `Buffer`. Enough for the body-transform helpers sources use
 * (xor, rc4, AES, gzip) without segment lists or timeouts.
 */

typealias Closeable = java.io.Closeable
typealias IOException = java.io.IOException

inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this?.close()
    }
}

class Timeout {
    fun timeout(timeout: Long, unit: TimeUnit): Timeout = this
    fun timeoutNanos(): Long = 0L
    fun hasDeadline(): Boolean = false
    fun deadlineNanoTime(nanos: Long): Timeout = this
    fun clearTimeout(): Timeout = this
    fun clearDeadline(): Timeout = this
    fun throwIfReached() {}

    companion object {
        val NONE = Timeout()
    }
}

interface Source : Closeable {
    fun read(sink: Buffer, byteCount: Long): Long
    fun timeout(): Timeout = Timeout.NONE
}

interface Sink : Closeable {
    fun write(source: Buffer, byteCount: Long)
    fun flush()
    fun timeout(): Timeout = Timeout.NONE
}

interface BufferedSource : Source {
    val buffer: Buffer
    fun exhausted(): Boolean
    fun request(byteCount: Long): Boolean
    fun require(byteCount: Long)
    fun readByte(): Byte
    fun readShort(): Short
    fun readShortLe(): Short
    fun readInt(): Int
    fun readIntLe(): Int
    fun readLong(): Long
    fun readLongLe(): Long
    fun readByteArray(): ByteArray
    fun readByteArray(byteCount: Long): ByteArray
    fun readByteString(): ByteString
    fun readByteString(byteCount: Long): ByteString
    fun read(sink: ByteArray): Int
    fun read(sink: ByteArray, offset: Int, byteCount: Int): Int
    fun readFully(sink: ByteArray)
    fun readFully(sink: Buffer, byteCount: Long)
    fun readAll(sink: Sink): Long
    fun readUtf8(): String
    fun readUtf8(byteCount: Long): String
    fun readUtf8Line(): String?
    fun readUtf8LineStrict(): String
    fun readString(charset: Charset): String
    fun readString(byteCount: Long, charset: Charset): String
    fun skip(byteCount: Long)
    fun indexOf(b: Byte): Long
    fun indexOf(b: Byte, fromIndex: Long): Long
    fun rangeEquals(offset: Long, bytes: ByteString): Boolean
    fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean
    fun peek(): BufferedSource
    fun inputStream(): InputStream
}

interface BufferedSink : Sink {
    val buffer: Buffer
    fun write(source: ByteArray): BufferedSink
    fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink
    fun write(byteString: ByteString): BufferedSink
    fun write(source: Source, byteCount: Long): BufferedSink
    fun writeAll(source: Source): Long
    fun writeByte(b: Int): BufferedSink
    fun writeShort(s: Int): BufferedSink
    fun writeShortLe(s: Int): BufferedSink
    fun writeInt(i: Int): BufferedSink
    fun writeIntLe(i: Int): BufferedSink
    fun writeLong(v: Long): BufferedSink
    fun writeLongLe(v: Long): BufferedSink
    fun writeUtf8(string: String): BufferedSink
    fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink
    fun writeUtf8CodePoint(codePoint: Int): BufferedSink
    fun writeString(string: String, charset: Charset): BufferedSink
    fun emit(): BufferedSink
    fun emitCompleteSegments(): BufferedSink
    fun outputStream(): OutputStream
}

/** Immutable byte sequence with the okio helpers sources reach for. */
class ByteString internal constructor(internal val data: ByteArray) : Comparable<ByteString> {
    val size: Int get() = data.size

    operator fun get(index: Int): Byte = data[index]

    fun toByteArray(): ByteArray = data.copyOf()

    fun utf8(): String = data.decodeToString()

    fun hex(): String = data.joinToString("") { ((it.toInt() and 0xFF) or 0x100).toString(16).substring(1) }

    fun base64(): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

    fun base64Url(): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)

    fun md5(): ByteString = ByteString(java.security.MessageDigest.getInstance("MD5").digest(data))

    fun sha1(): ByteString = ByteString(java.security.MessageDigest.getInstance("SHA-1").digest(data))

    fun sha256(): ByteString = ByteString(java.security.MessageDigest.getInstance("SHA-256").digest(data))

    fun sha512(): ByteString = ByteString(java.security.MessageDigest.getInstance("SHA-512").digest(data))

    fun substring(beginIndex: Int = 0, endIndex: Int = size): ByteString = ByteString(data.copyOfRange(beginIndex, endIndex))

    fun startsWith(prefix: ByteString): Boolean = size >= prefix.size && data.copyOfRange(0, prefix.size).contentEquals(prefix.data)

    fun indexOf(other: ByteString, fromIndex: Int = 0): Int {
        outer@ for (i in fromIndex..size - other.size) {
            for (j in 0 until other.size) if (data[i + j] != other.data[j]) continue@outer
            return i
        }
        return -1
    }

    override fun compareTo(other: ByteString): Int {
        val n = minOf(size, other.size)
        for (i in 0 until n) {
            val a = data[i].toInt() and 0xFF
            val b = other.data[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return size - other.size
    }

    override fun equals(other: Any?): Boolean = other is ByteString && other.data.contentEquals(data)
    override fun hashCode(): Int = data.contentHashCode()
    override fun toString(): String = "[hex=${hex()}]"

    companion object {
        val EMPTY: ByteString = ByteString(ByteArray(0))

        fun of(vararg data: Byte): ByteString = ByteString(data.copyOf())

        fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size - offset): ByteString =
            ByteString(copyOfRange(offset, offset + byteCount))

        fun String.encodeUtf8(): ByteString = ByteString(encodeToByteArray())

        fun String.encode(charset: Charset): ByteString = ByteString(toByteArray(charset))

        fun String.decodeBase64(): ByteString? = try {
            ByteString(android.util.Base64.decode(this, android.util.Base64.DEFAULT))
        } catch (_: IllegalArgumentException) {
            null
        }

        fun String.decodeHex(): ByteString {
            require(length % 2 == 0) { "Unexpected hex string: $this" }
            return ByteString(ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() })
        }
    }
}

abstract class ForwardingSource(private val delegate: Source) : Source {
    fun delegate(): Source = delegate
    override fun read(sink: Buffer, byteCount: Long): Long = delegate.read(sink, byteCount)
    override fun timeout(): Timeout = delegate.timeout()
    override fun close() = delegate.close()
}

abstract class ForwardingSink(private val delegate: Sink) : Sink {
    fun delegate(): Sink = delegate
    override fun write(source: Buffer, byteCount: Long) = delegate.write(source, byteCount)
    override fun flush() = delegate.flush()
    override fun timeout(): Timeout = delegate.timeout()
    override fun close() = delegate.close()
}

/** Reads the wrapped source to exhaustion on the first read, then serves from a buffer. */
private class RealBufferedSource(private val source: Source) : BufferedSource {
    override val buffer: Buffer = Buffer()
    private var filled = false
    private var closed = false

    private fun fill(): Buffer {
        if (!filled) {
            filled = true
            buffer.writeAll(source)
        }
        return buffer
    }

    override fun read(sink: Buffer, byteCount: Long): Long = fill().read(sink, byteCount)
    override fun exhausted(): Boolean = fill().exhausted()
    override fun request(byteCount: Long): Boolean = fill().request(byteCount)
    override fun require(byteCount: Long) = fill().require(byteCount)
    override fun readByte(): Byte = fill().readByte()
    override fun readShort(): Short = fill().readShort()
    override fun readShortLe(): Short = fill().readShortLe()
    override fun readInt(): Int = fill().readInt()
    override fun readIntLe(): Int = fill().readIntLe()
    override fun readLong(): Long = fill().readLong()
    override fun readLongLe(): Long = fill().readLongLe()
    override fun readByteArray(): ByteArray = fill().readByteArray()
    override fun readByteArray(byteCount: Long): ByteArray = fill().readByteArray(byteCount)
    override fun readByteString(): ByteString = fill().readByteString()
    override fun readByteString(byteCount: Long): ByteString = fill().readByteString(byteCount)
    override fun read(sink: ByteArray): Int = fill().read(sink)
    override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int = fill().read(sink, offset, byteCount)
    override fun readFully(sink: ByteArray) = fill().readFully(sink)
    override fun readFully(sink: Buffer, byteCount: Long) = fill().readFully(sink, byteCount)
    override fun readAll(sink: Sink): Long = fill().readAll(sink)
    override fun readUtf8(): String = fill().readUtf8()
    override fun readUtf8(byteCount: Long): String = fill().readUtf8(byteCount)
    override fun readUtf8Line(): String? = fill().readUtf8Line()
    override fun readUtf8LineStrict(): String = fill().readUtf8LineStrict()
    override fun readString(charset: Charset): String = fill().readString(charset)
    override fun readString(byteCount: Long, charset: Charset): String = fill().readString(byteCount, charset)
    override fun skip(byteCount: Long) = fill().skip(byteCount)
    override fun indexOf(b: Byte): Long = fill().indexOf(b)
    override fun indexOf(b: Byte, fromIndex: Long): Long = fill().indexOf(b, fromIndex)
    override fun rangeEquals(offset: Long, bytes: ByteString): Boolean = fill().rangeEquals(offset, bytes)
    override fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean =
        fill().rangeEquals(offset, bytes, bytesOffset, byteCount)
    override fun peek(): BufferedSource = fill().peek()
    override fun inputStream(): InputStream = fill().inputStream()
    override fun timeout(): Timeout = source.timeout()

    override fun close() {
        if (closed) return
        closed = true
        source.close()
    }
}

private class RealBufferedSink(private val sink: Sink) : BufferedSink {
    override val buffer: Buffer = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        buffer.write(source, byteCount)
    }

    override fun write(source: ByteArray): BufferedSink = apply { buffer.write(source) }
    override fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink = apply { buffer.write(source, offset, byteCount) }
    override fun write(byteString: ByteString): BufferedSink = apply { buffer.write(byteString) }
    override fun write(source: Source, byteCount: Long): BufferedSink = apply { buffer.write(source, byteCount) }
    override fun writeAll(source: Source): Long = buffer.writeAll(source)
    override fun writeByte(b: Int): BufferedSink = apply { buffer.writeByte(b) }
    override fun writeShort(s: Int): BufferedSink = apply { buffer.writeShort(s) }
    override fun writeShortLe(s: Int): BufferedSink = apply { buffer.writeShortLe(s) }
    override fun writeInt(i: Int): BufferedSink = apply { buffer.writeInt(i) }
    override fun writeIntLe(i: Int): BufferedSink = apply { buffer.writeIntLe(i) }
    override fun writeLong(v: Long): BufferedSink = apply { buffer.writeLong(v) }
    override fun writeLongLe(v: Long): BufferedSink = apply { buffer.writeLongLe(v) }
    override fun writeUtf8(string: String): BufferedSink = apply { buffer.writeUtf8(string) }
    override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink = apply { buffer.writeUtf8(string, beginIndex, endIndex) }
    override fun writeUtf8CodePoint(codePoint: Int): BufferedSink = apply { buffer.writeUtf8CodePoint(codePoint) }
    override fun writeString(string: String, charset: Charset): BufferedSink = apply { buffer.writeString(string, charset) }

    override fun emit(): BufferedSink = apply { flush() }
    override fun emitCompleteSegments(): BufferedSink = this
    override fun outputStream(): OutputStream = buffer.outputStream()

    override fun flush() {
        if (buffer.size > 0) sink.write(buffer, buffer.size)
        sink.flush()
    }

    override fun close() {
        flush()
        sink.close()
    }
}

fun Source.buffer(): BufferedSource = this as? BufferedSource ?: RealBufferedSource(this)

fun Sink.buffer(): BufferedSink = this as? BufferedSink ?: RealBufferedSink(this)

fun InputStream.source(): Source = object : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        val buf = ByteArray(minOf(byteCount, 8192L).toInt())
        val n = this@source.read(buf, 0, buf.size)
        if (n == -1) return -1L
        sink.write(buf, 0, n)
        return n.toLong()
    }

    override fun close() = this@source.close()
}

fun OutputStream.sink(): Sink = object : Sink {
    override fun write(source: Buffer, byteCount: Long) {
        this@sink.write(source.readByteArray(byteCount))
    }

    override fun flush() = this@sink.flush()
    override fun close() = this@sink.close()
}

fun ByteArray.source(): Source = ByteArrayInputStream(this).source()

fun blackholeSink(): Sink = object : Sink {
    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
    override fun flush() {}
    override fun close() {}
}

/** Decrypts (or encrypts) the whole upstream with [cipher] on first read. */
class CipherSource(private val source: BufferedSource, val cipher: Cipher) : Source {
    private var result: Buffer? = null

    override fun read(sink: Buffer, byteCount: Long): Long {
        val out = result ?: Buffer().also {
            it.write(cipher.doFinal(source.readByteArray()))
            result = it
        }
        return out.read(sink, byteCount)
    }

    override fun close() = source.close()
}

fun Source.cipherSource(cipher: Cipher): CipherSource = CipherSource(buffer(), cipher)

class CipherSink(private val sink: BufferedSink, val cipher: Cipher) : Sink {
    private val pending = Buffer()

    override fun write(source: Buffer, byteCount: Long) = pending.write(source, byteCount)
    override fun flush() {}

    override fun close() {
        sink.write(cipher.doFinal(pending.readByteArray()))
        sink.close()
    }
}

fun Sink.cipherSink(cipher: Cipher): CipherSink = CipherSink(buffer(), cipher)

class GzipSource(private val source: Source) : Source {
    private var result: Buffer? = null

    override fun read(sink: Buffer, byteCount: Long): Long {
        val out = result ?: Buffer().also {
            val compressed = source.buffer().readByteArray()
            it.write(GZIPInputStream(ByteArrayInputStream(compressed)).readBytes())
            result = it
        }
        return out.read(sink, byteCount)
    }

    override fun close() = source.close()
}

fun Source.gzip(): GzipSource = GzipSource(this)

class InflaterSource(private val source: Source, private val inflater: Inflater) : Source {
    private var result: Buffer? = null

    override fun read(sink: Buffer, byteCount: Long): Long {
        val out = result ?: Buffer().also {
            inflater.setInput(source.buffer().readByteArray())
            val chunk = ByteArray(65536)
            while (!inflater.finished()) {
                val n = inflater.inflate(chunk)
                if (n == 0) break
                it.write(chunk, 0, n)
            }
            result = it
        }
        return out.read(sink, byteCount)
    }

    override fun close() = source.close()
}

// No default argument: keiyoushi.utils.Inflater declares `Source.inflate(nowrap: Boolean = false)`,
// and a defaulted parameter here would make a bare `inflate()` call ambiguous.
fun Source.inflate(inflater: Inflater): InflaterSource = InflaterSource(this, inflater)

/** The number of bytes this string takes when encoded as UTF-8. */
fun String.utf8Size(): Long = encodeToByteArray().size.toLong()

@Suppress("unused")
private val eofMarker = EOFException::class
