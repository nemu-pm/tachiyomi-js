package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit

internal const val NANOS_PER_DAY = 86400L * NANOS_PER_SECOND

/** Time-based ChronoField values for a nano-of-day, or null when the field is not time-based. */
internal fun timeFieldValue(nanoOfDay: Long, field: TemporalField): Long? {
    val nano = nanoOfDay % NANOS_PER_SECOND
    val secondOfDay = nanoOfDay / NANOS_PER_SECOND
    val hour = secondOfDay / 3600
    val minute = (secondOfDay / 60) % 60
    val second = secondOfDay % 60
    return when (field) {
        ChronoField.NANO_OF_SECOND -> nano
        ChronoField.NANO_OF_DAY -> nanoOfDay
        ChronoField.MICRO_OF_SECOND -> nano / 1000
        ChronoField.MICRO_OF_DAY -> nanoOfDay / 1000
        ChronoField.MILLI_OF_SECOND -> nano / 1_000_000
        ChronoField.MILLI_OF_DAY -> nanoOfDay / 1_000_000
        ChronoField.SECOND_OF_MINUTE -> second
        ChronoField.SECOND_OF_DAY -> secondOfDay
        ChronoField.MINUTE_OF_HOUR -> minute
        ChronoField.MINUTE_OF_DAY -> secondOfDay / 60
        ChronoField.HOUR_OF_AMPM -> hour % 12
        ChronoField.CLOCK_HOUR_OF_AMPM -> (hour % 12).let { if (it == 0L) 12L else it }
        ChronoField.HOUR_OF_DAY -> hour
        ChronoField.CLOCK_HOUR_OF_DAY -> if (hour == 0L) 24L else hour
        ChronoField.AMPM_OF_DAY -> hour / 12
        else -> null
    }
}

class LocalTime private constructor(val hour: Int, val minute: Int, val second: Int, val nano: Int) : Temporal, Comparable<LocalTime> {
    fun getHour(): Int = hour
    fun getMinute(): Int = minute
    fun getSecond(): Int = second
    fun getNano(): Int = nano

    fun toSecondOfDay(): Int = hour * 3600 + minute * 60 + second
    fun toNanoOfDay(): Long = toSecondOfDay() * NANOS_PER_SECOND + nano

    fun atDate(date: LocalDate): LocalDateTime = LocalDateTime.of(date, this)

    fun plusHours(hours: Long): LocalTime = plusNanos(hours * 3600 * NANOS_PER_SECOND)
    fun plusMinutes(minutes: Long): LocalTime = plusNanos(minutes * 60 * NANOS_PER_SECOND)
    fun plusSeconds(seconds: Long): LocalTime = plusNanos(seconds * NANOS_PER_SECOND)
    fun plusNanos(nanos: Long): LocalTime = if (nanos == 0L) this else ofNanoOfDay(floorMod(toNanoOfDay() + nanos, NANOS_PER_DAY))
    fun minusHours(hours: Long): LocalTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): LocalTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): LocalTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): LocalTime = plusNanos(-nanos)

    override fun plus(amountToAdd: Long, unit: TemporalUnit): LocalTime {
        if (unit !is ChronoUnit || unit.isDateBased) throw DateTimeException("Unsupported unit: $unit")
        return plusNanos(amountToAdd * unit.nanos)
    }

    override fun minus(amountToSubtract: Long, unit: TemporalUnit): LocalTime = plus(-amountToSubtract, unit)

    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        if (unit !is ChronoUnit || unit.isDateBased) throw DateTimeException("Unsupported unit: $unit")
        return (from(endExclusive).toNanoOfDay() - toNanoOfDay()) / unit.nanos
    }

    fun truncatedTo(unit: TemporalUnit): LocalTime {
        if (unit == ChronoUnit.NANOS) return this
        val unitNanos = (unit as ChronoUnit).nanos
        if (unitNanos > NANOS_PER_DAY) throw DateTimeException("Unit is too large to be used for truncation")
        return ofNanoOfDay(toNanoOfDay() - toNanoOfDay() % unitNanos)
    }

    fun withHour(hour: Int): LocalTime = of(hour, minute, second, nano)
    fun withMinute(minute: Int): LocalTime = of(hour, minute, second, nano)
    fun withSecond(second: Int): LocalTime = of(hour, minute, second, nano)
    fun withNano(nanoOfSecond: Int): LocalTime = of(hour, minute, second, nanoOfSecond)

    fun isAfter(other: LocalTime): Boolean = compareTo(other) > 0
    fun isBefore(other: LocalTime): Boolean = compareTo(other) < 0
    override fun compareTo(other: LocalTime): Int = toNanoOfDay().compareTo(other.toNanoOfDay())

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && field.isTimeBased
    override fun getLong(field: TemporalField): Long = timeFieldValue(toNanoOfDay(), field) ?: throw DateTimeException("Unsupported field: $field")

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun equals(other: Any?): Boolean = other is LocalTime && other.toNanoOfDay() == toNanoOfDay()
    override fun hashCode(): Int = toNanoOfDay().hashCode()

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(padNumber(hour.toLong(), 2)).append(':').append(padNumber(minute.toLong(), 2))
        if (second > 0 || nano > 0) {
            sb.append(':').append(padNumber(second.toLong(), 2))
            if (nano > 0) {
                sb.append('.')
                when {
                    nano % 1_000_000 == 0 -> sb.append(padNumber((nano / 1_000_000).toLong(), 3))
                    nano % 1000 == 0 -> sb.append(padNumber((nano / 1000).toLong(), 6))
                    else -> sb.append(padNumber(nano.toLong(), 9))
                }
            }
        }
        return sb.toString()
    }

    companion object {
        val MIN: LocalTime = LocalTime(0, 0, 0, 0)
        val MAX: LocalTime = LocalTime(23, 59, 59, 999_999_999)
        val MIDNIGHT: LocalTime = MIN
        val NOON: LocalTime = LocalTime(12, 0, 0, 0)

        fun of(hour: Int, minute: Int): LocalTime = of(hour, minute, 0, 0)
        fun of(hour: Int, minute: Int, second: Int): LocalTime = of(hour, minute, second, 0)
        fun of(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalTime {
            ChronoField.HOUR_OF_DAY.checkValidValue(hour.toLong())
            ChronoField.MINUTE_OF_HOUR.checkValidValue(minute.toLong())
            ChronoField.SECOND_OF_MINUTE.checkValidValue(second.toLong())
            ChronoField.NANO_OF_SECOND.checkValidValue(nanoOfSecond.toLong())
            return LocalTime(hour, minute, second, nanoOfSecond)
        }

        fun ofSecondOfDay(secondOfDay: Long): LocalTime = ofNanoOfDay(secondOfDay * NANOS_PER_SECOND)
        fun ofNanoOfDay(nanoOfDay: Long): LocalTime {
            ChronoField.NANO_OF_DAY.checkValidValue(nanoOfDay)
            val secondOfDay = nanoOfDay / NANOS_PER_SECOND
            return LocalTime((secondOfDay / 3600).toInt(), ((secondOfDay / 60) % 60).toInt(), (secondOfDay % 60).toInt(), (nanoOfDay % NANOS_PER_SECOND).toInt())
        }

        fun now(): LocalTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalTime = ZonedDateTime.now(zone).toLocalTime()

        fun from(temporal: TemporalAccessor): LocalTime = when (temporal) {
            is LocalTime -> temporal
            is LocalDateTime -> temporal.toLocalTime()
            is ZonedDateTime -> temporal.toLocalTime()
            is OffsetDateTime -> temporal.toLocalTime()
            else -> {
                if (temporal.isSupported(ChronoField.NANO_OF_DAY)) {
                    ofNanoOfDay(temporal.getLong(ChronoField.NANO_OF_DAY))
                } else {
                    throw DateTimeException("Unable to obtain LocalTime from TemporalAccessor: $temporal")
                }
            }
        }

        fun parse(text: CharSequence?): LocalTime = parse(text, DateTimeFormatter.ISO_LOCAL_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalTime = formatter.parse(text!!, ::from)
    }
}
