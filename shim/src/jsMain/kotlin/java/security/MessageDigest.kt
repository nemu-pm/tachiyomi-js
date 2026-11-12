package java.security

/**
 * MessageDigest shim for MD5/SHA hashing.
 * Supports the streaming update/digest pattern needed for EVP_BytesToKey.
 */
abstract class MessageDigest protected constructor(val algorithm: String) {
    
    abstract val digestLength: Int
    
    abstract fun reset()
    abstract fun update(input: ByteArray)
    abstract fun update(input: ByteArray, offset: Int, len: Int)
    abstract fun digest(): ByteArray
    abstract fun digest(output: ByteArray, offset: Int, len: Int): Int
    
    fun digest(input: ByteArray): ByteArray {
        update(input)
        return digest()
    }

    fun getAlgorithm(): String = algorithm

    fun getDigestLength(): Int = digestLength

    companion object {
        fun getInstance(algorithm: String): MessageDigest {
            return when (algorithm.uppercase()) {
                "MD5" -> MD5Digest()
                "SHA-1", "SHA1", "SHA" -> SHA1Digest()
                "SHA-256", "SHA256" -> SHA256Digest()
                "SHA-512", "SHA512" -> SHA512Digest()
                else -> throw NoSuchAlgorithmException("Algorithm not supported: $algorithm")
            }
        }

        fun isEqual(a: ByteArray?, b: ByteArray?): Boolean = a != null && b != null && a.contentEquals(b)
    }
}

class NoSuchAlgorithmException(message: String) : Exception(message)

/**
 * Full MD5 implementation conforming to RFC 1321.
 * Required for CryptoAES EVP_BytesToKey derivation.
 */
private class MD5Digest : MessageDigest("MD5") {
    override val digestLength = 16
    
    private var state = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476)
    private var count = longArrayOf(0L)
    private var buffer = ByteArray(64)
    
    init { reset() }
    
    override fun reset() {
        state = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476)
        count[0] = 0L
        buffer = ByteArray(64)
    }
    
    override fun update(input: ByteArray) {
        update(input, 0, input.size)
    }
    
    override fun update(input: ByteArray, offset: Int, len: Int) {
        var index = ((count[0] shr 3) and 0x3F).toInt()
        count[0] += (len shl 3).toLong()
        
        val partLen = 64 - index
        var i = 0
        
        if (len >= partLen) {
            input.copyInto(buffer, index, offset, offset + partLen)
            transform(buffer, 0)
            i = partLen
            while (i + 63 < len) {
                transform(input, offset + i)
                i += 64
            }
            index = 0
        }
        
        input.copyInto(buffer, index, offset + i, offset + len)
    }
    
    override fun digest(): ByteArray {
        val bits = ByteArray(8)
        for (i in 0 until 8) {
            bits[i] = ((count[0] shr (i * 8)) and 0xFF).toByte()
        }
        
        val index = ((count[0] shr 3) and 0x3F).toInt()
        val padLen = if (index < 56) 56 - index else 120 - index
        
        val padding = ByteArray(padLen)
        padding[0] = 0x80.toByte()
        update(padding, 0, padLen)
        update(bits, 0, 8)
        
        val result = ByteArray(16)
        for (i in 0 until 4) {
            result[i * 4] = (state[i] and 0xFF).toByte()
            result[i * 4 + 1] = ((state[i] shr 8) and 0xFF).toByte()
            result[i * 4 + 2] = ((state[i] shr 16) and 0xFF).toByte()
            result[i * 4 + 3] = ((state[i] shr 24) and 0xFF).toByte()
        }
        
        reset()
        return result
    }
    
    override fun digest(output: ByteArray, offset: Int, len: Int): Int {
        val result = digest()
        val copyLen = minOf(len, result.size)
        result.copyInto(output, offset, 0, copyLen)
        return copyLen
    }
    
    private fun transform(block: ByteArray, offset: Int) {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        
        val x = IntArray(16)
        for (i in 0 until 16) {
            x[i] = (block[offset + i * 4].toInt() and 0xFF) or
                ((block[offset + i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((block[offset + i * 4 + 2].toInt() and 0xFF) shl 16) or
                ((block[offset + i * 4 + 3].toInt() and 0xFF) shl 24)
        }
        
        // Round 1
        a = ff(a, b, c, d, x[0], 7, 0xd76aa478.toInt())
        d = ff(d, a, b, c, x[1], 12, 0xe8c7b756.toInt())
        c = ff(c, d, a, b, x[2], 17, 0x242070db)
        b = ff(b, c, d, a, x[3], 22, 0xc1bdceee.toInt())
        a = ff(a, b, c, d, x[4], 7, 0xf57c0faf.toInt())
        d = ff(d, a, b, c, x[5], 12, 0x4787c62a)
        c = ff(c, d, a, b, x[6], 17, 0xa8304613.toInt())
        b = ff(b, c, d, a, x[7], 22, 0xfd469501.toInt())
        a = ff(a, b, c, d, x[8], 7, 0x698098d8)
        d = ff(d, a, b, c, x[9], 12, 0x8b44f7af.toInt())
        c = ff(c, d, a, b, x[10], 17, 0xffff5bb1.toInt())
        b = ff(b, c, d, a, x[11], 22, 0x895cd7be.toInt())
        a = ff(a, b, c, d, x[12], 7, 0x6b901122)
        d = ff(d, a, b, c, x[13], 12, 0xfd987193.toInt())
        c = ff(c, d, a, b, x[14], 17, 0xa679438e.toInt())
        b = ff(b, c, d, a, x[15], 22, 0x49b40821)
        
        // Round 2
        a = gg(a, b, c, d, x[1], 5, 0xf61e2562.toInt())
        d = gg(d, a, b, c, x[6], 9, 0xc040b340.toInt())
        c = gg(c, d, a, b, x[11], 14, 0x265e5a51)
        b = gg(b, c, d, a, x[0], 20, 0xe9b6c7aa.toInt())
        a = gg(a, b, c, d, x[5], 5, 0xd62f105d.toInt())
        d = gg(d, a, b, c, x[10], 9, 0x02441453)
        c = gg(c, d, a, b, x[15], 14, 0xd8a1e681.toInt())
        b = gg(b, c, d, a, x[4], 20, 0xe7d3fbc8.toInt())
        a = gg(a, b, c, d, x[9], 5, 0x21e1cde6)
        d = gg(d, a, b, c, x[14], 9, 0xc33707d6.toInt())
        c = gg(c, d, a, b, x[3], 14, 0xf4d50d87.toInt())
        b = gg(b, c, d, a, x[8], 20, 0x455a14ed)
        a = gg(a, b, c, d, x[13], 5, 0xa9e3e905.toInt())
        d = gg(d, a, b, c, x[2], 9, 0xfcefa3f8.toInt())
        c = gg(c, d, a, b, x[7], 14, 0x676f02d9)
        b = gg(b, c, d, a, x[12], 20, 0x8d2a4c8a.toInt())
        
        // Round 3
        a = hh(a, b, c, d, x[5], 4, 0xfffa3942.toInt())
        d = hh(d, a, b, c, x[8], 11, 0x8771f681.toInt())
        c = hh(c, d, a, b, x[11], 16, 0x6d9d6122)
        b = hh(b, c, d, a, x[14], 23, 0xfde5380c.toInt())
        a = hh(a, b, c, d, x[1], 4, 0xa4beea44.toInt())
        d = hh(d, a, b, c, x[4], 11, 0x4bdecfa9)
        c = hh(c, d, a, b, x[7], 16, 0xf6bb4b60.toInt())
        b = hh(b, c, d, a, x[10], 23, 0xbebfbc70.toInt())
        a = hh(a, b, c, d, x[13], 4, 0x289b7ec6)
        d = hh(d, a, b, c, x[0], 11, 0xeaa127fa.toInt())
        c = hh(c, d, a, b, x[3], 16, 0xd4ef3085.toInt())
        b = hh(b, c, d, a, x[6], 23, 0x04881d05)
        a = hh(a, b, c, d, x[9], 4, 0xd9d4d039.toInt())
        d = hh(d, a, b, c, x[12], 11, 0xe6db99e5.toInt())
        c = hh(c, d, a, b, x[15], 16, 0x1fa27cf8)
        b = hh(b, c, d, a, x[2], 23, 0xc4ac5665.toInt())
        
        // Round 4
        a = ii(a, b, c, d, x[0], 6, 0xf4292244.toInt())
        d = ii(d, a, b, c, x[7], 10, 0x432aff97)
        c = ii(c, d, a, b, x[14], 15, 0xab9423a7.toInt())
        b = ii(b, c, d, a, x[5], 21, 0xfc93a039.toInt())
        a = ii(a, b, c, d, x[12], 6, 0x655b59c3)
        d = ii(d, a, b, c, x[3], 10, 0x8f0ccc92.toInt())
        c = ii(c, d, a, b, x[10], 15, 0xffeff47d.toInt())
        b = ii(b, c, d, a, x[1], 21, 0x85845dd1.toInt())
        a = ii(a, b, c, d, x[8], 6, 0x6fa87e4f)
        d = ii(d, a, b, c, x[15], 10, 0xfe2ce6e0.toInt())
        c = ii(c, d, a, b, x[6], 15, 0xa3014314.toInt())
        b = ii(b, c, d, a, x[13], 21, 0x4e0811a1)
        a = ii(a, b, c, d, x[4], 6, 0xf7537e82.toInt())
        d = ii(d, a, b, c, x[11], 10, 0xbd3af235.toInt())
        c = ii(c, d, a, b, x[2], 15, 0x2ad7d2bb)
        b = ii(b, c, d, a, x[9], 21, 0xeb86d391.toInt())
        
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
    }
    
    private fun f(x: Int, y: Int, z: Int) = (x and y) or (x.inv() and z)
    private fun g(x: Int, y: Int, z: Int) = (x and z) or (y and z.inv())
    private fun h(x: Int, y: Int, z: Int) = x xor y xor z
    private fun i(x: Int, y: Int, z: Int) = y xor (x or z.inv())
    
    private fun rotateLeft(x: Int, n: Int) = (x shl n) or (x ushr (32 - n))
    
    private fun ff(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, ac: Int): Int {
        var aa = a + f(b, c, d) + x + ac
        aa = rotateLeft(aa, s)
        return aa + b
    }
    
    private fun gg(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, ac: Int): Int {
        var aa = a + g(b, c, d) + x + ac
        aa = rotateLeft(aa, s)
        return aa + b
    }
    
    private fun hh(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, ac: Int): Int {
        var aa = a + h(b, c, d) + x + ac
        aa = rotateLeft(aa, s)
        return aa + b
    }
    
    private fun ii(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, ac: Int): Int {
        var aa = a + i(b, c, d) + x + ac
        aa = rotateLeft(aa, s)
        return aa + b
    }
}

/**
 * SHA-256 implementation.
 */
private class SHA256Digest : MessageDigest("SHA-256") {
    override val digestLength = 32
    
    private var state = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19
    )
    private var count = 0L
    private var buffer = ByteArray(64)
    
    init { reset() }
    
    override fun reset() {
        state = intArrayOf(
            0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
            0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19
        )
        count = 0L
        buffer = ByteArray(64)
    }
    
    override fun update(input: ByteArray) {
        update(input, 0, input.size)
    }
    
    override fun update(input: ByteArray, offset: Int, len: Int) {
        var index = (count and 0x3F).toInt()
        count += len.toLong()
        
        val partLen = 64 - index
        var i = 0
        
        if (len >= partLen) {
            input.copyInto(buffer, index, offset, offset + partLen)
            transform(buffer, 0)
            i = partLen
            while (i + 63 < len) {
                transform(input, offset + i)
                i += 64
            }
            index = 0
        }
        
        input.copyInto(buffer, index, offset + i, offset + len)
    }
    
    override fun digest(): ByteArray {
        val bits = count * 8
        val index = (count and 0x3F).toInt()
        val padLen = if (index < 56) 56 - index else 120 - index
        
        val padding = ByteArray(padLen + 8)
        padding[0] = 0x80.toByte()
        for (i in 0 until 8) {
            padding[padLen + 7 - i] = ((bits shr (i * 8)) and 0xFF).toByte()
        }
        update(padding, 0, padding.size)
        
        val result = ByteArray(32)
        for (i in 0 until 8) {
            result[i * 4] = ((state[i] shr 24) and 0xFF).toByte()
            result[i * 4 + 1] = ((state[i] shr 16) and 0xFF).toByte()
            result[i * 4 + 2] = ((state[i] shr 8) and 0xFF).toByte()
            result[i * 4 + 3] = (state[i] and 0xFF).toByte()
        }
        
        reset()
        return result
    }
    
    override fun digest(output: ByteArray, offset: Int, len: Int): Int {
        val result = digest()
        val copyLen = minOf(len, result.size)
        result.copyInto(output, offset, 0, copyLen)
        return copyLen
    }
    
    private fun transform(block: ByteArray, offset: Int) {
        val w = IntArray(64)
        for (i in 0 until 16) {
            w[i] = ((block[offset + i * 4].toInt() and 0xFF) shl 24) or
                ((block[offset + i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((block[offset + i * 4 + 2].toInt() and 0xFF) shl 8) or
                (block[offset + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val s0 = rotateRight(w[i-15], 7) xor rotateRight(w[i-15], 18) xor (w[i-15] ushr 3)
            val s1 = rotateRight(w[i-2], 17) xor rotateRight(w[i-2], 19) xor (w[i-2] ushr 10)
            w[i] = w[i-16] + s0 + w[i-7] + s1
        }
        
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        
        for (i in 0 until 64) {
            val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[i] + w[i]
            val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            
            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }
        
        state[0] += a; state[1] += b; state[2] += c; state[3] += d
        state[4] += e; state[5] += f; state[6] += g; state[7] += h
    }
    
    private fun rotateRight(x: Int, n: Int) = (x ushr n) or (x shl (32 - n))
    
    companion object {
        private val K = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
        )
    }
}

/**
 * SHA-1 (FIPS 180-4), 64-byte blocks, big-endian length.
 */
private class SHA1Digest : MessageDigest("SHA-1") {
    override val digestLength = 20

    private var state = IntArray(5)
    private var count = 0L
    private var buffer = ByteArray(64)

    init { reset() }

    override fun reset() {
        state = intArrayOf(0x67452301, 0xEFCDAB89.toInt(), 0x98BADCFE.toInt(), 0x10325476, 0xC3D2E1F0.toInt())
        count = 0L
        buffer = ByteArray(64)
    }

    override fun update(input: ByteArray) = update(input, 0, input.size)

    override fun update(input: ByteArray, offset: Int, len: Int) {
        var index = (count and 0x3F).toInt()
        count += len.toLong()
        val partLen = 64 - index
        var i = 0
        if (len >= partLen) {
            input.copyInto(buffer, index, offset, offset + partLen)
            transform(buffer, 0)
            i = partLen
            while (i + 63 < len) {
                transform(input, offset + i)
                i += 64
            }
            index = 0
        }
        input.copyInto(buffer, index, offset + i, offset + len)
    }

    override fun digest(): ByteArray {
        val bits = count * 8
        val index = (count and 0x3F).toInt()
        val padLen = if (index < 56) 56 - index else 120 - index
        val padding = ByteArray(padLen + 8)
        padding[0] = 0x80.toByte()
        for (i in 0 until 8) padding[padLen + 7 - i] = ((bits shr (i * 8)) and 0xFF).toByte()
        update(padding, 0, padding.size)
        val result = ByteArray(20)
        for (i in 0 until 5) {
            result[i * 4] = ((state[i] shr 24) and 0xFF).toByte()
            result[i * 4 + 1] = ((state[i] shr 16) and 0xFF).toByte()
            result[i * 4 + 2] = ((state[i] shr 8) and 0xFF).toByte()
            result[i * 4 + 3] = (state[i] and 0xFF).toByte()
        }
        reset()
        return result
    }

    override fun digest(output: ByteArray, offset: Int, len: Int): Int {
        val result = digest()
        val copyLen = minOf(len, result.size)
        result.copyInto(output, offset, 0, copyLen)
        return copyLen
    }

    private fun transform(block: ByteArray, offset: Int) {
        val w = IntArray(80)
        for (i in 0 until 16) {
            w[i] = ((block[offset + i * 4].toInt() and 0xFF) shl 24) or
                ((block[offset + i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((block[offset + i * 4 + 2].toInt() and 0xFF) shl 8) or
                (block[offset + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 80) w[i] = rotateLeft(w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16], 1)
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]; var e = state[4]
        for (i in 0 until 80) {
            val (f, k) = when {
                i < 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                i < 40 -> (b xor c xor d) to 0x6ED9EBA1
                i < 60 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }
            val temp = rotateLeft(a, 5) + f + e + k + w[i]
            e = d; d = c; c = rotateLeft(b, 30); b = a; a = temp
        }
        state[0] += a; state[1] += b; state[2] += c; state[3] += d; state[4] += e
    }

    private fun rotateLeft(x: Int, n: Int) = (x shl n) or (x ushr (32 - n))
}

/**
 * SHA-512 (FIPS 180-4), 128-byte blocks. Uses emulated Longs under Kotlin/JS, so it is slow on
 * large inputs; sources only hash short strings with it.
 */
private class SHA512Digest : MessageDigest("SHA-512") {
    override val digestLength = 64

    private var state = LongArray(8)
    private var count = 0L
    private var buffer = ByteArray(128)

    init { reset() }

    override fun reset() {
        state = longArrayOf(
            0x6a09e667f3bcc908uL.toLong(), 0xbb67ae8584caa73buL.toLong(), 0x3c6ef372fe94f82buL.toLong(), 0xa54ff53a5f1d36f1uL.toLong(),
            0x510e527fade682d1uL.toLong(), 0x9b05688c2b3e6c1fuL.toLong(), 0x1f83d9abfb41bd6buL.toLong(), 0x5be0cd19137e2179uL.toLong(),
        )
        count = 0L
        buffer = ByteArray(128)
    }

    override fun update(input: ByteArray) = update(input, 0, input.size)

    override fun update(input: ByteArray, offset: Int, len: Int) {
        var index = (count and 0x7F).toInt()
        count += len.toLong()
        val partLen = 128 - index
        var i = 0
        if (len >= partLen) {
            input.copyInto(buffer, index, offset, offset + partLen)
            transform(buffer, 0)
            i = partLen
            while (i + 127 < len) {
                transform(input, offset + i)
                i += 128
            }
            index = 0
        }
        input.copyInto(buffer, index, offset + i, offset + len)
    }

    override fun digest(): ByteArray {
        val bits = count * 8
        val index = (count and 0x7F).toInt()
        val padLen = if (index < 112) 112 - index else 240 - index
        val padding = ByteArray(padLen + 16)
        padding[0] = 0x80.toByte()
        for (i in 0 until 8) padding[padLen + 15 - i] = ((bits shr (i * 8)) and 0xFF).toByte()
        update(padding, 0, padding.size)
        val result = ByteArray(64)
        for (i in 0 until 8) {
            for (j in 0 until 8) result[i * 8 + j] = ((state[i] shr (56 - j * 8)) and 0xFF).toByte()
        }
        reset()
        return result
    }

    override fun digest(output: ByteArray, offset: Int, len: Int): Int {
        val result = digest()
        val copyLen = minOf(len, result.size)
        result.copyInto(output, offset, 0, copyLen)
        return copyLen
    }

    private fun transform(block: ByteArray, offset: Int) {
        val w = LongArray(80)
        for (i in 0 until 16) {
            var v = 0L
            for (j in 0 until 8) v = (v shl 8) or (block[offset + i * 8 + j].toLong() and 0xFF)
            w[i] = v
        }
        for (i in 16 until 80) {
            val s0 = rotateRight(w[i - 15], 1) xor rotateRight(w[i - 15], 8) xor (w[i - 15] ushr 7)
            val s1 = rotateRight(w[i - 2], 19) xor rotateRight(w[i - 2], 61) xor (w[i - 2] ushr 6)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (i in 0 until 80) {
            val s1 = rotateRight(e, 14) xor rotateRight(e, 18) xor rotateRight(e, 41)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[i] + w[i]
            val s0 = rotateRight(a, 28) xor rotateRight(a, 34) xor rotateRight(a, 39)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }
        state[0] += a; state[1] += b; state[2] += c; state[3] += d
        state[4] += e; state[5] += f; state[6] += g; state[7] += h
    }

    private fun rotateRight(x: Long, n: Int) = (x ushr n) or (x shl (64 - n))

    companion object {
        private val K = longArrayOf(
            0x428a2f98d728ae22uL.toLong(), 0x7137449123ef65cduL.toLong(), 0xb5c0fbcfec4d3b2fuL.toLong(), 0xe9b5dba58189dbbcuL.toLong(),
            0x3956c25bf348b538uL.toLong(), 0x59f111f1b605d019uL.toLong(), 0x923f82a4af194f9buL.toLong(), 0xab1c5ed5da6d8118uL.toLong(),
            0xd807aa98a3030242uL.toLong(), 0x12835b0145706fbeuL.toLong(), 0x243185be4ee4b28cuL.toLong(), 0x550c7dc3d5ffb4e2uL.toLong(),
            0x72be5d74f27b896fuL.toLong(), 0x80deb1fe3b1696b1uL.toLong(), 0x9bdc06a725c71235uL.toLong(), 0xc19bf174cf692694uL.toLong(),
            0xe49b69c19ef14ad2uL.toLong(), 0xefbe4786384f25e3uL.toLong(), 0x0fc19dc68b8cd5b5uL.toLong(), 0x240ca1cc77ac9c65uL.toLong(),
            0x2de92c6f592b0275uL.toLong(), 0x4a7484aa6ea6e483uL.toLong(), 0x5cb0a9dcbd41fbd4uL.toLong(), 0x76f988da831153b5uL.toLong(),
            0x983e5152ee66dfabuL.toLong(), 0xa831c66d2db43210uL.toLong(), 0xb00327c898fb213fuL.toLong(), 0xbf597fc7beef0ee4uL.toLong(),
            0xc6e00bf33da88fc2uL.toLong(), 0xd5a79147930aa725uL.toLong(), 0x06ca6351e003826fuL.toLong(), 0x142929670a0e6e70uL.toLong(),
            0x27b70a8546d22ffcuL.toLong(), 0x2e1b21385c26c926uL.toLong(), 0x4d2c6dfc5ac42aeduL.toLong(), 0x53380d139d95b3dfuL.toLong(),
            0x650a73548baf63deuL.toLong(), 0x766a0abb3c77b2a8uL.toLong(), 0x81c2c92e47edaee6uL.toLong(), 0x92722c851482353buL.toLong(),
            0xa2bfe8a14cf10364uL.toLong(), 0xa81a664bbc423001uL.toLong(), 0xc24b8b70d0f89791uL.toLong(), 0xc76c51a30654be30uL.toLong(),
            0xd192e819d6ef5218uL.toLong(), 0xd69906245565a910uL.toLong(), 0xf40e35855771202auL.toLong(), 0x106aa07032bbd1b8uL.toLong(),
            0x19a4c116b8d2d0c8uL.toLong(), 0x1e376c085141ab53uL.toLong(), 0x2748774cdf8eeb99uL.toLong(), 0x34b0bcb5e19b48a8uL.toLong(),
            0x391c0cb3c5c95a63uL.toLong(), 0x4ed8aa4ae3418acbuL.toLong(), 0x5b9cca4f7763e373uL.toLong(), 0x682e6ff3d6b2b8a3uL.toLong(),
            0x748f82ee5defb2fcuL.toLong(), 0x78a5636f43172f60uL.toLong(), 0x84c87814a1f0ab72uL.toLong(), 0x8cc702081a6439ecuL.toLong(),
            0x90befffa23631e28uL.toLong(), 0xa4506cebde82bde9uL.toLong(), 0xbef9a3f7b2c67915uL.toLong(), 0xc67178f2e372532buL.toLong(),
            0xca273eceea26619cuL.toLong(), 0xd186b8c721c0c207uL.toLong(), 0xeada7dd6cde0eb1euL.toLong(), 0xf57d4f7fee6ed178uL.toLong(),
            0x06f067aa72176fbauL.toLong(), 0x0a637dc5a2c898a6uL.toLong(), 0x113f9804bef90daeuL.toLong(), 0x1b710b35131c471buL.toLong(),
            0x28db77f523047d84uL.toLong(), 0x32caab7b40c72493uL.toLong(), 0x3c9ebe0a15c9bebcuL.toLong(), 0x431d67c49c100d4cuL.toLong(),
            0x4cc5d4becb3e42b6uL.toLong(), 0x597f299cfc657e2auL.toLong(), 0x5fcb6fab3ad6faecuL.toLong(), 0x6c44198c4a475817uL.toLong(),
        )
    }
}
