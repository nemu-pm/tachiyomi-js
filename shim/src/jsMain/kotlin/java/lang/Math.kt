package java.lang

import kotlin.math.pow

/**
 * java.lang.Math for sources that call it by name instead of kotlin.math.
 */
object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E

    fun abs(a: Int): Int = kotlin.math.abs(a)
    fun abs(a: Long): Long = kotlin.math.abs(a)
    fun abs(a: Float): Float = kotlin.math.abs(a)
    fun abs(a: Double): Double = kotlin.math.abs(a)

    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)

    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)

    /** Java rounds half up (toward positive infinity), unlike Kotlin's half-to-even `roundToLong`. */
    fun round(a: Double): Long = kotlin.math.floor(a + 0.5).toLong()
    fun round(a: Float): Int = kotlin.math.floor(a + 0.5f).toInt()
    fun rint(a: Double): Double = kotlin.math.round(a)

    fun floor(a: Double): Double = kotlin.math.floor(a)
    fun ceil(a: Double): Double = kotlin.math.ceil(a)
    fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
    fun cbrt(a: Double): Double = kotlin.math.cbrt(a)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun exp(a: Double): Double = kotlin.math.exp(a)
    fun log(a: Double): Double = kotlin.math.ln(a)
    fun log10(a: Double): Double = kotlin.math.log10(a)
    fun log1p(a: Double): Double = kotlin.math.ln1p(a)
    fun sin(a: Double): Double = kotlin.math.sin(a)
    fun cos(a: Double): Double = kotlin.math.cos(a)
    fun tan(a: Double): Double = kotlin.math.tan(a)
    fun asin(a: Double): Double = kotlin.math.asin(a)
    fun acos(a: Double): Double = kotlin.math.acos(a)
    fun atan(a: Double): Double = kotlin.math.atan(a)
    fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    fun hypot(x: Double, y: Double): Double = kotlin.math.hypot(x, y)
    fun signum(a: Double): Double = kotlin.math.sign(a)
    fun signum(a: Float): Float = kotlin.math.sign(a)
    fun toRadians(deg: Double): Double = deg / 180.0 * kotlin.math.PI
    fun toDegrees(rad: Double): Double = rad * 180.0 / kotlin.math.PI

    fun floorDiv(x: Int, y: Int): Int = kotlin.math.floor(x.toDouble() / y).toInt()
    fun floorDiv(x: Long, y: Long): Long = floorDivLong(x, y)
    fun floorMod(x: Int, y: Int): Int = x - floorDiv(x, y) * y
    fun floorMod(x: Long, y: Long): Long = x - floorDiv(x, y) * y

    private fun floorDivLong(x: Long, y: Long): Long {
        val q = x / y
        return if ((x % y != 0L) && ((x < 0) != (y < 0))) q - 1 else q
    }

    fun toIntExact(value: Long): Int {
        if (value != value.toInt().toLong()) throw ArithmeticException("integer overflow")
        return value.toInt()
    }

    fun addExact(x: Int, y: Int): Int = toIntExact(x.toLong() + y)
    fun multiplyExact(x: Int, y: Int): Int = toIntExact(x.toLong() * y)

    fun random(): Double = kotlin.random.Random.nextDouble()
}
