package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit

class ZonedDateTime private constructor(private val dateTime: LocalDateTime, val offset: ZoneOffset, val zone: ZoneId) : Temporal, Comparable<ZonedDateTime> {
    val year: Int get() = dateTime.year
    val monthValue: Int get() = dateTime.monthValue
    val month: Month get() = dateTime.month
    val dayOfMonth: Int get() = dateTime.dayOfMonth
    val dayOfWeek: DayOfWeek get() = dateTime.dayOfWeek
    val dayOfYear: Int get() = dateTime.dayOfYear
    val hour: Int get() = dateTime.hour
    val minute: Int get() = dateTime.minute
    val second: Int get() = dateTime.second
    val nano: Int get() = dateTime.nano

    fun getYear(): Int = year
    fun getMonthValue(): Int = monthValue
    fun getMonth(): Month = month
    fun getDayOfMonth(): Int = dayOfMonth
    fun getDayOfWeek(): DayOfWeek = dayOfWeek
    fun getDayOfYear(): Int = dayOfYear
    fun getHour(): Int = hour
    fun getMinute(): Int = minute
    fun getSecond(): Int = second
    fun getNano(): Int = nano
    fun getZone(): ZoneId = zone
    fun getOffset(): ZoneOffset = offset

    fun toLocalDateTime(): LocalDateTime = dateTime
    fun toLocalDate(): LocalDate = dateTime.toLocalDate()
    fun toLocalTime(): LocalTime = dateTime.toLocalTime()
    fun toEpochSecond(): Long = dateTime.toEpochSecond(offset)
    fun toInstant(): Instant = Instant.ofEpochSecond(toEpochSecond(), nano.toLong())
    fun toOffsetDateTime(): OffsetDateTime = OffsetDateTime.of(dateTime, offset)

    fun withZoneSameInstant(zone: ZoneId): ZonedDateTime = if (zone == this.zone) this else ofInstant(toInstant(), zone)
    fun withZoneSameLocal(zone: ZoneId): ZonedDateTime = if (zone == this.zone) this else ofLocal(dateTime, zone, offset)
    fun withFixedOffsetZone(): ZonedDateTime = if (zone == offset) this else ZonedDateTime(dateTime, offset, offset)

    private fun resolveLocal(newDateTime: LocalDateTime): ZonedDateTime = ofLocal(newDateTime, zone, offset)
    private fun resolveInstant(newDateTime: LocalDateTime): ZonedDateTime = ofInstant(newDateTime.toInstant(offset), zone)

    fun plusYears(years: Long): ZonedDateTime = resolveLocal(dateTime.plusYears(years))
    fun plusMonths(months: Long): ZonedDateTime = resolveLocal(dateTime.plusMonths(months))
    fun plusWeeks(weeks: Long): ZonedDateTime = resolveLocal(dateTime.plusWeeks(weeks))
    fun plusDays(days: Long): ZonedDateTime = resolveLocal(dateTime.plusDays(days))
    fun plusHours(hours: Long): ZonedDateTime = resolveInstant(dateTime.plusHours(hours))
    fun plusMinutes(minutes: Long): ZonedDateTime = resolveInstant(dateTime.plusMinutes(minutes))
    fun plusSeconds(seconds: Long): ZonedDateTime = resolveInstant(dateTime.plusSeconds(seconds))
    fun plusNanos(nanos: Long): ZonedDateTime = resolveInstant(dateTime.plusNanos(nanos))
    fun minusYears(years: Long): ZonedDateTime = plusYears(-years)
    fun minusMonths(months: Long): ZonedDateTime = plusMonths(-months)
    fun minusWeeks(weeks: Long): ZonedDateTime = plusWeeks(-weeks)
    fun minusDays(days: Long): ZonedDateTime = plusDays(-days)
    fun minusHours(hours: Long): ZonedDateTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): ZonedDateTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): ZonedDateTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): ZonedDateTime = plusNanos(-nanos)
    fun plus(duration: Duration): ZonedDateTime = plusSeconds(duration.seconds).plusNanos(duration.nano.toLong())
    fun minus(duration: Duration): ZonedDateTime = plus(duration.negated())

    override fun plus(amountToAdd: Long, unit: TemporalUnit): ZonedDateTime {
        if (unit !is ChronoUnit) throw DateTimeException("Unsupported unit: $unit")
        return if (unit.isDateBased) resolveLocal(dateTime.plus(amountToAdd, unit)) else resolveInstant(dateTime.plus(amountToAdd, unit))
    }

    override fun minus(amountToSubtract: Long, unit: TemporalUnit): ZonedDateTime = plus(-amountToSubtract, unit)

    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        val end = from(endExclusive).withZoneSameInstant(zone)
        if (unit !is ChronoUnit) throw DateTimeException("Unsupported unit: $unit")
        return if (unit.isDateBased) dateTime.until(end.dateTime, unit) else toOffsetDateTime().until(end.toOffsetDateTime(), unit)
    }

    fun truncatedTo(unit: TemporalUnit): ZonedDateTime = resolveLocal(dateTime.truncatedTo(unit))

    fun withYear(year: Int): ZonedDateTime = resolveLocal(dateTime.withYear(year))
    fun withMonth(month: Int): ZonedDateTime = resolveLocal(dateTime.withMonth(month))
    fun withDayOfMonth(dayOfMonth: Int): ZonedDateTime = resolveLocal(dateTime.withDayOfMonth(dayOfMonth))
    fun withDayOfYear(dayOfYear: Int): ZonedDateTime = resolveLocal(dateTime.withDayOfYear(dayOfYear))
    fun withHour(hour: Int): ZonedDateTime = resolveLocal(dateTime.withHour(hour))
    fun withMinute(minute: Int): ZonedDateTime = resolveLocal(dateTime.withMinute(minute))
    fun withSecond(second: Int): ZonedDateTime = resolveLocal(dateTime.withSecond(second))
    fun withNano(nanoOfSecond: Int): ZonedDateTime = resolveLocal(dateTime.withNano(nanoOfSecond))

    fun isAfter(other: ZonedDateTime): Boolean = compareTo(other) > 0
    fun isBefore(other: ZonedDateTime): Boolean = compareTo(other) < 0
    fun isEqual(other: ZonedDateTime): Boolean = compareTo(other) == 0
    override fun compareTo(other: ZonedDateTime): Int {
        val c = toEpochSecond().compareTo(other.toEpochSecond())
        return if (c != 0) c else nano.compareTo(other.nano)
    }

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField
    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> toEpochSecond()
        ChronoField.OFFSET_SECONDS -> offset.totalSeconds.toLong()
        else -> dateTime.getLong(field)
    }

    override fun zoneOrNull(): ZoneId = zone
    override fun offsetOrNull(): ZoneOffset = offset

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun equals(other: Any?): Boolean = other is ZonedDateTime && other.dateTime == dateTime && other.offset == offset && other.zone == zone
    override fun hashCode(): Int = dateTime.hashCode() xor offset.hashCode() xor zone.hashCode()
    override fun toString(): String {
        val base = "$dateTime${offset.id}"
        return if (zone == offset) base else "$base[$zone]"
    }

    companion object {
        fun of(localDateTime: LocalDateTime, zone: ZoneId): ZonedDateTime = ofLocal(localDateTime, zone, null)
        fun of(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = of(LocalDateTime.of(date, time), zone)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, zone: ZoneId): ZonedDateTime =
            of(LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond), zone)

        fun ofLocal(localDateTime: LocalDateTime, zone: ZoneId, preferredOffset: ZoneOffset?): ZonedDateTime {
            if (zone is ZoneOffset) return ZonedDateTime(localDateTime, zone, zone)
            val localSecond = localDateTime.toEpochSecond(ZoneOffset.UTC)
            val resolved = zone.offsetForLocal(localSecond)
            // In an overlap both offsets are valid; honour the preferred one when it still is.
            if (preferredOffset != null && preferredOffset != resolved) {
                val check = zone.offsetAt(localSecond - preferredOffset.totalSeconds)
                if (check == preferredOffset) return ZonedDateTime(localDateTime, preferredOffset, zone)
            }
            // A gap shifts the local time forward by the gap length.
            val instant = localSecond - resolved.totalSeconds
            val actual = zone.offsetAt(instant)
            val ldt = if (actual == resolved) localDateTime else LocalDateTime.ofEpochSecond(instant, localDateTime.nano, actual)
            return ZonedDateTime(ldt, actual, zone)
        }

        fun ofStrict(localDateTime: LocalDateTime, offset: ZoneOffset, zone: ZoneId): ZonedDateTime = ZonedDateTime(localDateTime, offset, zone)

        fun ofInstant(instant: Instant, zone: ZoneId): ZonedDateTime {
            val offset = zone.offsetAt(instant.epochSecond)
            return ZonedDateTime(LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset), offset, zone)
        }

        fun ofInstant(localDateTime: LocalDateTime, offset: ZoneOffset, zone: ZoneId): ZonedDateTime = ofInstant(localDateTime.toInstant(offset), zone)

        fun now(): ZonedDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): ZonedDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): ZonedDateTime = when (temporal) {
            is ZonedDateTime -> temporal
            is OffsetDateTime -> temporal.toZonedDateTime()
            is Instant -> ofInstant(temporal, ZoneOffset.UTC)
            else -> {
                val zone = temporal.zoneOrNull() ?: temporal.offsetOrNull()
                    ?: throw DateTimeException("Unable to obtain ZonedDateTime from TemporalAccessor: no zone or offset in $temporal")
                if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
                    ofInstant(Instant.from(temporal), zone)
                } else {
                    ofLocal(LocalDateTime.from(temporal), zone, temporal.offsetOrNull())
                }
            }
        }

        fun parse(text: CharSequence?): ZonedDateTime = parse(text, DateTimeFormatter.ISO_ZONED_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): ZonedDateTime = formatter.parse(text!!, ::from)
    }
}

class OffsetDateTime private constructor(private val dateTime: LocalDateTime, val offset: ZoneOffset) : Temporal, Comparable<OffsetDateTime> {
    val year: Int get() = dateTime.year
    val monthValue: Int get() = dateTime.monthValue
    val month: Month get() = dateTime.month
    val dayOfMonth: Int get() = dateTime.dayOfMonth
    val dayOfWeek: DayOfWeek get() = dateTime.dayOfWeek
    val dayOfYear: Int get() = dateTime.dayOfYear
    val hour: Int get() = dateTime.hour
    val minute: Int get() = dateTime.minute
    val second: Int get() = dateTime.second
    val nano: Int get() = dateTime.nano

    fun getYear(): Int = year
    fun getMonthValue(): Int = monthValue
    fun getMonth(): Month = month
    fun getDayOfMonth(): Int = dayOfMonth
    fun getDayOfWeek(): DayOfWeek = dayOfWeek
    fun getHour(): Int = hour
    fun getMinute(): Int = minute
    fun getSecond(): Int = second
    fun getNano(): Int = nano
    fun getOffset(): ZoneOffset = offset

    fun toLocalDateTime(): LocalDateTime = dateTime
    fun toLocalDate(): LocalDate = dateTime.toLocalDate()
    fun toLocalTime(): LocalTime = dateTime.toLocalTime()
    fun toEpochSecond(): Long = dateTime.toEpochSecond(offset)
    fun toInstant(): Instant = dateTime.toInstant(offset)
    fun toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofStrict(dateTime, offset, offset)
    fun atZoneSameInstant(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), zone)
    fun atZoneSimilarLocal(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofLocal(dateTime, zone, offset)
    fun withOffsetSameInstant(offset: ZoneOffset): OffsetDateTime = if (offset == this.offset) this else ofInstant(toInstant(), offset)
    fun withOffsetSameLocal(offset: ZoneOffset): OffsetDateTime = OffsetDateTime(dateTime, offset)

    fun plusYears(years: Long): OffsetDateTime = OffsetDateTime(dateTime.plusYears(years), offset)
    fun plusMonths(months: Long): OffsetDateTime = OffsetDateTime(dateTime.plusMonths(months), offset)
    fun plusWeeks(weeks: Long): OffsetDateTime = OffsetDateTime(dateTime.plusWeeks(weeks), offset)
    fun plusDays(days: Long): OffsetDateTime = OffsetDateTime(dateTime.plusDays(days), offset)
    fun plusHours(hours: Long): OffsetDateTime = OffsetDateTime(dateTime.plusHours(hours), offset)
    fun plusMinutes(minutes: Long): OffsetDateTime = OffsetDateTime(dateTime.plusMinutes(minutes), offset)
    fun plusSeconds(seconds: Long): OffsetDateTime = OffsetDateTime(dateTime.plusSeconds(seconds), offset)
    fun plusNanos(nanos: Long): OffsetDateTime = OffsetDateTime(dateTime.plusNanos(nanos), offset)
    fun minusYears(years: Long): OffsetDateTime = plusYears(-years)
    fun minusMonths(months: Long): OffsetDateTime = plusMonths(-months)
    fun minusWeeks(weeks: Long): OffsetDateTime = plusWeeks(-weeks)
    fun minusDays(days: Long): OffsetDateTime = plusDays(-days)
    fun minusHours(hours: Long): OffsetDateTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): OffsetDateTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): OffsetDateTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): OffsetDateTime = plusNanos(-nanos)

    override fun plus(amountToAdd: Long, unit: TemporalUnit): OffsetDateTime = OffsetDateTime(dateTime.plus(amountToAdd, unit), offset)
    override fun minus(amountToSubtract: Long, unit: TemporalUnit): OffsetDateTime = plus(-amountToSubtract, unit)
    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        val end = from(endExclusive).withOffsetSameInstant(offset)
        return dateTime.until(end.dateTime, unit)
    }

    fun truncatedTo(unit: TemporalUnit): OffsetDateTime = OffsetDateTime(dateTime.truncatedTo(unit), offset)
    fun withHour(hour: Int): OffsetDateTime = OffsetDateTime(dateTime.withHour(hour), offset)
    fun withMinute(minute: Int): OffsetDateTime = OffsetDateTime(dateTime.withMinute(minute), offset)
    fun withSecond(second: Int): OffsetDateTime = OffsetDateTime(dateTime.withSecond(second), offset)
    fun withNano(nanoOfSecond: Int): OffsetDateTime = OffsetDateTime(dateTime.withNano(nanoOfSecond), offset)
    fun withDayOfMonth(dayOfMonth: Int): OffsetDateTime = OffsetDateTime(dateTime.withDayOfMonth(dayOfMonth), offset)

    fun isAfter(other: OffsetDateTime): Boolean = compareTo(other) > 0
    fun isBefore(other: OffsetDateTime): Boolean = compareTo(other) < 0
    fun isEqual(other: OffsetDateTime): Boolean = compareTo(other) == 0
    override fun compareTo(other: OffsetDateTime): Int {
        val c = toEpochSecond().compareTo(other.toEpochSecond())
        return if (c != 0) c else nano.compareTo(other.nano)
    }

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField
    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> toEpochSecond()
        ChronoField.OFFSET_SECONDS -> offset.totalSeconds.toLong()
        else -> dateTime.getLong(field)
    }

    override fun offsetOrNull(): ZoneOffset = offset

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun equals(other: Any?): Boolean = other is OffsetDateTime && other.dateTime == dateTime && other.offset == offset
    override fun hashCode(): Int = dateTime.hashCode() xor offset.hashCode()
    override fun toString(): String = "$dateTime${offset.id}"

    companion object {
        fun of(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime = OffsetDateTime(dateTime, offset)
        fun of(date: LocalDate, time: LocalTime, offset: ZoneOffset): OffsetDateTime = OffsetDateTime(LocalDateTime.of(date, time), offset)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, offset: ZoneOffset): OffsetDateTime =
            OffsetDateTime(LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond), offset)

        fun ofInstant(instant: Instant, zone: ZoneId): OffsetDateTime {
            val offset = zone.offsetAt(instant.epochSecond)
            return OffsetDateTime(LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset), offset)
        }

        fun now(): OffsetDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): OffsetDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): OffsetDateTime = when (temporal) {
            is OffsetDateTime -> temporal
            is ZonedDateTime -> temporal.toOffsetDateTime()
            is Instant -> ofInstant(temporal, ZoneOffset.UTC)
            else -> {
                val offset = temporal.offsetOrNull()
                    ?: temporal.zoneOrNull()?.let { ZonedDateTime.from(temporal).offset }
                    ?: throw DateTimeException("Unable to obtain OffsetDateTime from TemporalAccessor: no offset in $temporal")
                if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) ofInstant(Instant.from(temporal), offset)
                else OffsetDateTime(LocalDateTime.from(temporal), offset)
            }
        }

        fun parse(text: CharSequence?): OffsetDateTime = parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): OffsetDateTime = formatter.parse(text!!, ::from)
    }
}
