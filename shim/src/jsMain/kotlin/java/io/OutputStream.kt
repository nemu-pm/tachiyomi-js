package java.io

abstract class OutputStream : Closeable {
    abstract fun write(b: Int)

    open fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    open fun write(b: ByteArray, off: Int, len: Int) {
        for (i in off until off + len) {
            write(b[i].toInt() and 0xFF)
        }
    }

    open fun flush() {}
    override fun close() {}
}

class ByteArrayOutputStream(initialSize: Int = 32) : OutputStream() {
    private var buf = ByteArray(initialSize.coerceAtLeast(1))
    private var count = 0

    private fun ensure(extra: Int) {
        if (count + extra > buf.size) {
            buf = buf.copyOf(maxOf(buf.size * 2, count + extra))
        }
    }

    override fun write(b: Int) {
        ensure(1)
        buf[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }

    fun toByteArray(): ByteArray = buf.copyOf(count)

    fun size(): Int = count

    override fun toString(): String = toByteArray().decodeToString()

    fun reset() {
        count = 0
    }
}
