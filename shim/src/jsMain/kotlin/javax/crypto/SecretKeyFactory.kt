package javax.crypto

import java.security.NoSuchAlgorithmException
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** PBKDF2 (RFC 8018 §5.2) over the shim's [Mac]; the only SecretKeyFactory algorithms the corpus asks for. */
class SecretKeyFactory private constructor(val algorithm: String, private val hmac: String) {
    fun getAlgorithm(): String = algorithm

    fun generateSecret(spec: PBEKeySpec): SecretKeySpec {
        val password = spec.password.concatToString().encodeToByteArray()
        val mac = Mac.getInstance(hmac).apply { init(SecretKeySpec(password, hmac)) }
        val hashLength = mac.macLength
        val keyBytes = (spec.keyLength + 7) / 8
        val blocks = (keyBytes + hashLength - 1) / hashLength
        val derived = ByteArray(blocks * hashLength)
        for (block in 1..blocks) {
            mac.update(spec.salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until spec.iterationCount) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            t.copyInto(derived, (block - 1) * hashLength)
        }
        return SecretKeySpec(derived.copyOf(keyBytes), algorithm)
    }

    companion object {
        fun getInstance(algorithm: String): SecretKeyFactory = when (algorithm.uppercase()) {
            "PBKDF2WITHHMACSHA1" -> SecretKeyFactory(algorithm, "HmacSHA1")
            "PBKDF2WITHHMACSHA256" -> SecretKeyFactory(algorithm, "HmacSHA256")
            "PBKDF2WITHHMACSHA512" -> SecretKeyFactory(algorithm, "HmacSHA512")
            else -> throw NoSuchAlgorithmException("Unsupported SecretKeyFactory algorithm: $algorithm")
        }
    }
}
