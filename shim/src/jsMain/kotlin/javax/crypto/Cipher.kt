package javax.crypto

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.Key
import java.security.PublicKey
import java.security.PrivateKey
import java.security.RSAPublicKeyImpl
import java.security.RSAPrivateKeyImpl
import java.security.spec.AlgorithmParameterSpec
import java.math.BigInteger

/**
 * Cipher implementation for AES and RSA encryption/decryption.
 * Supports:
 * - AES/{CBC,ECB,CTR,GCM}/{PKCS5Padding,PKCS7Padding,NoPadding}
 * - RSA/ECB/PKCS1Padding
 *
 * `update` buffers and returns nothing; everything is produced by `doFinal`, which is
 * equivalent for callers that concatenate the two.
 */
class Cipher private constructor(private val transformation: String) {

    private var mode: Int = 0
    private var aesKey: ByteArray = ByteArray(0)
    private var iv: ByteArray = ByteArray(0)
    private var tagBits: Int = 128
    private var aad: ByteArray = ByteArray(0)
    private val pending = java.io.ByteArrayOutputStream()
    private var rsaPublicKey: RSAPublicKeyImpl? = null
    private var rsaPrivateKey: RSAPrivateKeyImpl? = null
    private val isRsa: Boolean = transformation.uppercase().contains("RSA")
    private val parts = transformation.uppercase().split('/')
    private val blockMode: String = parts.getOrNull(1) ?: "ECB"
    private val padding: Boolean = (parts.getOrNull(2) ?: "PKCS5PADDING") != "NOPADDING"

    fun getAlgorithm(): String = transformation
    fun getBlockSize(): Int = if (isRsa) 0 else 16
    fun getIV(): ByteArray = iv.copyOf()

    fun init(opmode: Int, key: Key) {
        this.mode = opmode
        pending.reset()
        aad = ByteArray(0)
        when {
            key is RSAPublicKeyImpl -> rsaPublicKey = key
            key is RSAPrivateKeyImpl -> rsaPrivateKey = key
            key is PublicKey && isRsa -> rsaPublicKey = key as? RSAPublicKeyImpl
            key is PrivateKey && isRsa -> rsaPrivateKey = key as? RSAPrivateKeyImpl
            key is SecretKeySpec -> {
                this.aesKey = key.encoded
                this.iv = ByteArray(16) // Default IV
            }
            else -> throw IllegalArgumentException("Unsupported key type: ${key::class.simpleName}")
        }
    }

    fun init(opmode: Int, key: Key, params: AlgorithmParameterSpec?) {
        init(opmode, key)
        when (params) {
            is IvParameterSpec -> this.iv = params.iv
            is GCMParameterSpec -> {
                this.iv = params.iv
                this.tagBits = params.getTLen()
            }
            else -> {}
        }
    }

    fun updateAAD(src: ByteArray) {
        aad += src
    }

    fun update(input: ByteArray): ByteArray {
        pending.write(input, 0, input.size)
        return ByteArray(0)
    }

    fun update(input: ByteArray, offset: Int, len: Int): ByteArray {
        pending.write(input, offset, len)
        return ByteArray(0)
    }

    fun doFinal(): ByteArray = doFinal(ByteArray(0))

    fun doFinal(input: ByteArray, offset: Int, len: Int): ByteArray = doFinal(input.copyOfRange(offset, offset + len))

    fun doFinal(input: ByteArray): ByteArray {
        val data = if (pending.size() == 0) {
            input
        } else {
            pending.write(input, 0, input.size)
            pending.toByteArray().also { pending.reset() }
        }
        if (isRsa) {
            return when (mode) {
                ENCRYPT_MODE -> rsaEncrypt(data)
                DECRYPT_MODE -> rsaDecrypt(data)
                else -> throw IllegalStateException("Invalid mode: $mode")
            }
        }
        val encrypt = when (mode) {
            ENCRYPT_MODE -> true
            DECRYPT_MODE -> false
            else -> throw IllegalStateException("Invalid mode: $mode")
        }
        return when (blockMode) {
            "CBC" -> if (encrypt) encryptAesCbc(data, aesKey, iv, padding) else decryptAesCbc(data, aesKey, iv, padding)
            "ECB" -> aesEcb(data, aesKey, encrypt, padding)
            "CTR" -> aesCtr(data, aesKey, iv)
            "GCM" -> aesGcm(data, aesKey, iv, aad, tagBits, encrypt)
            else -> throw java.security.NoSuchAlgorithmException("Unsupported transformation: $transformation")
        }
    }
    
    private fun rsaEncrypt(input: ByteArray): ByteArray {
        val key = rsaPublicKey ?: throw IllegalStateException("No public key set for RSA encryption")
        val n = key.modulus
        val e = key.publicExponent
        val keyLen = (n.bitLength() + 7) / 8
        
        // PKCS#1 v1.5 padding
        val padded = pkcs1Pad(input, keyLen)
        
        // Convert to BigInteger and encrypt: c = m^e mod n
        val m = BigInteger.fromPositiveBytes(padded)
        val c = m.modPow(e, n)
        
        // Convert result to byte array with correct length
        return bigIntToBytes(c, keyLen)
    }
    
    private fun rsaDecrypt(input: ByteArray): ByteArray {
        val key = rsaPrivateKey ?: throw IllegalStateException("No private key set for RSA decryption")
        val n = key.modulus
        val d = key.privateExponent
        val keyLen = (n.bitLength() + 7) / 8
        
        // Convert to BigInteger and decrypt: m = c^d mod n
        val c = BigInteger.fromPositiveBytes(input)
        val m = c.modPow(d, n)
        
        // Convert result to byte array and unpad
        val padded = bigIntToBytes(m, keyLen)
        return pkcs1Unpad(padded)
    }
    
    companion object {
        const val ENCRYPT_MODE = 1
        const val DECRYPT_MODE = 2
        
        fun getInstance(transformation: String): Cipher {
            val normalizedTransformation = transformation.uppercase()
            if (!normalizedTransformation.contains("AES") && !normalizedTransformation.contains("RSA")) {
                throw java.security.NoSuchAlgorithmException("Unsupported transformation: $transformation")
            }
            return Cipher(transformation)
        }
    }
}

/**
 * PKCS#1 v1.5 padding for encryption (type 2).
 * Structure: 0x00 | 0x02 | PS | 0x00 | M
 * PS = random non-zero padding bytes (at least 8 bytes)
 */
private fun pkcs1Pad(data: ByteArray, keyLen: Int): ByteArray {
    val maxDataLen = keyLen - 11
    if (data.size > maxDataLen) {
        throw IllegalArgumentException("Data too long for RSA key size: ${data.size} > $maxDataLen")
    }
    
    val padLen = keyLen - data.size - 3
    val result = ByteArray(keyLen)
    
    result[0] = 0x00
    result[1] = 0x02 // Block type 2 (encryption)
    
    // Random non-zero padding
    for (i in 2 until 2 + padLen) {
        var b: Int
        do {
            b = kotlin.random.Random.nextInt(1, 256)
        } while (b == 0)
        result[i] = b.toByte()
    }
    
    result[2 + padLen] = 0x00
    data.copyInto(result, 3 + padLen)
    
    return result
}

/**
 * Remove PKCS#1 v1.5 padding.
 */
private fun pkcs1Unpad(data: ByteArray): ByteArray {
    if (data.size < 11) throw IllegalArgumentException("Invalid PKCS#1 padding")
    if (data[0] != 0x00.toByte()) throw IllegalArgumentException("Invalid PKCS#1 padding: first byte not 0x00")
    if (data[1] != 0x02.toByte() && data[1] != 0x01.toByte()) {
        throw IllegalArgumentException("Invalid PKCS#1 padding: invalid block type")
    }
    
    // Find 0x00 separator after padding
    var separatorPos = -1
    for (i in 2 until data.size) {
        if (data[i] == 0x00.toByte()) {
            separatorPos = i
            break
        }
    }
    
    if (separatorPos < 10) {
        throw IllegalArgumentException("Invalid PKCS#1 padding: separator too early")
    }
    
    return data.copyOfRange(separatorPos + 1, data.size)
}

/**
 * Convert BigInteger to byte array with fixed length.
 */
private fun bigIntToBytes(value: BigInteger, length: Int): ByteArray {
    val bytes = value.toByteArray()
    return when {
        bytes.size == length -> bytes
        bytes.size > length -> {
            // Remove leading zeros (sign byte)
            if (bytes[0] == 0x00.toByte()) {
                bytes.copyOfRange(bytes.size - length, bytes.size)
            } else {
                bytes.copyOfRange(bytes.size - length, bytes.size)
            }
        }
        else -> {
            // Pad with leading zeros
            val result = ByteArray(length)
            bytes.copyInto(result, length - bytes.size)
            result
        }
    }
}

// Pure Kotlin AES implementation
private fun encryptAesCbc(plaintext: ByteArray, key: ByteArray, iv: ByteArray, padding: Boolean = true): ByteArray {
    val padded = if (padding) pkcs7Pad(plaintext, 16) else plaintext
    if (padded.size % 16 != 0) throw IllegalArgumentException("Input length not multiple of 16 bytes")
    val expandedKey = expandKey(key)
    val result = ByteArray(padded.size)
    var previousBlock = iv.copyOf()

    for (blockIndex in padded.indices step 16) {
        val block = padded.copyOfRange(blockIndex, blockIndex + 16)
        // XOR with previous ciphertext block (or IV)
        for (i in 0 until 16) {
            block[i] = (block[i].toInt() xor previousBlock[i].toInt()).toByte()
        }
        val encrypted = aesEncryptBlock(block, expandedKey)
        encrypted.copyInto(result, blockIndex)
        previousBlock = encrypted
    }
    
    return result
}

private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray, padding: Boolean = true): ByteArray {
    if (ciphertext.isEmpty()) return ciphertext
    if (ciphertext.size % 16 != 0) {
        throw IllegalArgumentException("Invalid ciphertext length")
    }
    
    val expandedKey = expandKey(key)
    val result = ByteArray(ciphertext.size)
    var previousBlock = iv.copyOf()
    
    for (blockIndex in ciphertext.indices step 16) {
        val block = ciphertext.copyOfRange(blockIndex, blockIndex + 16)
        val decrypted = aesDecryptBlock(block, expandedKey)
        // XOR with previous ciphertext block (or IV)
        for (i in 0 until 16) {
            result[blockIndex + i] = (decrypted[i].toInt() xor previousBlock[i].toInt()).toByte()
        }
        previousBlock = block
    }

    return if (padding) pkcs7Unpad(result) else result
}

private fun aesEcb(input: ByteArray, key: ByteArray, encrypt: Boolean, padding: Boolean): ByteArray {
    val expandedKey = expandKey(key)
    val data = if (encrypt && padding) pkcs7Pad(input, 16) else input
    if (data.size % 16 != 0) throw IllegalArgumentException("Input length not multiple of 16 bytes")
    val result = ByteArray(data.size)
    for (blockIndex in data.indices step 16) {
        val block = data.copyOfRange(blockIndex, blockIndex + 16)
        val out = if (encrypt) aesEncryptBlock(block, expandedKey) else aesDecryptBlock(block, expandedKey)
        out.copyInto(result, blockIndex)
    }
    return if (!encrypt && padding) pkcs7Unpad(result) else result
}

/** Counter mode: the whole 128-bit counter increments big-endian (what the JDK does). */
private fun aesCtr(input: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val expandedKey = expandKey(key)
    val counter = iv.copyOf(16)
    val result = ByteArray(input.size)
    var pos = 0
    while (pos < input.size) {
        val keystream = aesEncryptBlock(counter, expandedKey)
        val n = minOf(16, input.size - pos)
        for (i in 0 until n) result[pos + i] = (input[pos + i].toInt() xor keystream[i].toInt()).toByte()
        pos += n
        incrementCounter(counter, 0)
    }
    return result
}

/** Increments the big-endian counter in [block] from the last byte back to index [stop]. */
private fun incrementCounter(block: ByteArray, stop: Int) {
    var i = block.size - 1
    while (i >= stop) {
        val v = (block[i].toInt() and 0xFF) + 1
        block[i] = v.toByte()
        if (v <= 0xFF) return
        i--
    }
}

/** AES-GCM (NIST SP 800-38D); the tag is appended to the ciphertext as the JDK does. */
private fun aesGcm(input: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray, tagBits: Int, encrypt: Boolean): ByteArray {
    val expandedKey = expandKey(key)
    val h = bytesToWords(aesEncryptBlock(ByteArray(16), expandedKey))
    val j0 = if (iv.size == 12) {
        iv.copyOf(16).also { it[15] = 1 }
    } else {
        val padded = iv.copyOf((iv.size + 15) / 16 * 16 + 16)
        val bits = iv.size.toLong() * 8
        for (i in 0 until 8) padded[padded.size - 1 - i] = ((bits shr (i * 8)) and 0xFF).toByte()
        wordsToBytes(ghash(h, padded))
    }
    val tagLen = tagBits / 8
    val plainOrCipher: ByteArray
    val cipherText: ByteArray
    if (encrypt) {
        plainOrCipher = input
        cipherText = gctr(expandedKey, j0, input)
    } else {
        if (input.size < tagLen) throw javax.crypto.AEADBadTagException("Input too short")
        cipherText = input.copyOfRange(0, input.size - tagLen)
        plainOrCipher = gctr(expandedKey, j0, cipherText)
    }
    val aadPadLen = (aad.size + 15) / 16 * 16
    val cPadLen = (cipherText.size + 15) / 16 * 16
    val ghashInput = ByteArray(aadPadLen + cPadLen + 16)
    aad.copyInto(ghashInput, 0)
    cipherText.copyInto(ghashInput, aadPadLen)
    val aadBits = aad.size.toLong() * 8
    val cBits = cipherText.size.toLong() * 8
    for (i in 0 until 8) {
        ghashInput[aadPadLen + cPadLen + 7 - i] = ((aadBits shr (i * 8)) and 0xFF).toByte()
        ghashInput[aadPadLen + cPadLen + 15 - i] = ((cBits shr (i * 8)) and 0xFF).toByte()
    }
    val s = wordsToBytes(ghash(h, ghashInput))
    val ekj0 = aesEncryptBlock(j0, expandedKey)
    val tag = ByteArray(tagLen) { (s[it].toInt() xor ekj0[it].toInt()).toByte() }
    return if (encrypt) {
        cipherText + tag
    } else {
        val given = input.copyOfRange(input.size - tagLen, input.size)
        if (!tag.contentEquals(given)) throw javax.crypto.AEADBadTagException("Tag mismatch")
        plainOrCipher
    }
}

private fun gctr(expandedKey: IntArray, j0: ByteArray, input: ByteArray): ByteArray {
    val counter = j0.copyOf()
    incrementCounter(counter, 12)
    val result = ByteArray(input.size)
    var pos = 0
    while (pos < input.size) {
        val keystream = aesEncryptBlock(counter, expandedKey)
        val n = minOf(16, input.size - pos)
        for (i in 0 until n) result[pos + i] = (input[pos + i].toInt() xor keystream[i].toInt()).toByte()
        pos += n
        incrementCounter(counter, 12)
    }
    return result
}

private fun bytesToWords(b: ByteArray): IntArray = IntArray(4) { i ->
    ((b[i * 4].toInt() and 0xFF) shl 24) or ((b[i * 4 + 1].toInt() and 0xFF) shl 16) or
        ((b[i * 4 + 2].toInt() and 0xFF) shl 8) or (b[i * 4 + 3].toInt() and 0xFF)
}

private fun wordsToBytes(w: IntArray): ByteArray = ByteArray(16) { i -> (w[i / 4] ushr (24 - (i % 4) * 8)).toByte() }

private fun ghash(h: IntArray, data: ByteArray): IntArray {
    var y = IntArray(4)
    for (blockIndex in data.indices step 16) {
        val x = bytesToWords(data.copyOfRange(blockIndex, blockIndex + 16))
        for (i in 0 until 4) y[i] = y[i] xor x[i]
        y = gfMultiply(y, h)
    }
    return y
}

/** Multiplication in GF(2^128) with the GCM bit ordering. */
private fun gfMultiply(x: IntArray, h: IntArray): IntArray {
    val z = IntArray(4)
    val v = h.copyOf()
    for (i in 0 until 128) {
        if ((x[i ushr 5] ushr (31 - (i and 31))) and 1 == 1) {
            for (j in 0 until 4) z[j] = z[j] xor v[j]
        }
        val lsb = v[3] and 1
        v[3] = (v[3] ushr 1) or (v[2] shl 31)
        v[2] = (v[2] ushr 1) or (v[1] shl 31)
        v[1] = (v[1] ushr 1) or (v[0] shl 31)
        v[0] = v[0] ushr 1
        if (lsb == 1) v[0] = v[0] xor 0xE1000000.toInt()
    }
    return z
}

private fun pkcs7Pad(data: ByteArray, blockSize: Int): ByteArray {
    val padding = blockSize - (data.size % blockSize)
    val padded = ByteArray(data.size + padding)
    data.copyInto(padded)
    for (i in data.size until padded.size) {
        padded[i] = padding.toByte()
    }
    return padded
}

private fun pkcs7Unpad(data: ByteArray): ByteArray {
    if (data.isEmpty()) return data
    val padding = data.last().toInt() and 0xFF
    if (padding == 0 || padding > 16) return data
    // Verify padding
    for (i in data.size - padding until data.size) {
        if ((data[i].toInt() and 0xFF) != padding) return data
    }
    return data.copyOfRange(0, data.size - padding)
}

// AES S-box
private val SBOX = intArrayOf(
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
)

// Inverse S-box
private val INV_SBOX = intArrayOf(
    0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb,
    0x7c, 0xe3, 0x39, 0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb,
    0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2, 0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e,
    0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76, 0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25,
    0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc, 0x5d, 0x65, 0xb6, 0x92,
    0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d, 0x84,
    0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06,
    0xd0, 0x2c, 0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b,
    0x3a, 0x91, 0x11, 0x41, 0x4f, 0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73,
    0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85, 0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e,
    0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62, 0x0e, 0xaa, 0x18, 0xbe, 0x1b,
    0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd, 0x5a, 0xf4,
    0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f,
    0x60, 0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef,
    0xa0, 0xe0, 0x3b, 0x4d, 0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61,
    0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6, 0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d
)

// Round constants
private val RCON = intArrayOf(
    0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36
)

private fun expandKey(key: ByteArray): IntArray {
    val nk = key.size / 4 // Number of 32-bit words in key
    val nr = nk + 6 // Number of rounds
    val expanded = IntArray(4 * (nr + 1))
    
    // First nk words are the key itself
    for (i in 0 until nk) {
        expanded[i] = ((key[4 * i].toInt() and 0xFF) shl 24) or
                ((key[4 * i + 1].toInt() and 0xFF) shl 16) or
                ((key[4 * i + 2].toInt() and 0xFF) shl 8) or
                (key[4 * i + 3].toInt() and 0xFF)
    }
    
    for (i in nk until expanded.size) {
        var temp = expanded[i - 1]
        if (i % nk == 0) {
            temp = subWord(rotWord(temp)) xor (RCON[i / nk - 1] shl 24)
        } else if (nk > 6 && i % nk == 4) {
            temp = subWord(temp)
        }
        expanded[i] = expanded[i - nk] xor temp
    }
    
    return expanded
}

private fun subWord(word: Int): Int {
    return (SBOX[(word shr 24) and 0xFF] shl 24) or
            (SBOX[(word shr 16) and 0xFF] shl 16) or
            (SBOX[(word shr 8) and 0xFF] shl 8) or
            SBOX[word and 0xFF]
}

private fun rotWord(word: Int): Int {
    return (word shl 8) or ((word shr 24) and 0xFF)
}

private fun aesEncryptBlock(block: ByteArray, expandedKey: IntArray): ByteArray {
    val state = Array(4) { row -> IntArray(4) { col -> block[row + 4 * col].toInt() and 0xFF } }
    val nr = expandedKey.size / 4 - 1
    
    // AddRoundKey
    addRoundKey(state, expandedKey, 0)
    
    for (round in 1 until nr) {
        subBytes(state)
        shiftRows(state)
        mixColumns(state)
        addRoundKey(state, expandedKey, round)
    }
    
    // Final round (no MixColumns)
    subBytes(state)
    shiftRows(state)
    addRoundKey(state, expandedKey, nr)
    
    val result = ByteArray(16)
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            result[row + 4 * col] = state[row][col].toByte()
        }
    }
    return result
}

private fun aesDecryptBlock(block: ByteArray, expandedKey: IntArray): ByteArray {
    val state = Array(4) { row -> IntArray(4) { col -> block[row + 4 * col].toInt() and 0xFF } }
    val nr = expandedKey.size / 4 - 1
    
    // AddRoundKey
    addRoundKey(state, expandedKey, nr)
    
    for (round in nr - 1 downTo 1) {
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, expandedKey, round)
        invMixColumns(state)
    }
    
    // Final round
    invShiftRows(state)
    invSubBytes(state)
    addRoundKey(state, expandedKey, 0)
    
    val result = ByteArray(16)
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            result[row + 4 * col] = state[row][col].toByte()
        }
    }
    return result
}

private fun addRoundKey(state: Array<IntArray>, expandedKey: IntArray, round: Int) {
    for (col in 0 until 4) {
        val word = expandedKey[round * 4 + col]
        state[0][col] = state[0][col] xor ((word shr 24) and 0xFF)
        state[1][col] = state[1][col] xor ((word shr 16) and 0xFF)
        state[2][col] = state[2][col] xor ((word shr 8) and 0xFF)
        state[3][col] = state[3][col] xor (word and 0xFF)
    }
}

private fun subBytes(state: Array<IntArray>) {
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            state[row][col] = SBOX[state[row][col]]
        }
    }
}

private fun invSubBytes(state: Array<IntArray>) {
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            state[row][col] = INV_SBOX[state[row][col]]
        }
    }
}

private fun shiftRows(state: Array<IntArray>) {
    // Row 1: shift left by 1
    val temp1 = state[1][0]
    state[1][0] = state[1][1]
    state[1][1] = state[1][2]
    state[1][2] = state[1][3]
    state[1][3] = temp1
    
    // Row 2: shift left by 2
    var temp = state[2][0]
    state[2][0] = state[2][2]
    state[2][2] = temp
    temp = state[2][1]
    state[2][1] = state[2][3]
    state[2][3] = temp
    
    // Row 3: shift left by 3 (same as right by 1)
    val temp3 = state[3][3]
    state[3][3] = state[3][2]
    state[3][2] = state[3][1]
    state[3][1] = state[3][0]
    state[3][0] = temp3
}

private fun invShiftRows(state: Array<IntArray>) {
    // Row 1: shift right by 1
    val temp1 = state[1][3]
    state[1][3] = state[1][2]
    state[1][2] = state[1][1]
    state[1][1] = state[1][0]
    state[1][0] = temp1
    
    // Row 2: shift right by 2
    var temp = state[2][0]
    state[2][0] = state[2][2]
    state[2][2] = temp
    temp = state[2][1]
    state[2][1] = state[2][3]
    state[2][3] = temp
    
    // Row 3: shift right by 3 (same as left by 1)
    val temp3 = state[3][0]
    state[3][0] = state[3][1]
    state[3][1] = state[3][2]
    state[3][2] = state[3][3]
    state[3][3] = temp3
}

private fun mixColumns(state: Array<IntArray>) {
    for (col in 0 until 4) {
        val s0 = state[0][col]
        val s1 = state[1][col]
        val s2 = state[2][col]
        val s3 = state[3][col]
        
        state[0][col] = gmul(2, s0) xor gmul(3, s1) xor s2 xor s3
        state[1][col] = s0 xor gmul(2, s1) xor gmul(3, s2) xor s3
        state[2][col] = s0 xor s1 xor gmul(2, s2) xor gmul(3, s3)
        state[3][col] = gmul(3, s0) xor s1 xor s2 xor gmul(2, s3)
    }
}

private fun invMixColumns(state: Array<IntArray>) {
    for (col in 0 until 4) {
        val s0 = state[0][col]
        val s1 = state[1][col]
        val s2 = state[2][col]
        val s3 = state[3][col]
        
        state[0][col] = gmul(0x0e, s0) xor gmul(0x0b, s1) xor gmul(0x0d, s2) xor gmul(0x09, s3)
        state[1][col] = gmul(0x09, s0) xor gmul(0x0e, s1) xor gmul(0x0b, s2) xor gmul(0x0d, s3)
        state[2][col] = gmul(0x0d, s0) xor gmul(0x09, s1) xor gmul(0x0e, s2) xor gmul(0x0b, s3)
        state[3][col] = gmul(0x0b, s0) xor gmul(0x0d, s1) xor gmul(0x09, s2) xor gmul(0x0e, s3)
    }
}

// Galois field multiplication
private fun gmul(a: Int, b: Int): Int {
    var result = 0
    var aa = a
    var bb = b
    while (aa != 0) {
        if ((aa and 1) != 0) {
            result = result xor bb
        }
        val hiBitSet = (bb and 0x80) != 0
        bb = bb shl 1
        if (hiBitSet) {
            bb = bb xor 0x1b // x^8 + x^4 + x^3 + x + 1
        }
        bb = bb and 0xFF
        aa = aa shr 1
    }
    return result
}

