package java.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.IntlBridge
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * java.util.Calendar over the shim's java.time: the instant lives in [timeInMillis], the fields
 * are read off a ZonedDateTime in [timeZone]. `set` is lazy like the JVM: pending field values
 * are resolved together, leniently (`set(DATE, 0)` is the last day of the previous month), at the
 * next read.
 */
abstract class Calendar protected constructor(zone: TimeZone, locale: Locale) : Comparable<Calendar> {
    var timeZone: TimeZone = zone
        set(value) {
            complete()
            field = value
            cached = null
        }

    /** Sunday = 1 … Saturday = 7, from the locale's week definition. */
    var firstDayOfWeek: Int = WeekFields.of(locale).firstDayOfWeek.value % 7 + 1
    var minimalDaysInFirstWeek: Int = WeekFields.of(locale).minimalDaysInFirstWeek
    var isLenient: Boolean = true

    private var millis: Long = Date.currentTimeMillis()
    private var cached: ZonedDateTime? = null
    private val pending = LinkedHashMap<Int, Int>()
    /** Whether `HOUR_OF_DAY` (24) or `HOUR`/`AM_PM` (12) was set last; the later one wins. */
    private var hourMode = 0

    var timeInMillis: Long
        get() {
            complete()
            return millis
        }
        set(value) {
            pending.clear()
            millis = value
            cached = null
        }

    var time: Date
        get() = Date(timeInMillis)
        set(value) {
            timeInMillis = value.time
        }

    fun getTimeInMillis(): Long = timeInMillis
    fun setTimeInMillis(millis: Long) {
        timeInMillis = millis
    }

    fun getTime(): Date = time
    fun setTime(date: Date) {
        time = date
    }

    fun getTimeZone(): TimeZone = timeZone
    fun setTimeZone(zone: TimeZone) {
        timeZone = zone
    }

    fun getFirstDayOfWeek(): Int = firstDayOfWeek
    fun setFirstDayOfWeek(value: Int) {
        firstDayOfWeek = value
    }

    fun getMinimalDaysInFirstWeek(): Int = minimalDaysInFirstWeek
    fun setMinimalDaysInFirstWeek(value: Int) {
        minimalDaysInFirstWeek = value
    }

    fun setLenient(lenient: Boolean) {
        isLenient = lenient
    }

    fun toInstant(): Instant = Instant.ofEpochMilli(timeInMillis)

    private fun zdt(): ZonedDateTime {
        complete()
        return cached ?: ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone.toZoneId()).also { cached = it }
    }

    private fun complete() {
        if (pending.isEmpty()) return
        val base = cached ?: ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone.toZoneId())
        val p = LinkedHashMap(pending)
        pending.clear()
        if (!isLenient) {
            for ((field, value) in p) {
                if (value < getMinimum(field) || value > getMaximum(field)) throw IllegalArgumentException("$field: $value")
            }
        }

        val era = p[ERA] ?: (if (base.year > 0) AD else BC)
        val yearOfEra = p[YEAR] ?: (if (base.year > 0) base.year else 1 - base.year)
        val year = if (era == AD) yearOfEra else 1 - yearOfEra
        var month = p[MONTH] ?: (base.monthValue - 1)
        val day: Int
        val dayOfYear = p[DAY_OF_YEAR]
        if (dayOfYear != null && p[MONTH] == null && p[DATE] == null) {
            month = 0
            day = dayOfYear
        } else {
            day = p[DATE] ?: base.dayOfMonth
        }
        var date = LocalDate.of(year, 1, 1).plusMonths(month.toLong()).plusDays(day - 1L)
        p[WEEK_OF_YEAR]?.let { w -> date = date.plusWeeks((w - weekOfYear(date)).toLong()) }
        p[WEEK_OF_MONTH]?.let { w -> date = date.plusWeeks((w - weekOfMonth(date)).toLong()) }
        p[DAY_OF_WEEK_IN_MONTH]?.let { n -> date = date.plusWeeks((n - ((date.dayOfMonth - 1) / 7 + 1)).toLong()) }
        p[DAY_OF_WEEK]?.let { dow ->
            val current = date.dayOfWeek.value % 7 + 1
            date = date.plusDays((((dow - firstDayOfWeek + 7) % 7) - ((current - firstDayOfWeek + 7) % 7)).toLong())
        }

        val hourOfDay = when {
            hourMode == 24 && p.containsKey(HOUR_OF_DAY) -> p[HOUR_OF_DAY]!!
            p.containsKey(HOUR) || p.containsKey(AM_PM) -> (p[AM_PM] ?: (base.hour / 12)) * 12 + (p[HOUR] ?: (base.hour % 12))
            else -> p[HOUR_OF_DAY] ?: base.hour
        }
        val minute = p[MINUTE] ?: base.minute
        val second = p[SECOND] ?: base.second
        val millisecond = p[MILLISECOND] ?: (base.nano / 1_000_000)

        val ldt = date.atStartOfDay()
            .plusHours(hourOfDay.toLong())
            .plusMinutes(minute.toLong())
            .plusSeconds(second.toLong())
            .plusNanos(millisecond * 1_000_000L)
        val resolved = ZonedDateTime.of(ldt, timeZone.toZoneId())
        millis = resolved.toInstant().toEpochMilli()
        cached = resolved
        hourMode = 0
    }

    private fun startOfWeek(date: LocalDate): LocalDate =
        date.minusDays((((date.dayOfWeek.value % 7 + 1) - firstDayOfWeek + 7) % 7).toLong())

    private fun week1Start(year: Int): LocalDate {
        val jan1 = LocalDate.of(year, 1, 1)
        val start = startOfWeek(jan1)
        val daysInFirstWeek = 7 - (jan1.toEpochDay() - start.toEpochDay()).toInt()
        return if (daysInFirstWeek >= minimalDaysInFirstWeek) start else start.plusWeeks(1)
    }

    private fun weekOfYear(date: LocalDate): Int {
        if (!date.isBefore(week1Start(date.year + 1))) return 1
        var start = week1Start(date.year)
        if (date.isBefore(start)) start = week1Start(date.year - 1)
        return ((date.toEpochDay() - start.toEpochDay()) / 7).toInt() + 1
    }

    private fun weekOfMonth(date: LocalDate): Int {
        val first = date.withDayOfMonth(1)
        val start = startOfWeek(first)
        val daysInFirstWeek = 7 - (first.toEpochDay() - start.toEpochDay()).toInt()
        val week1 = if (daysInFirstWeek >= minimalDaysInFirstWeek) start else start.plusWeeks(1)
        if (date.isBefore(week1)) return 0
        return ((date.toEpochDay() - week1.toEpochDay()) / 7).toInt() + 1
    }

    // Kotlin lets `calendar[Calendar.YEAR]` / `calendar[Calendar.YEAR] = y` index JVM Calendar; keep that.
    operator fun get(field: Int): Int {
        val z = zdt()
        return when (field) {
            ERA -> if (z.year > 0) AD else BC
            YEAR -> if (z.year > 0) z.year else 1 - z.year
            MONTH -> z.monthValue - 1
            WEEK_OF_YEAR -> weekOfYear(z.toLocalDate())
            WEEK_OF_MONTH -> weekOfMonth(z.toLocalDate())
            DATE -> z.dayOfMonth
            DAY_OF_YEAR -> z.dayOfYear
            DAY_OF_WEEK -> z.dayOfWeek.value % 7 + 1
            DAY_OF_WEEK_IN_MONTH -> (z.dayOfMonth - 1) / 7 + 1
            AM_PM -> z.hour / 12
            HOUR -> z.hour % 12
            HOUR_OF_DAY -> z.hour
            MINUTE -> z.minute
            SECOND -> z.second
            MILLISECOND -> z.nano / 1_000_000
            ZONE_OFFSET -> timeZone.rawOffset
            DST_OFFSET -> z.offset.totalSeconds * 1000 - timeZone.rawOffset
            else -> throw IllegalArgumentException("Unknown calendar field: $field")
        }
    }

    operator fun set(field: Int, value: Int) {
        if (field < 0 || field >= FIELD_COUNT) throw IllegalArgumentException("Unknown calendar field: $field")
        if (field == ZONE_OFFSET || field == DST_OFFSET) return
        if (field == HOUR_OF_DAY) hourMode = 24 else if (field == HOUR || field == AM_PM) hourMode = 12
        pending[field] = value
        cached = null
    }

    fun set(year: Int, month: Int, date: Int) {
        set(YEAR, year)
        set(MONTH, month)
        set(DATE, date)
    }

    fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int) {
        set(year, month, date)
        set(HOUR_OF_DAY, hourOfDay)
        set(MINUTE, minute)
    }

    fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int) {
        set(year, month, date, hourOfDay, minute)
        set(SECOND, second)
    }

    fun add(field: Int, amount: Int) {
        if (amount == 0) return
        val z = zdt()
        val n = amount.toLong()
        val result = when (field) {
            ERA -> return
            YEAR -> z.plusYears(n)
            MONTH -> z.plusMonths(n)
            WEEK_OF_YEAR, WEEK_OF_MONTH, DAY_OF_WEEK_IN_MONTH -> z.plusWeeks(n)
            DATE, DAY_OF_YEAR, DAY_OF_WEEK -> z.plusDays(n)
            AM_PM -> z.plusHours(n * 12)
            HOUR, HOUR_OF_DAY -> z.plusHours(n)
            MINUTE -> z.plusMinutes(n)
            SECOND -> z.plusSeconds(n)
            MILLISECOND -> z.plusNanos(n * 1_000_000)
            else -> throw IllegalArgumentException("Unknown calendar field: $field")
        }
        millis = result.toInstant().toEpochMilli()
        cached = result
    }

    /** Changes [field] without carrying into larger fields. */
    fun roll(field: Int, amount: Int) {
        if (amount == 0) return
        val min = getActualMinimum(field)
        val range = getActualMaximum(field) - min + 1
        val value = get(field)
        set(field, min + (((value - min + amount) % range) + range) % range)
    }

    fun roll(field: Int, up: Boolean) = roll(field, if (up) 1 else -1)

    /** Unsets every field: the time becomes the epoch at midnight in [timeZone]. */
    fun clear() {
        pending.clear()
        hourMode = 0
        cached = null
        millis = LocalDate.EPOCH.atStartOfDay(timeZone.toZoneId()).toInstant().toEpochMilli()
    }

    fun clear(field: Int) {
        set(field, when (field) {
            YEAR -> 1970
            DATE -> 1
            ERA -> AD
            DAY_OF_WEEK, WEEK_OF_YEAR, DAY_OF_WEEK_IN_MONTH -> 1
            else -> 0
        })
    }

    fun isSet(field: Int): Boolean = true

    fun getActualMinimum(field: Int): Int = getMinimum(field)

    fun getActualMaximum(field: Int): Int {
        val z = zdt()
        return when (field) {
            DATE -> z.toLocalDate().lengthOfMonth()
            DAY_OF_YEAR -> z.toLocalDate().lengthOfYear()
            WEEK_OF_YEAR -> {
                val dec31 = LocalDate.of(z.year, 12, 31)
                val w = weekOfYear(dec31)
                if (w == 1) weekOfYear(dec31.minusWeeks(1)) else w
            }
            WEEK_OF_MONTH -> weekOfMonth(z.toLocalDate().withDayOfMonth(z.toLocalDate().lengthOfMonth()))
            DAY_OF_WEEK_IN_MONTH -> (z.toLocalDate().lengthOfMonth() - 1) / 7 + 1
            else -> getMaximum(field)
        }
    }

    fun getMinimum(field: Int): Int = when (field) {
        ERA, AM_PM, HOUR, HOUR_OF_DAY, MINUTE, SECOND, MILLISECOND, MONTH, WEEK_OF_MONTH -> 0
        YEAR, DATE, DAY_OF_YEAR, DAY_OF_WEEK, WEEK_OF_YEAR, DAY_OF_WEEK_IN_MONTH -> 1
        ZONE_OFFSET -> -13 * 3600 * 1000
        DST_OFFSET -> 0
        else -> throw IllegalArgumentException("Unknown calendar field: $field")
    }

    fun getMaximum(field: Int): Int = when (field) {
        ERA -> 1
        YEAR -> 292278994
        MONTH -> 11
        WEEK_OF_YEAR -> 53
        WEEK_OF_MONTH -> 6
        DATE -> 31
        DAY_OF_YEAR -> 366
        DAY_OF_WEEK -> 7
        DAY_OF_WEEK_IN_MONTH -> 6
        AM_PM -> 1
        HOUR -> 11
        HOUR_OF_DAY -> 23
        MINUTE, SECOND -> 59
        MILLISECOND -> 999
        ZONE_OFFSET -> 14 * 3600 * 1000
        DST_OFFSET -> 2 * 3600 * 1000
        else -> throw IllegalArgumentException("Unknown calendar field: $field")
    }

    fun getGreatestMinimum(field: Int): Int = getMinimum(field)
    fun getLeastMaximum(field: Int): Int = when (field) {
        DATE -> 28
        DAY_OF_YEAR -> 365
        WEEK_OF_YEAR -> 52
        WEEK_OF_MONTH -> 4
        DAY_OF_WEEK_IN_MONTH -> 4
        else -> getMaximum(field)
    }

    open fun getDisplayName(field: Int, style: Int, locale: Locale): String? {
        val textStyle = when (style) {
            SHORT, SHORT_FORMAT -> TextStyle.SHORT
            SHORT_STANDALONE -> TextStyle.SHORT_STANDALONE
            LONG_STANDALONE -> TextStyle.FULL_STANDALONE
            else -> TextStyle.FULL
        }
        return when (field) {
            MONTH -> Month.of(get(MONTH) + 1).getDisplayName(textStyle, locale)
            DAY_OF_WEEK -> DayOfWeek.of((get(DAY_OF_WEEK) + 5) % 7 + 1).getDisplayName(textStyle, locale)
            AM_PM -> IntlBridge.text(locale.toLanguageTag()).let { if (get(AM_PM) == AM) it.am else it.pm }
            ERA -> if (get(ERA) == AD) "AD" else "BC"
            else -> null
        }
    }

    fun getWeekYear(): Int {
        val year = get(YEAR)
        val week = get(WEEK_OF_YEAR)
        return when {
            week == 1 && get(MONTH) == DECEMBER -> year + 1
            week >= 52 && get(MONTH) == JANUARY -> year - 1
            else -> year
        }
    }

    fun before(other: Any?): Boolean = other is Calendar && timeInMillis < other.timeInMillis
    fun after(other: Any?): Boolean = other is Calendar && timeInMillis > other.timeInMillis
    override fun compareTo(other: Calendar): Int = timeInMillis.compareTo(other.timeInMillis)

    open fun clone(): Calendar = GregorianCalendar(timeZone, Locale.getDefault()).also { copy ->
        copy.timeInMillis = timeInMillis
        copy.firstDayOfWeek = firstDayOfWeek
        copy.minimalDaysInFirstWeek = minimalDaysInFirstWeek
        copy.isLenient = isLenient
    }

    override fun equals(other: Any?): Boolean = other is Calendar && other.timeInMillis == timeInMillis && other.timeZone == timeZone
    override fun hashCode(): Int = timeInMillis.hashCode() xor timeZone.hashCode()
    override fun toString(): String = "Calendar[time=$timeInMillis,zone=${timeZone.id}]"

    companion object {
        const val ERA = 0
        const val YEAR = 1
        const val MONTH = 2
        const val WEEK_OF_YEAR = 3
        const val WEEK_OF_MONTH = 4
        const val DATE = 5
        const val DAY_OF_MONTH = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val DAY_OF_WEEK_IN_MONTH = 8
        const val AM_PM = 9
        const val HOUR = 10
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14
        const val ZONE_OFFSET = 15
        const val DST_OFFSET = 16
        const val FIELD_COUNT = 17

        const val SUNDAY = 1
        const val MONDAY = 2
        const val TUESDAY = 3
        const val WEDNESDAY = 4
        const val THURSDAY = 5
        const val FRIDAY = 6
        const val SATURDAY = 7

        const val JANUARY = 0
        const val FEBRUARY = 1
        const val MARCH = 2
        const val APRIL = 3
        const val MAY = 4
        const val JUNE = 5
        const val JULY = 6
        const val AUGUST = 7
        const val SEPTEMBER = 8
        const val OCTOBER = 9
        const val NOVEMBER = 10
        const val DECEMBER = 11
        const val UNDECIMBER = 12

        const val AM = 0
        const val PM = 1

        const val ALL_STYLES = 0
        const val SHORT = 1
        const val LONG = 2
        const val NARROW_FORMAT = 4
        const val SHORT_FORMAT = 1
        const val LONG_FORMAT = 2
        const val SHORT_STANDALONE = 0x8001
        const val LONG_STANDALONE = 0x8002
        const val NARROW_STANDALONE = 0x8004

        private const val AD = 1
        private const val BC = 0

        fun getInstance(): Calendar = GregorianCalendar(TimeZone.getDefault(), Locale.getDefault())
        fun getInstance(locale: Locale): Calendar = GregorianCalendar(TimeZone.getDefault(), locale)
        fun getInstance(zone: TimeZone): Calendar = GregorianCalendar(zone, Locale.getDefault())
        fun getInstance(zone: TimeZone, locale: Locale): Calendar = GregorianCalendar(zone, locale)
    }
}

open class GregorianCalendar(zone: TimeZone, locale: Locale) : Calendar(zone, locale) {
    constructor() : this(TimeZone.getDefault(), Locale.getDefault())
    constructor(zone: TimeZone) : this(zone, Locale.getDefault())
    constructor(locale: Locale) : this(TimeZone.getDefault(), locale)

    constructor(year: Int, month: Int, dayOfMonth: Int) : this() {
        clear()
        set(year, month, dayOfMonth)
    }

    constructor(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int) : this() {
        clear()
        set(year, month, dayOfMonth, hourOfDay, minute)
    }

    constructor(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int, second: Int) : this() {
        clear()
        set(year, month, dayOfMonth, hourOfDay, minute, second)
    }

    fun isLeapYear(year: Int): Boolean = Year.isLeap(year.toLong())

    companion object {
        const val BC = 0
        const val AD = 1
    }
}
