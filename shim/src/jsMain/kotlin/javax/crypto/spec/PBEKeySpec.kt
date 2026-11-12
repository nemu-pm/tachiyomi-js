package javax.crypto.spec

/** Password-based key spec for [javax.crypto.SecretKeyFactory] (PBKDF2). */
class PBEKeySpec(
    password: CharArray?,
    val salt: ByteArray,
    val iterationCount: Int,
    val keyLength: Int,
) {
    val password: CharArray = password?.copyOf() ?: CharArray(0)

    fun getPassword(): CharArray = password.copyOf()
    fun getSalt(): ByteArray = salt.copyOf()
    fun getIterationCount(): Int = iterationCount
    fun getKeyLength(): Int = keyLength
}
