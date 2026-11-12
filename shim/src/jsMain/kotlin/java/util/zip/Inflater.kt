package java.util.zip

/**
 * java.util.zip.Inflater over the pure-Kotlin DEFLATE decoder in GZIPInputStream.kt.
 * The whole input is inflated on the first `inflate` call and handed out in slices.
 */
class Inflater(private val nowrap: Boolean = false) {
    private var input: ByteArray = ByteArray(0)
    private var output: ByteArray? = null
    private var outPos = 0

    fun setInput(b: ByteArray) = setInput(b, 0, b.size)

    fun setInput(b: ByteArray, off: Int, len: Int) {
        input = b.copyOfRange(off, off + len)
        output = null
        outPos = 0
    }

    fun needsInput(): Boolean = input.isEmpty() && output == null

    fun finished(): Boolean = output?.let { outPos >= it.size } ?: false

    fun getRemaining(): Int = 0

    fun inflate(b: ByteArray): Int = inflate(b, 0, b.size)

    fun inflate(b: ByteArray, off: Int, len: Int): Int {
        val out = output ?: run {
            val raw = if (nowrap) input else stripZlibHeader(input)
            val inflated = inflateRaw(raw) ?: throw DataFormatException("invalid deflate stream")
            output = inflated
            outPos = 0
            inflated
        }
        if (outPos >= out.size) return 0
        val n = minOf(len, out.size - outPos)
        out.copyInto(b, off, outPos, outPos + n)
        outPos += n
        return n
    }

    fun reset() {
        input = ByteArray(0)
        output = null
        outPos = 0
    }

    fun end() = reset()

    private fun stripZlibHeader(data: ByteArray): ByteArray {
        if (data.size < 2) return data
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        val isZlib = (cmf and 0x0F) == 8 && ((cmf shl 8) or flg) % 31 == 0
        return if (isZlib) data.copyOfRange(2, data.size) else data
    }
}

class DataFormatException(message: String? = null) : Exception(message)
