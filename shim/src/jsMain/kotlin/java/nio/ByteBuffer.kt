package java.nio

class ByteOrder private constructor(private val name: String) {
    override fun toString(): String = name

    companion object {
        val BIG_ENDIAN = ByteOrder("BIG_ENDIAN")
        val LITTLE_ENDIAN = ByteOrder("LITTLE_ENDIAN")
        fun nativeOrder(): ByteOrder = LITTLE_ENDIAN
    }
}

class BufferUnderflowException : RuntimeException()
class BufferOverflowException : RuntimeException()

/**
 * Heap-only java.nio.ByteBuffer: wrap/allocate, absolute and relative get/put of the primitive
 * widths, and the position/limit cursor. The `int`/`short`/`long` properties stand in for the JVM's
 * synthetic getters (`buffer.int`), which Kotlin/JS does not derive from `getInt()`.
 */
class ByteBuffer private constructor(
    private val buf: ByteArray,
    private val offset: Int,
    private var lim: Int,
) {
    private var pos = 0
    private var byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    companion object {
        fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array, 0, array.size)
        fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer =
            ByteBuffer(array, offset, length).also { it.pos = 0 }
        fun allocate(capacity: Int): ByteBuffer = ByteBuffer(ByteArray(capacity), 0, capacity)
        fun allocateDirect(capacity: Int): ByteBuffer = allocate(capacity)
    }

    fun order(): ByteOrder = byteOrder
    fun order(order: ByteOrder): ByteBuffer = apply { byteOrder = order }

    fun array(): ByteArray = buf
    fun arrayOffset(): Int = offset
    fun hasArray(): Boolean = true
    fun capacity(): Int = buf.size - offset
    fun position(): Int = pos
    fun position(newPosition: Int): ByteBuffer = apply {
        require(newPosition in 0..lim) { "position $newPosition out of range 0..$lim" }
        pos = newPosition
    }
    fun limit(): Int = lim
    fun limit(newLimit: Int): ByteBuffer = apply {
        require(newLimit in 0..capacity()) { "limit $newLimit out of range" }
        lim = newLimit
        if (pos > lim) pos = lim
    }
    fun remaining(): Int = lim - pos
    fun hasRemaining(): Boolean = pos < lim
    fun rewind(): ByteBuffer = apply { pos = 0 }
    fun flip(): ByteBuffer = apply { lim = pos; pos = 0 }
    fun clear(): ByteBuffer = apply { pos = 0; lim = capacity() }
    fun slice(): ByteBuffer = ByteBuffer(buf, offset + pos, remaining()).order(byteOrder)
    fun duplicate(): ByteBuffer = ByteBuffer(buf, offset, lim).also { it.pos = pos; it.byteOrder = byteOrder }

    private fun take(n: Int): Int {
        if (lim - pos < n) throw BufferUnderflowException()
        return pos.also { pos += n }
    }

    private fun room(n: Int): Int {
        if (lim - pos < n) throw BufferOverflowException()
        return pos.also { pos += n }
    }

    private fun check(index: Int, n: Int) {
        if (index < 0 || index + n > lim) throw IndexOutOfBoundsException("index $index, width $n, limit $lim")
    }

    private fun readLong(index: Int, n: Int): Long {
        var v = 0L
        if (byteOrder === ByteOrder.BIG_ENDIAN) {
            for (i in 0 until n) v = (v shl 8) or (buf[offset + index + i].toLong() and 0xFF)
        } else {
            for (i in n - 1 downTo 0) v = (v shl 8) or (buf[offset + index + i].toLong() and 0xFF)
        }
        return v
    }

    private fun writeLong(index: Int, n: Int, value: Long) {
        var v = value
        if (byteOrder === ByteOrder.BIG_ENDIAN) {
            for (i in n - 1 downTo 0) { buf[offset + index + i] = v.toByte(); v = v shr 8 }
        } else {
            for (i in 0 until n) { buf[offset + index + i] = v.toByte(); v = v shr 8 }
        }
    }

    fun get(): Byte = buf[offset + take(1)]
    fun get(index: Int): Byte { check(index, 1); return buf[offset + index] }
    fun get(dst: ByteArray): ByteBuffer = get(dst, 0, dst.size)
    fun get(dst: ByteArray, dstOffset: Int, length: Int): ByteBuffer = apply {
        val start = take(length)
        buf.copyInto(dst, dstOffset, offset + start, offset + start + length)
    }

    fun put(b: Byte): ByteBuffer = apply { buf[offset + room(1)] = b }
    fun put(index: Int, b: Byte): ByteBuffer = apply { check(index, 1); buf[offset + index] = b }
    fun put(src: ByteArray): ByteBuffer = put(src, 0, src.size)
    fun put(src: ByteArray, srcOffset: Int, length: Int): ByteBuffer = apply {
        val start = room(length)
        src.copyInto(buf, offset + start, srcOffset, srcOffset + length)
    }
    fun put(src: ByteBuffer): ByteBuffer = apply {
        val n = src.remaining()
        val start = room(n)
        src.buf.copyInto(buf, offset + start, src.offset + src.pos, src.offset + src.pos + n)
        src.pos += n
    }

    fun getShort(): Short = readLong(take(2), 2).toShort()
    fun getShort(index: Int): Short { check(index, 2); return readLong(index, 2).toShort() }
    fun putShort(value: Short): ByteBuffer = apply { writeLong(room(2), 2, value.toLong()) }
    fun putShort(index: Int, value: Short): ByteBuffer = apply { check(index, 2); writeLong(index, 2, value.toLong()) }

    fun getChar(): Char = getShort().toInt().toChar()
    fun putChar(value: Char): ByteBuffer = putShort(value.code.toShort())

    fun getInt(): Int = readLong(take(4), 4).toInt()
    fun getInt(index: Int): Int { check(index, 4); return readLong(index, 4).toInt() }
    fun putInt(value: Int): ByteBuffer = apply { writeLong(room(4), 4, value.toLong()) }
    fun putInt(index: Int, value: Int): ByteBuffer = apply { check(index, 4); writeLong(index, 4, value.toLong()) }

    fun getLong(): Long = readLong(take(8), 8)
    fun getLong(index: Int): Long { check(index, 8); return readLong(index, 8) }
    fun putLong(value: Long): ByteBuffer = apply { writeLong(room(8), 8, value) }
    fun putLong(index: Int, value: Long): ByteBuffer = apply { check(index, 8); writeLong(index, 8, value) }

    fun getFloat(): Float = Float.fromBits(getInt())
    fun getFloat(index: Int): Float = Float.fromBits(getInt(index))
    fun putFloat(value: Float): ByteBuffer = putInt(value.toRawBits())
    fun getDouble(): Double = Double.fromBits(getLong())
    fun getDouble(index: Int): Double = Double.fromBits(getLong(index))
    fun putDouble(value: Double): ByteBuffer = putLong(value.toRawBits())

    val short: Short get() = getShort()
    val char: Char get() = getChar()
    val int: Int get() = getInt()
    val long: Long get() = getLong()
    val float: Float get() = getFloat()
    val double: Double get() = getDouble()

    override fun toString(): String = "java.nio.ByteBuffer[pos=$pos lim=$lim cap=${capacity()}]"
}
