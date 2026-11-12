package java.security

import kotlin.random.Random

/** java.security.SecureRandom over kotlin.random.Random: nonces and SSL-context arguments in the corpus, nothing key-grade. */
open class SecureRandom {
    private val random = Random.Default

    fun nextBytes(bytes: ByteArray) {
        random.nextBytes(bytes)
    }

    fun nextInt(): Int = random.nextInt()
    fun nextInt(bound: Int): Int = random.nextInt(bound)
    fun nextLong(): Long = random.nextLong()
    fun nextDouble(): Double = random.nextDouble()
    fun nextFloat(): Float = random.nextFloat()
    fun nextBoolean(): Boolean = random.nextBoolean()
}
