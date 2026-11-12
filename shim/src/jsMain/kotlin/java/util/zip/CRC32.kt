package java.util.zip

/** java.util.zip.CRC32 (IEEE 802.3 polynomial, table-driven). */
class CRC32 {
    private var crc = 0

    fun update(b: Int) {
        crc = crc.inv()
        crc = TABLE[(crc xor b) and 0xFF] xor (crc ushr 8)
        crc = crc.inv()
    }

    fun update(b: ByteArray) = update(b, 0, b.size)

    fun update(b: ByteArray, off: Int, len: Int) {
        var c = crc.inv()
        for (i in off until off + len) {
            c = TABLE[(c xor b[i].toInt()) and 0xFF] xor (c ushr 8)
        }
        crc = c.inv()
    }

    fun getValue(): Long = crc.toLong() and 0xFFFFFFFFL

    val value: Long get() = getValue()

    fun reset() {
        crc = 0
    }

    private companion object {
        val TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
            c
        }
    }
}

open class ZipException(message: String? = null) : java.io.IOException(message)
