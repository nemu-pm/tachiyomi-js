package java.time

import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

/** java.time.Duration subset: second-resolution amounts of time (nanos carried for completeness). */
class Duration private constructor(val seconds: Long, val nano: Int) : Comparable<Duration> {
    val isZero: Boolean get() = seconds == 0L && nano == 0
    val isNegative: Boolean get() = seconds < 0

    fun toDays(): Long = seconds / 86400
    fun toHours(): Long = seconds / 3600
    fun toMinutes(): Long = seconds / 60
    fun toSeconds(): Long = seconds
    fun toMillis(): Long = seconds * 1000 + nano / 1_000_000
    fun toNanos(): Long = seconds * NANOS_PER_SECOND + nano
    fun getSeconds(): Long = seconds
    fun getNano(): Int = nano

    fun plus(other: Duration): Duration = ofSeconds(seconds + other.seconds, nano.toLong() + other.nano)
    fun minus(other: Duration): Duration = ofSeconds(seconds - other.seconds, nano.toLong() - other.nano)
    fun plusSeconds(amount: Long): Duration = ofSeconds(seconds + amount, nano.toLong())
    fun plusMinutes(amount: Long): Duration = plusSeconds(amount * 60)
    fun plusHours(amount: Long): Duration = plusSeconds(amount * 3600)
    fun plusDays(amount: Long): Duration = plusSeconds(amount * 86400)
    fun plusMillis(amount: Long): Duration = ofSeconds(seconds, nano + amount * 1_000_000)
    fun minusSeconds(amount: Long): Duration = plusSeconds(-amount)
    fun minusMinutes(amount: Long): Duration = plusMinutes(-amount)
    fun minusHours(amount: Long): Duration = plusHours(-amount)
    fun minusDays(amount: Long): Duration = plusDays(-amount)
    fun multipliedBy(multiplicand: Long): Duration = ofSeconds(seconds * multiplicand, nano.toLong() * multiplicand)
    fun dividedBy(divisor: Long): Duration = ofNanos(toNanos() / divisor)
    fun negated(): Duration = ofSeconds(-seconds, -nano.toLong())
    fun abs(): Duration = if (isNegative) negated() else this

    override fun compareTo(other: Duration): Int {
        val c = seconds.compareTo(other.seconds)
        return if (c != 0) c else nano.compareTo(other.nano)
    }

    override fun equals(other: Any?): Boolean = other is Duration && other.seconds == seconds && other.nano == nano
    override fun hashCode(): Int = seconds.hashCode() * 31 + nano

    override fun toString(): String {
        if (isZero) return "PT0S"
        val totalSecs = seconds
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        val sb = StringBuilder("PT")
        if (hours != 0L) sb.append(hours).append('H')
        if (minutes != 0L) sb.append(minutes).append('M')
        if (secs != 0L || nano != 0 || sb.length == 2) {
            sb.append(secs)
            if (nano != 0) sb.append('.').append(padNumber(nano.toLong(), 9).trimEnd('0'))
            sb.append('S')
        }
        return sb.toString()
    }

    companion object {
        val ZERO = Duration(0, 0)

        fun ofDays(days: Long): Duration = Duration(days * 86400, 0)
        fun ofHours(hours: Long): Duration = Duration(hours * 3600, 0)
        fun ofMinutes(minutes: Long): Duration = Duration(minutes * 60, 0)
        fun ofSeconds(seconds: Long): Duration = Duration(seconds, 0)
        fun ofSeconds(seconds: Long, nanoAdjustment: Long): Duration {
            val secs = seconds + floorDiv(nanoAdjustment, NANOS_PER_SECOND)
            val nos = floorMod(nanoAdjustment, NANOS_PER_SECOND).toInt()
            return Duration(secs, nos)
        }
        fun ofMillis(millis: Long): Duration = ofSeconds(floorDiv(millis, 1000), floorMod(millis, 1000) * 1_000_000)
        fun ofNanos(nanos: Long): Duration = ofSeconds(floorDiv(nanos, NANOS_PER_SECOND), floorMod(nanos, NANOS_PER_SECOND))
        fun of(amount: Long, unit: TemporalUnit): Duration = when (unit) {
            ChronoUnit.NANOS -> ofNanos(amount)
            ChronoUnit.MICROS -> ofNanos(amount * 1000)
            ChronoUnit.MILLIS -> ofMillis(amount)
            else -> ofSeconds(amount * unit.durationSeconds)
        }
        fun between(startInclusive: Instant, endExclusive: Instant): Duration =
            ofSeconds(endExclusive.epochSecond - startInclusive.epochSecond, (endExclusive.nano - startInclusive.nano).toLong())

        /** Accepts the subset `PnDTnHnMn.nS` (days, hours, minutes, seconds). */
        fun parse(text: String): Duration {
            val m = Regex("""^([-+]?)P(?:([-+]?\d+)D)?(?:T(?:([-+]?\d+)H)?(?:([-+]?\d+)M)?(?:([-+]?\d+)(?:\.(\d{1,9}))?S)?)?$""", RegexOption.IGNORE_CASE)
                .find(text.trim()) ?: throw DateTimeException("Text cannot be parsed to a Duration: $text")
            val sign = if (m.groupValues[1] == "-") -1 else 1
            val days = m.groupValues[2].toLongOrNull() ?: 0
            val hours = m.groupValues[3].toLongOrNull() ?: 0
            val minutes = m.groupValues[4].toLongOrNull() ?: 0
            val secs = m.groupValues[5].toLongOrNull() ?: 0
            val fraction = m.groupValues[6].padEnd(9, '0').toLongOrNull() ?: 0
            val total = ((days * 24 + hours) * 60 + minutes) * 60 + secs
            return ofSeconds(sign * total, sign * (if (secs < 0) -fraction else fraction))
        }
    }
}
