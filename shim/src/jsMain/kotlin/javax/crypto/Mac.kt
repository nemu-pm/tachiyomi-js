package javax.crypto

import java.security.Key
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/** HMAC over the pure-Kotlin [MessageDigest] implementations. */
class Mac private constructor(val algorithm: String, private val digestName: String, private val blockSize: Int) {
    private var key: ByteArray? = null
    private val buffer = java.io.ByteArrayOutputStream()

    fun getAlgorithm(): String = algorithm

    val macLength: Int get() = MessageDigest.getInstance(digestName).digestLength

    fun getMacLength(): Int = macLength

    fun init(key: Key) {
        this.key = key.encoded
        buffer.reset()
    }

    fun update(input: ByteArray) {
        buffer.write(input, 0, input.size)
    }

    fun update(input: ByteArray, offset: Int, len: Int) {
        buffer.write(input, offset, len)
    }

    fun update(input: Byte) {
        buffer.write(input.toInt())
    }

    fun reset() {
        buffer.reset()
    }

    fun doFinal(): ByteArray {
        val k = key ?: throw IllegalStateException("MAC not initialized")
        val message = buffer.toByteArray()
        buffer.reset()
        return hmac(k, message)
    }

    fun doFinal(input: ByteArray): ByteArray {
        update(input)
        return doFinal()
    }

    private fun hmac(rawKey: ByteArray, message: ByteArray): ByteArray {
        val key = if (rawKey.size > blockSize) MessageDigest.getInstance(digestName).digest(rawKey) else rawKey
        val padded = key.copyOf(blockSize)
        val inner = ByteArray(blockSize) { (padded[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(blockSize) { (padded[it].toInt() xor 0x5c).toByte() }
        val innerDigest = MessageDigest.getInstance(digestName).apply {
            update(inner)
            update(message)
        }.digest()
        return MessageDigest.getInstance(digestName).apply {
            update(outer)
            update(innerDigest)
        }.digest()
    }

    companion object {
        fun getInstance(algorithm: String): Mac = when (algorithm.uppercase().replace("-", "")) {
            "HMACMD5" -> Mac(algorithm, "MD5", 64)
            "HMACSHA1" -> Mac(algorithm, "SHA-1", 64)
            "HMACSHA256" -> Mac(algorithm, "SHA-256", 64)
            "HMACSHA512" -> Mac(algorithm, "SHA-512", 128)
            else -> throw NoSuchAlgorithmException("Unsupported MAC algorithm: $algorithm")
        }
    }
}
