package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit

class LocalDateTime private constructor(private val date: LocalDate, private val time: LocalTime) : Temporal, Comparable<LocalDateTime> {
    val year: Int get() = date.year
    val monthValue: Int get() = date.monthValue
    val month: Month get() = date.month
    val dayOfMonth: Int get() = date.dayOfMonth
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
    val dayOfYear: Int get() = date.dayOfYear
    val hour: Int get() = time.hour
    val minute: Int get() = time.minute
    val second: Int get() = time.second
    val nano: Int get() = time.nano

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

    fun toLocalDate(): LocalDate = date
    fun toLocalTime(): LocalTime = time

    fun toEpochSecond(offset: ZoneOffset): Long = date.toEpochDay() * SECONDS_PER_DAY + time.toSecondOfDay() - offset.totalSeconds
    fun toInstant(offset: ZoneOffset): Instant = Instant.ofEpochSecond(toEpochSecond(offset), nano.toLong())
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.of(this, offset)

    fun plusYears(years: Long): LocalDateTime = withDate(date.plusYears(years))
    fun plusMonths(months: Long): LocalDateTime = withDate(date.plusMonths(months))
    fun plusWeeks(weeks: Long): LocalDateTime = withDate(date.plusWeeks(weeks))
    fun plusDays(days: Long): LocalDateTime = withDate(date.plusDays(days))
    fun plusHours(hours: Long): LocalDateTime = plusNanos(hours * 3600 * NANOS_PER_SECOND)
    fun plusMinutes(minutes: Long): LocalDateTime = plusNanos(minutes * 60 * NANOS_PER_SECOND)
    fun plusSeconds(seconds: Long): LocalDateTime = plusNanos(seconds * NANOS_PER_SECOND)
    fun plusNanos(nanos: Long): LocalDateTime {
        if (nanos == 0L) return this
        val total = time.toNanoOfDay() + nanos
        val days = floorDiv(total, NANOS_PER_DAY)
        return LocalDateTime(date.plusDays(days), LocalTime.ofNanoOfDay(floorMod(total, NANOS_PER_DAY)))
    }
    fun minusYears(years: Long): LocalDateTime = plusYears(-years)
    fun minusMonths(months: Long): LocalDateTime = plusMonths(-months)
    fun minusWeeks(weeks: Long): LocalDateTime = plusWeeks(-weeks)
    fun minusDays(days: Long): LocalDateTime = plusDays(-days)
    fun minusHours(hours: Long): LocalDateTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): LocalDateTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): LocalDateTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): LocalDateTime = plusNanos(-nanos)

    override fun plus(amountToAdd: Long, unit: TemporalUnit): LocalDateTime {
        if (unit !is ChronoUnit) throw DateTimeException("Unsupported unit: $unit")
        return if (unit.isDateBased) withDate(date.plus(amountToAdd, unit)) else plusNanos(amountToAdd * unit.nanos)
    }

    override fun minus(amountToSubtract: Long, unit: TemporalUnit): LocalDateTime = plus(-amountToSubtract, unit)

    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        val end = from(endExclusive)
        if (unit !is ChronoUnit) throw DateTimeException("Unsupported unit: $unit")
        if (unit.isDateBased) {
            var endDate = end.date
            if (endDate.isAfter(date) && end.time.isBefore(time)) endDate = endDate.minusDays(1)
            else if (endDate.isBefore(date) && end.time.isAfter(time)) endDate = endDate.plusDays(1)
            return date.until(endDate, unit)
        }
        val days = end.date.toEpochDay() - date.toEpochDay()
        val nanosDiff = days * NANOS_PER_DAY + (end.time.toNanoOfDay() - time.toNanoOfDay())
        return nanosDiff / unit.nanos
    }

    fun truncatedTo(unit: TemporalUnit): LocalDateTime = when (unit) {
        ChronoUnit.DAYS -> LocalDateTime(date, LocalTime.MIDNIGHT)
        else -> LocalDateTime(date, time.truncatedTo(unit))
    }

    fun withYear(year: Int): LocalDateTime = withDate(date.withYear(year))
    fun withMonth(month: Int): LocalDateTime = withDate(date.withMonth(month))
    fun withDayOfMonth(dayOfMonth: Int): LocalDateTime = withDate(date.withDayOfMonth(dayOfMonth))
    fun withDayOfYear(dayOfYear: Int): LocalDateTime = withDate(date.withDayOfYear(dayOfYear))
    fun withHour(hour: Int): LocalDateTime = LocalDateTime(date, time.withHour(hour))
    fun withMinute(minute: Int): LocalDateTime = LocalDateTime(date, time.withMinute(minute))
    fun withSecond(second: Int): LocalDateTime = LocalDateTime(date, time.withSecond(second))
    fun withNano(nanoOfSecond: Int): LocalDateTime = LocalDateTime(date, time.withNano(nanoOfSecond))
    private fun withDate(newDate: LocalDate): LocalDateTime = if (newDate === date) this else LocalDateTime(newDate, time)

    fun isAfter(other: LocalDateTime): Boolean = compareTo(other) > 0
    fun isBefore(other: LocalDateTime): Boolean = compareTo(other) < 0
    fun isEqual(other: LocalDateTime): Boolean = compareTo(other) == 0
    override fun compareTo(other: LocalDateTime): Int {
        val c = date.compareTo(other.date)
        return if (c != 0) c else time.compareTo(other.time)
    }

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && (field.isDateBased || field.isTimeBased)
    override fun getLong(field: TemporalField): Long =
        if (field is ChronoField && field.isTimeBased) time.getLong(field) else date.getLong(field)

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun equals(other: Any?): Boolean = other is LocalDateTime && other.date == date && other.time == time
    override fun hashCode(): Int = date.hashCode() xor time.hashCode()
    override fun toString(): String = "${date}T$time"

    companion object {
        val MIN: LocalDateTime = LocalDateTime(LocalDate.MIN, LocalTime.MIN)
        val MAX: LocalDateTime = LocalDateTime(LocalDate.MAX, LocalTime.MAX)

        fun of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute))
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute, second))
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute, second, nanoOfSecond))
        fun of(year: Int, month: Month, dayOfMonth: Int, hour: Int, minute: Int): LocalDateTime = of(year, month.value, dayOfMonth, hour, minute)
        fun of(year: Int, month: Month, dayOfMonth: Int, hour: Int, minute: Int, second: Int): LocalDateTime = of(year, month.value, dayOfMonth, hour, minute, second)

        fun ofEpochSecond(epochSecond: Long, nanoOfSecond: Int, offset: ZoneOffset): LocalDateTime {
            val localSecond = epochSecond + offset.totalSeconds
            val epochDay = floorDiv(localSecond, SECONDS_PER_DAY)
            val secsOfDay = floorMod(localSecond, SECONDS_PER_DAY)
            return LocalDateTime(LocalDate.ofEpochDay(epochDay), LocalTime.ofNanoOfDay(secsOfDay * NANOS_PER_SECOND + nanoOfSecond))
        }

        fun ofInstant(instant: Instant, zone: ZoneId): LocalDateTime =
            ofEpochSecond(instant.epochSecond, instant.nano, zone.offsetAt(instant.epochSecond))

        fun now(): LocalDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): LocalDateTime = when (temporal) {
            is LocalDateTime -> temporal
            is ZonedDateTime -> temporal.toLocalDateTime()
            is OffsetDateTime -> temporal.toLocalDateTime()
            else -> LocalDateTime(LocalDate.from(temporal), LocalTime.from(temporal))
        }

        fun parse(text: CharSequence?): LocalDateTime = parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalDateTime = formatter.parse(text!!, ::from)
    }
}
