package java.lang

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
}

