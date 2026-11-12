package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit

class Instant private constructor(val epochSecond: Long, val nano: Int) : Temporal, Comparable<Instant> {
    fun getEpochSecond(): Long = epochSecond
    fun getNano(): Int = nano
    fun toEpochMilli(): Long = epochSecond * 1000 + nano / 1_000_000

    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.ofInstant(this, offset)

    fun plusSeconds(seconds: Long): Instant = ofEpochSecond(epochSecond + seconds, nano.toLong())
    fun plusMillis(millis: Long): Instant = ofEpochSecond(epochSecond + floorDiv(millis, 1000), nano + floorMod(millis, 1000) * 1_000_000)
    fun plusNanos(nanos: Long): Instant = ofEpochSecond(epochSecond, nano + nanos)
    fun minusSeconds(seconds: Long): Instant = plusSeconds(-seconds)
    fun minusMillis(millis: Long): Instant = plusMillis(-millis)
    fun minusNanos(nanos: Long): Instant = plusNanos(-nanos)
    fun plus(duration: Duration): Instant = ofEpochSecond(epochSecond + duration.seconds, nano.toLong() + duration.nano)
    fun minus(duration: Duration): Instant = plus(duration.negated())

    override fun plus(amountToAdd: Long, unit: TemporalUnit): Instant = when (unit) {
        ChronoUnit.NANOS -> plusNanos(amountToAdd)
        ChronoUnit.MICROS -> plusNanos(amountToAdd * 1000)
        ChronoUnit.MILLIS -> plusMillis(amountToAdd)
        else -> {
            if (unit.isDateBased && unit != ChronoUnit.DAYS && unit != ChronoUnit.WEEKS) {
                throw DateTimeException("Unsupported unit: $unit")
            }
            plusSeconds(amountToAdd * unit.durationSeconds)
        }
    }

    override fun minus(amountToSubtract: Long, unit: TemporalUnit): Instant = plus(-amountToSubtract, unit)

    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        val end = from(endExclusive)
        val nanosDiff = (end.epochSecond - epochSecond) * NANOS_PER_SECOND + (end.nano - nano)
        return when (unit) {
            ChronoUnit.NANOS -> nanosDiff
            ChronoUnit.MICROS -> nanosDiff / 1000
            ChronoUnit.MILLIS -> nanosDiff / 1_000_000
            else -> {
                val secs = end.epochSecond - epochSecond - (if (end.nano < nano) 1 else 0)
                secs / unit.durationSeconds
            }
        }
    }

    fun truncatedTo(unit: TemporalUnit): Instant {
        if (unit == ChronoUnit.NANOS) return this
        val unitNanos = (unit as ChronoUnit).nanos
        if (unitNanos < NANOS_PER_SECOND) return ofEpochSecond(epochSecond, nano - nano % unitNanos)
        val unitSecs = unit.durationSeconds
        return ofEpochSecond(epochSecond - floorMod(epochSecond, unitSecs), 0)
    }

    fun isAfter(other: Instant): Boolean = compareTo(other) > 0
    fun isBefore(other: Instant): Boolean = compareTo(other) < 0

    override fun compareTo(other: Instant): Int {
        val c = epochSecond.compareTo(other.epochSecond)
        return if (c != 0) c else nano.compareTo(other.nano)
    }

    override fun isSupported(field: TemporalField): Boolean =
        field == ChronoField.INSTANT_SECONDS || field == ChronoField.NANO_OF_SECOND ||
            field == ChronoField.MICRO_OF_SECOND || field == ChronoField.MILLI_OF_SECOND

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> epochSecond
        ChronoField.NANO_OF_SECOND -> nano.toLong()
        ChronoField.MICRO_OF_SECOND -> (nano / 1000).toLong()
        ChronoField.MILLI_OF_SECOND -> (nano / 1_000_000).toLong()
        else -> throw DateTimeException("Unsupported field: $field")
    }

    override fun equals(other: Any?): Boolean = other is Instant && other.epochSecond == epochSecond && other.nano == nano
    override fun hashCode(): Int = epochSecond.hashCode() * 31 + nano

    /** ISO-8601 instant, e.g. `2024-01-15T10:30:00Z`. */
    override fun toString(): String = DateTimeFormatter.ISO_INSTANT.format(this)

    companion object {
        val EPOCH: Instant = Instant(0, 0)
        val MIN: Instant = Instant(-31557014167219200L, 0)
        val MAX: Instant = Instant(31556889864403199L, 999_999_999)

        fun now(): Instant = ofEpochMilli(currentEpochMillis())
        fun now(clock: Any?): Instant = now()

        fun ofEpochSecond(epochSecond: Long): Instant = Instant(epochSecond, 0)
        fun ofEpochSecond(epochSecond: Long, nanoAdjustment: Long): Instant =
            Instant(epochSecond + floorDiv(nanoAdjustment, NANOS_PER_SECOND), floorMod(nanoAdjustment, NANOS_PER_SECOND).toInt())
        fun ofEpochMilli(epochMilli: Long): Instant =
            Instant(floorDiv(epochMilli, 1000), (floorMod(epochMilli, 1000) * 1_000_000).toInt())

        fun from(temporal: TemporalAccessor): Instant = when (temporal) {
            is Instant -> temporal
            is ZonedDateTime -> temporal.toInstant()
            is OffsetDateTime -> temporal.toInstant()
            else -> {
                if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
                    ofEpochSecond(temporal.getLong(ChronoField.INSTANT_SECONDS), temporal.getLong(ChronoField.NANO_OF_SECOND))
                } else {
                    throw DateTimeException("Unable to obtain Instant from TemporalAccessor: $temporal")
                }
            }
        }

        /** ISO-8601 with `Z` or an offset, e.g. `2024-01-15T10:30:00.000Z` or `2024-01-15T10:30:00+01:00`. */
        fun parse(text: CharSequence): Instant = DateTimeFormatter.ISO_INSTANT.parse(text, ::from)
    }
}
