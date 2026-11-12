package java.lang

import kotlin.math.sign

object Integer {
    const val MAX_VALUE: Int = Int.MAX_VALUE
    const val MIN_VALUE: Int = Int.MIN_VALUE
    
    fun parseInt(s: String): Int = s.toInt()
    fun parseInt(s: String, radix: Int): Int = s.toInt(radix)
    fun toString(i: Int): String = i.toString()
    fun valueOf(s: String): Int = s.toInt()
    fun valueOf(i: Int): Int = i
    fun rotateLeft(i: Int, distance: Int): Int = i.rotateLeft(distance)
    fun rotateRight(i: Int, distance: Int): Int = i.rotateRight(distance)
    fun toHexString(i: Int): String = i.toUInt().toString(16)
    fun toBinaryString(i: Int): String = i.toUInt().toString(2)
    fun toOctalString(i: Int): String = i.toUInt().toString(8)

    fun compare(x: Int, y: Int): Int = x.compareTo(y)
    fun compareUnsigned(x: Int, y: Int): Int = x.toUInt().compareTo(y.toUInt())
    fun divideUnsigned(dividend: Int, divisor: Int): Int = (dividend.toUInt() / divisor.toUInt()).toInt()
    fun remainderUnsigned(dividend: Int, divisor: Int): Int = (dividend.toUInt() % divisor.toUInt()).toInt()
    fun toUnsignedLong(i: Int): Long = i.toUInt().toLong()
    fun toUnsignedString(i: Int): String = i.toUInt().toString()
    fun parseUnsignedInt(s: String): Int = s.toUInt().toInt()
    fun parseUnsignedInt(s: String, radix: Int): Int = s.toUInt(radix).toInt()

    fun bitCount(i: Int): Int = i.countOneBits()
    fun numberOfLeadingZeros(i: Int): Int = i.countLeadingZeroBits()
    fun numberOfTrailingZeros(i: Int): Int = i.countTrailingZeroBits()
    fun highestOneBit(i: Int): Int = i.takeHighestOneBit()
    fun lowestOneBit(i: Int): Int = i.takeLowestOneBit()
    fun reverse(i: Int): Int {
        var x = i
        x = (x and 0x55555555) shl 1 or (x ushr 1 and 0x55555555)
        x = (x and 0x33333333) shl 2 or (x ushr 2 and 0x33333333)
        x = (x and 0x0f0f0f0f) shl 4 or (x ushr 4 and 0x0f0f0f0f)
        return reverseBytes(x)
    }
    fun reverseBytes(i: Int): Int = (i shl 24) or (i and 0xff00 shl 8) or (i ushr 8 and 0xff00) or (i ushr 24)
    fun signum(i: Int): Int = i.sign
    fun hashCode(value: Int): Int = value
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun sum(a: Int, b: Int): Int = a + b
}

