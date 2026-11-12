package java.util

/** java.util.Random over kotlin.random; seeded instances repeat, like the JVM, but not the JVM's sequence. */
open class Random(seed: Long? = null) {
    private var rng: kotlin.random.Random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default

    constructor(seed: Long) : this(seed as Long?)

    fun setSeed(seed: Long) {
        rng = kotlin.random.Random(seed)
    }

    fun nextInt(): Int = rng.nextInt()
    fun nextInt(bound: Int): Int = rng.nextInt(bound)
    fun nextInt(origin: Int, bound: Int): Int = rng.nextInt(origin, bound)
    fun nextLong(): Long = rng.nextLong()
    fun nextDouble(): Double = rng.nextDouble()
    fun nextFloat(): Float = rng.nextFloat()
    fun nextBoolean(): Boolean = rng.nextBoolean()
    fun nextGaussian(): Double {
        // Box-Muller
        val u1 = rng.nextDouble().coerceAtLeast(Double.MIN_VALUE)
        val u2 = rng.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
    }
    fun nextBytes(bytes: ByteArray) {
        rng.nextBytes(bytes)
    }
}
