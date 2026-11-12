package java.time.temporal

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

interface TemporalField {
    /** Value of this field read from [temporal]. */
    fun getFrom(temporal: TemporalAccessor): Long
    fun isSupportedBy(temporal: TemporalAccessor): Boolean
}

interface TemporalUnit {
    /** Length of the unit in seconds (estimated for date units, as in Java). */
    val durationSeconds: Long
    val isDateBased: Boolean
    val isTimeBased: Boolean get() = !isDateBased
}

fun interface TemporalQuery<R> {
    fun queryFrom(temporal: TemporalAccessor): R
}

interface TemporalAccessor {
    fun isSupported(field: TemporalField): Boolean
    fun getLong(field: TemporalField): Long
    fun get(field: TemporalField): Int = getLong(field).toInt()
    fun <R> query(query: TemporalQuery<R>): R = query.queryFrom(this)

    /** Zone carried by this temporal, if any (ZonedDateTime, parsed text with a zone id). */
    fun zoneOrNull(): ZoneId? = null

    /** Offset carried by this temporal, if any. */
    fun offsetOrNull(): ZoneOffset? = null
}

interface Temporal : TemporalAccessor {
    fun plus(amountToAdd: Long, unit: TemporalUnit): Temporal
    fun minus(amountToSubtract: Long, unit: TemporalUnit): Temporal = plus(-amountToSubtract, unit)
    fun until(endExclusive: Temporal, unit: TemporalUnit): Long
}

enum class ChronoUnit(override val durationSeconds: Long, override val isDateBased: Boolean) : TemporalUnit {
    NANOS(0, false),
    MICROS(0, false),
    MILLIS(0, false),
    SECONDS(1, false),
    MINUTES(60, false),
    HOURS(3600, false),
    HALF_DAYS(43200, false),
    DAYS(86400, true),
    WEEKS(7 * 86400, true),
    MONTHS(2629746, true),
    YEARS(31556952, true),
    DECADES(315569520, true),
    CENTURIES(3155695200, true),
    MILLENNIA(31556952000, true),
    ERAS(31556952000000000, true),
    FOREVER(Long.MAX_VALUE, false);

    /** Nanoseconds in one unit, for the sub-second units. */
    val nanos: Long get() = when (this) {
        NANOS -> 1
        MICROS -> 1_000
        MILLIS -> 1_000_000
        else -> durationSeconds * 1_000_000_000L
    }

    fun between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long =
        temporal1Inclusive.until(temporal2Exclusive, this)
}

enum class ChronoField(val baseUnit: ChronoUnit, val rangeMin: Long, val rangeMax: Long) : TemporalField {
    NANO_OF_SECOND(ChronoUnit.NANOS, 0, 999_999_999),
    NANO_OF_DAY(ChronoUnit.NANOS, 0, 86400L * 1_000_000_000L - 1),
    MICRO_OF_SECOND(ChronoUnit.MICROS, 0, 999_999),
    MICRO_OF_DAY(ChronoUnit.MICROS, 0, 86400L * 1_000_000L - 1),
    MILLI_OF_SECOND(ChronoUnit.MILLIS, 0, 999),
    MILLI_OF_DAY(ChronoUnit.MILLIS, 0, 86400L * 1000L - 1),
    SECOND_OF_MINUTE(ChronoUnit.SECONDS, 0, 59),
    SECOND_OF_DAY(ChronoUnit.SECONDS, 0, 86399),
    MINUTE_OF_HOUR(ChronoUnit.MINUTES, 0, 59),
    MINUTE_OF_DAY(ChronoUnit.MINUTES, 0, 1439),
    HOUR_OF_AMPM(ChronoUnit.HOURS, 0, 11),
    CLOCK_HOUR_OF_AMPM(ChronoUnit.HOURS, 1, 12),
    HOUR_OF_DAY(ChronoUnit.HOURS, 0, 23),
    CLOCK_HOUR_OF_DAY(ChronoUnit.HOURS, 1, 24),
    AMPM_OF_DAY(ChronoUnit.HALF_DAYS, 0, 1),
    DAY_OF_WEEK(ChronoUnit.DAYS, 1, 7),
    ALIGNED_DAY_OF_WEEK_IN_MONTH(ChronoUnit.DAYS, 1, 7),
    ALIGNED_DAY_OF_WEEK_IN_YEAR(ChronoUnit.DAYS, 1, 7),
    DAY_OF_MONTH(ChronoUnit.DAYS, 1, 31),
    DAY_OF_YEAR(ChronoUnit.DAYS, 1, 366),
    EPOCH_DAY(ChronoUnit.DAYS, -365243219162L, 365241780471L),
    ALIGNED_WEEK_OF_MONTH(ChronoUnit.WEEKS, 1, 5),
    ALIGNED_WEEK_OF_YEAR(ChronoUnit.WEEKS, 1, 53),
    MONTH_OF_YEAR(ChronoUnit.MONTHS, 1, 12),
    PROLEPTIC_MONTH(ChronoUnit.MONTHS, -999999999L * 12, 999999999L * 12 + 11),
    YEAR_OF_ERA(ChronoUnit.YEARS, 1, 999999999),
    YEAR(ChronoUnit.YEARS, -999999999, 999999999),
    ERA(ChronoUnit.ERAS, 0, 1),
    INSTANT_SECONDS(ChronoUnit.SECONDS, Long.MIN_VALUE, Long.MAX_VALUE),
    OFFSET_SECONDS(ChronoUnit.SECONDS, -18 * 3600, 18 * 3600);

    override fun getFrom(temporal: TemporalAccessor): Long = temporal.getLong(this)
    override fun isSupportedBy(temporal: TemporalAccessor): Boolean = temporal.isSupported(this)

    val isDateBased: Boolean get() = ordinal >= DAY_OF_WEEK.ordinal && this != INSTANT_SECONDS && this != OFFSET_SECONDS
    val isTimeBased: Boolean get() = ordinal < DAY_OF_WEEK.ordinal

    fun checkValidValue(value: Long): Long {
        if (value < rangeMin || value > rangeMax) throw DateTimeException("Invalid value for $name: $value")
        return value
    }

    fun checkValidIntValue(value: Long): Int = checkValidValue(value).toInt()
}

/** Only the localized day-of-week number is modelled; week-of-year fields are not used by sources. */
class WeekFields private constructor(val firstDayOfWeek: DayOfWeek, val minimalDaysInFirstWeek: Int) {
    fun dayOfWeek(): TemporalField = object : TemporalField {
        override fun getFrom(temporal: TemporalAccessor): Long {
            val iso = temporal.getLong(ChronoField.DAY_OF_WEEK)
            return java.time.floorMod(iso - firstDayOfWeek.value, 7) + 1
        }

        override fun isSupportedBy(temporal: TemporalAccessor): Boolean = temporal.isSupported(ChronoField.DAY_OF_WEEK)
    }

    companion object {
        val ISO = WeekFields(DayOfWeek.MONDAY, 4)
        val SUNDAY_START = WeekFields(DayOfWeek.SUNDAY, 1)

        fun of(firstDayOfWeek: DayOfWeek, minimalDaysInFirstWeek: Int): WeekFields = WeekFields(firstDayOfWeek, minimalDaysInFirstWeek)

        fun of(locale: Locale): WeekFields = when (locale.getCountry().uppercase()) {
            "US", "CA", "JP", "BR", "MX", "PH", "TW", "KR", "HK", "IL", "SA", "ZA", "IN", "AU" -> SUNDAY_START
            else -> ISO
        }
    }
}
