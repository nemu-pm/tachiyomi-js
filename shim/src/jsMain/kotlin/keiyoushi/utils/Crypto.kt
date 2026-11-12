package keiyoushi.utils

// Ported from keiyoushi core utils/Crypto.kt. The streaming RC4 and XOR sources read each chunk
// through a scratch Buffer instead of Buffer.UnsafeCursor, which the shim's Buffer does not have.

import android.util.Base64
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.cipherSource
import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * Decodes this hexadecimal string into a [ByteArray].
 *
 * @throws IllegalArgumentException if the string has an odd length or contains invalid hex characters
 */
fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Unexpected hex string: $this" }

    val result = ByteArray(length / 2)
    for (i in result.indices) {
        val d1 = decodeHexDigit(this[i * 2]) shl 4
        val d2 = decodeHexDigit(this[i * 2 + 1])
        result[i] = (d1 + d2).toByte()
    }
    return result
}

private fun decodeHexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("Unexpected hex digit: $c")
}

/** Computes the MD5 digest of this [ByteArray]. */
fun ByteArray.md5(): ByteArray = MessageDigest.getInstance("MD5").digest(this)

/** Computes the MD5 digest of this string's UTF-8 bytes. */
fun String.md5(): ByteArray = encodeToByteArray().md5()

/** Computes the SHA-256 digest of this [ByteArray]. */
fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

/** Computes the SHA-256 digest of this string's UTF-8 bytes. */
fun String.sha256(): ByteArray = encodeToByteArray().sha256()

/** Computes the SHA-512 digest of this [ByteArray]. */
fun ByteArray.sha512(): ByteArray = MessageDigest.getInstance("SHA-512").digest(this)

/** Computes the SHA-512 digest of this string's UTF-8 bytes. */
fun String.sha512(): ByteArray = encodeToByteArray().sha512()

/** Computes the HMAC-SHA256 of this [ByteArray] with [key]. */
fun ByteArray.hmacSha256(key: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    doFinal(this@hmacSha256)
}

/** Computes the HMAC-SHA256 of this string's UTF-8 bytes with [key]. */
fun String.hmacSha256(key: ByteArray): ByteArray = encodeToByteArray().hmacSha256(key)

/**
 * Encrypts or decrypts this [ByteArray] using the RC4 stream cipher with [key].
 *
 * An optional [skip] count discards that many initial keystream bytes before processing this [ByteArray].
 *
 * @throws IllegalArgumentException if [key] is empty, exceeds 256 bytes, or [skip] is negative
 */
fun ByteArray.rc4(key: ByteArray, skip: Int = 0): ByteArray = copyOf().also { Rc4(key, skip).apply(it, 0, it.size) }

/**
 * Encrypts or decrypts this [Source] using the RC4 stream cipher with [key].
 *
 * @throws IllegalArgumentException if [key] is empty, exceeds 256 bytes, or [skip] is negative
 */
fun Source.rc4(key: ByteArray, skip: Int = 0): Source = Rc4Source(this, Rc4(key, skip))

private class Rc4Source(
    delegate: Source,
    private val rc4: Rc4,
) : ForwardingSource(delegate) {
    private val scratch = Buffer()

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(scratch, byteCount)
        if (read <= 0L) return read

        val bytes = scratch.readByteArray()
        rc4.apply(bytes, 0, bytes.size)
        sink.write(bytes)
        return read
    }
}

private class Rc4(key: ByteArray, skip: Int) {
    private val s = IntArray(256) { it }
    private var a = 0
    private var b = 0

    init {
        require(key.isNotEmpty()) { "RC4 key must not be empty" }
        require(key.size <= 256) { "RC4 key must not exceed 256 bytes, got ${key.size}" }
        require(skip >= 0) { "RC4 skip must be non-negative, got $skip" }
        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[i]
            s[i] = s[j]
            s[j] = t
        }
        repeat(skip) {
            a = (a + 1) and 0xFF
            b = (b + s[a]) and 0xFF
            val t = s[a]
            s[a] = s[b]
            s[b] = t
        }
    }

    fun apply(data: ByteArray, offset: Int, length: Int) {
        val s = s
        var a = a
        var b = b
        for (i in offset until offset + length) {
            a = (a + 1) and 0xFF
            val sa = s[a]
            b = (b + sa) and 0xFF
            val sb = s[b]
            s[a] = sb
            s[b] = sa
            data[i] = (data[i].toInt() xor s[(sa + sb) and 0xFF]).toByte()
        }
        this.a = a
        this.b = b
    }
}

/** Decrypts or encrypts this [Source] by applying a single-byte XOR [key] mask. */
fun Source.xor(key: Byte): Source = xor(byteArrayOf(key))

/** Decrypts or encrypts this [Source] by applying a single-byte XOR [key] mask (converted to Byte). */
fun Source.xor(key: Int): Source = xor(key.toByte())

/**
 * Decrypts or encrypts this [Source] by applying a multi-byte XOR [key] cyclically.
 *
 * @throws IllegalArgumentException if [key] is empty
 */
fun Source.xor(key: ByteArray): Source {
    require(key.isNotEmpty()) { "XOR key must not be empty" }
    return XorSource(this, key)
}

/** Transforms this [ByteArray] in-place by applying a single-byte XOR [key] mask. */
fun ByteArray.xorInPlace(key: Byte): ByteArray {
    for (i in indices) {
        this[i] = this[i] xor key
    }
    return this
}

/** Transforms this [ByteArray] in-place by applying a single-byte XOR [key] mask (converted to Byte). */
fun ByteArray.xorInPlace(key: Int): ByteArray = xorInPlace(key.toByte())

/**
 * Transforms this [ByteArray] in-place by applying a multi-byte XOR [key] cyclically.
 *
 * @throws IllegalArgumentException if [key] is empty
 */
fun ByteArray.xorInPlace(key: ByteArray): ByteArray {
    require(key.isNotEmpty()) { "XOR key must not be empty" }
    var k = 0
    for (i in indices) {
        this[i] = this[i] xor key[k]
        if (++k == key.size) k = 0
    }
    return this
}

/** Transforms a copy of this [ByteArray] by applying a single-byte XOR [key] mask. */
fun ByteArray.xor(key: Byte): ByteArray = copyOf().xorInPlace(key)

/** Transforms a copy of this [ByteArray] by applying a single-byte XOR [key] mask (converted to Byte). */
fun ByteArray.xor(key: Int): ByteArray = copyOf().xorInPlace(key.toByte())

/**
 * Transforms a copy of this [ByteArray] by applying a multi-byte XOR [key] cyclically.
 *
 * @throws IllegalArgumentException if [key] is empty
 */
fun ByteArray.xor(key: ByteArray): ByteArray = copyOf().xorInPlace(key)

/** Wraps this [ResponseBody] source with a single-byte XOR [key] mask transformation. */
fun ResponseBody.xor(key: Byte, contentType: MediaType? = contentType()): ResponseBody = source().xor(key).buffer().asResponseBody(contentType, contentLength())

/** Wraps this [ResponseBody] source with a single-byte XOR [key] mask (converted to Byte) transformation. */
fun ResponseBody.xor(key: Int, contentType: MediaType? = contentType()): ResponseBody = xor(key.toByte(), contentType)

/** Wraps this [ResponseBody] source with a multi-byte cyclic XOR [key] transformation. */
fun ResponseBody.xor(key: ByteArray, contentType: MediaType? = contentType()): ResponseBody = source().xor(key).buffer().asResponseBody(contentType, contentLength())

/** Replaces the [Response.body] with a single-byte XOR [key] mask transformed body. */
fun Response.xor(key: Byte, contentType: MediaType? = body.contentType()): Response = newBuilder().body(body.xor(key, contentType)).build()

/** Replaces the [Response.body] with a single-byte XOR [key] mask (converted to Byte) transformed body. */
fun Response.xor(key: Int, contentType: MediaType? = body.contentType()): Response = xor(key.toByte(), contentType)

/** Replaces the [Response.body] with a multi-byte cyclic XOR [key] transformed body. */
fun Response.xor(key: ByteArray, contentType: MediaType? = body.contentType()): Response = newBuilder().body(body.xor(key, contentType)).build()

private class XorSource(delegate: Source, private val key: ByteArray) : ForwardingSource(delegate) {
    private val scratch = Buffer()
    private var index = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(scratch, byteCount)
        if (read <= 0L) return read

        val data = scratch.readByteArray()
        var k = index
        for (i in data.indices) {
            data[i] = data[i] xor key[k]
            if (++k == key.size) k = 0
        }
        index = k
        sink.write(data)
        return read
    }
}

/**
 * Creates and initializes an AES [Cipher].
 *
 * @param params optional algorithm parameter specifications such as [IvParameterSpec] or [GCMParameterSpec]
 * @param encrypt true for [Cipher.ENCRYPT_MODE], false for [Cipher.DECRYPT_MODE]
 */
fun newAesCipher(
    transformation: String,
    key: ByteArray,
    params: AlgorithmParameterSpec? = null,
    encrypt: Boolean = false,
): Cipher = Cipher.getInstance(transformation).apply {
    init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), params)
}

/** Creates and initializes an AES [Cipher] in CBC mode. */
fun aesCbc(
    key: ByteArray,
    iv: ByteArray,
    padding: Boolean = true,
    encrypt: Boolean = false,
): Cipher = newAesCipher(
    transformation = if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding",
    key = key,
    params = IvParameterSpec(iv),
    encrypt = encrypt,
)

/** Creates and initializes an AES [Cipher] in GCM mode. */
fun aesGcm(
    key: ByteArray,
    iv: ByteArray,
    tagLength: Int = 128,
    encrypt: Boolean = false,
): Cipher = newAesCipher(
    transformation = "AES/GCM/NoPadding",
    key = key,
    params = GCMParameterSpec(tagLength, iv),
    encrypt = encrypt,
)

/** Creates and initializes an AES [Cipher] in CTR mode. */
fun aesCtr(
    key: ByteArray,
    iv: ByteArray,
    encrypt: Boolean = false,
): Cipher = newAesCipher(
    transformation = "AES/CTR/NoPadding",
    key = key,
    params = IvParameterSpec(iv),
    encrypt = encrypt,
)

/** Creates and initializes an AES [Cipher] in ECB mode. */
fun aesEcb(
    key: ByteArray,
    padding: Boolean = true,
    encrypt: Boolean = false,
): Cipher = newAesCipher(
    transformation = if (padding) "AES/ECB/PKCS5Padding" else "AES/ECB/NoPadding",
    key = key,
    params = null,
    encrypt = encrypt,
)

/** Decrypts in-memory [ByteArray] using the specified [Cipher]. */
fun ByteArray.aesDecrypt(cipher: Cipher): ByteArray = cipher.doFinal(this)

/** Encrypts in-memory [ByteArray] using the specified [Cipher]. */
fun ByteArray.aesEncrypt(cipher: Cipher): ByteArray = cipher.doFinal(this)

/** Wraps this Okio [Source] in a streaming AES decryption [Source] using the provided [cipher]. */
fun Source.aesDecrypt(cipher: Cipher): Source = cipherSource(cipher)

/** Returns a new [ResponseBody] streaming decrypted data from this body using [cipher]. */
fun ResponseBody.aesDecrypt(
    cipher: Cipher,
    contentType: MediaType? = this.contentType(),
): ResponseBody = source().aesDecrypt(cipher).buffer().asResponseBody(contentType)

/** Returns a new [Response] whose [Response.body] streams decrypted data using [cipher]. */
fun Response.aesDecrypt(
    cipher: Cipher,
    contentType: MediaType? = body.contentType(),
): Response = newBuilder()
    .body(body.aesDecrypt(cipher, contentType))
    .build()

/**
 * Decrypts a [Response] where the initialization vector (IV) is prepended to the response body stream.
 *
 * @param ivSize the size of the prepended IV in bytes
 * @param cipher function taking the extracted IV and returning an initialized [Cipher]
 */
fun Response.aesDecryptWithPrependedIv(
    ivSize: Int,
    contentType: MediaType? = body.contentType(),
    cipher: (iv: ByteArray) -> Cipher,
): Response {
    val source = body.source()
    val iv = source.readByteArray(ivSize.toLong())
    val decryptedBody = source.cipherSource(cipher(iv)).buffer().asResponseBody(contentType)
    return newBuilder().body(decryptedBody).build()
}

/**
 * Decrypts a [Response] where the initialization vector (IV) is prepended to the response body stream,
 * using AES-CBC.
 */
fun Response.aesDecryptWithPrependedIv(
    key: ByteArray,
    ivSize: Int,
    padding: Boolean = true,
    contentType: MediaType? = body.contentType(),
): Response = aesDecryptWithPrependedIv(ivSize, contentType) { iv ->
    aesCbc(key, iv, padding)
}

/**
 * Derives a key and IV from [password] and [salt] using OpenSSL's `EVP_BytesToKey` with MD5 and a single iteration.
 */
fun evpBytesToKey(
    password: ByteArray,
    salt: ByteArray?,
    keyLen: Int = 32,
    ivLen: Int = 16,
): Pair<ByteArray, ByteArray> {
    val md = MessageDigest.getInstance("MD5")
    val derived = Buffer()
    var block = ByteArray(0)
    while (derived.size < (keyLen + ivLen).toLong()) {
        md.update(block)
        md.update(password)
        if (salt != null) md.update(salt)
        block = md.digest()
        derived.write(block)
    }
    return derived.readByteArray(keyLen.toLong()) to derived.readByteArray(ivLen.toLong())
}

/**
 * Decrypts an OpenSSL-formatted (`Salted__` + 8-byte salt + ciphertext) payload with a [passphrase],
 * matching `CryptoJS.AES.decrypt(ciphertext, passphrase)`.
 *
 * @throws IllegalArgumentException if the payload is missing the `Salted__` header
 */
fun ByteArray.cryptoJsDecrypt(passphrase: String): ByteArray {
    require(size >= 16 && copyOfRange(0, 8).contentEquals(SALTED_HEADER)) { "Missing Salted__ header" }
    val (key, iv) = evpBytesToKey(passphrase.encodeToByteArray(), copyOfRange(8, 16))
    return copyOfRange(16, size).aesDecrypt(aesCbc(key, iv))
}

/**
 * Decrypts a Base64-encoded OpenSSL-formatted payload with a [passphrase],
 * matching `CryptoJS.AES.decrypt(ciphertext, passphrase).toString(CryptoJS.enc.Utf8)`.
 */
fun String.cryptoJsDecrypt(passphrase: String): String = Base64.decode(this, Base64.DEFAULT).cryptoJsDecrypt(passphrase).decodeToString()

private val SALTED_HEADER = "Salted__".encodeToByteArray()
