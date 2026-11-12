package java.time

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalUnit
import java.util.Locale

/** Date-based ChronoField values for a date, or null when the field is not date-based. */
internal fun dateFieldValue(year: Int, month: Int, day: Int, epochDay: Long, field: TemporalField): Long? = when (field) {
    ChronoField.YEAR -> year.toLong()
    ChronoField.YEAR_OF_ERA -> if (year >= 1) year.toLong() else 1L - year
    ChronoField.ERA -> if (year >= 1) 1L else 0L
    ChronoField.MONTH_OF_YEAR -> month.toLong()
    ChronoField.PROLEPTIC_MONTH -> year.toLong() * 12 + month - 1
    ChronoField.DAY_OF_MONTH -> day.toLong()
    ChronoField.DAY_OF_YEAR -> (daysFromCivil(year.toLong(), month, day) - daysFromCivil(year.toLong(), 1, 1) + 1)
    ChronoField.DAY_OF_WEEK -> floorMod(epochDay + 3, 7) + 1
    ChronoField.EPOCH_DAY -> epochDay
    ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH -> ((day - 1) % 7 + 1).toLong()
    ChronoField.ALIGNED_WEEK_OF_MONTH -> ((day - 1) / 7 + 1).toLong()
    ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR, ChronoField.ALIGNED_WEEK_OF_YEAR -> {
        val doy = (daysFromCivil(year.toLong(), month, day) - daysFromCivil(year.toLong(), 1, 1)).toInt()
        if (field == ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR) (doy % 7 + 1).toLong() else (doy / 7 + 1).toLong()
    }
    else -> null
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1
    fun getValue(): Int = value

    fun length(leapYear: Boolean): Int = if (this == FEBRUARY) (if (leapYear) 29 else 28) else lengthOfMonth(2001, value)
    fun minLength(): Int = length(false)
    fun maxLength(): Int = length(true)
    fun plus(months: Long): Month = of((floorMod(ordinal + months, 12) + 1).toInt())
    fun minus(months: Long): Month = plus(-months)

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val text = IntlBridge.text(locale.toLanguageTag())
        return when (style) {
            TextStyle.FULL -> text.months[ordinal]
            TextStyle.FULL_STANDALONE -> text.monthsStandalone[ordinal]
            TextStyle.SHORT -> text.monthsShort[ordinal]
            TextStyle.SHORT_STANDALONE -> text.monthsShortStandalone[ordinal]
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> text.monthsStandalone[ordinal].take(1)
        }
    }

    companion object {
        fun of(month: Int): Month {
            if (month < 1 || month > 12) throw DateTimeException("Invalid value for MonthOfYear: $month")
            return entries[month - 1]
        }

        fun from(temporal: TemporalAccessor): Month = of(temporal.get(ChronoField.MONTH_OF_YEAR))
    }
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val value: Int get() = ordinal + 1
    fun getValue(): Int = value
    fun plus(days: Long): DayOfWeek = of((floorMod(ordinal + days, 7) + 1).toInt())
    fun minus(days: Long): DayOfWeek = plus(-days)

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val text = IntlBridge.text(locale.toLanguageTag())
        return when (style) {
            TextStyle.FULL, TextStyle.FULL_STANDALONE -> text.days[ordinal]
            TextStyle.SHORT, TextStyle.SHORT_STANDALONE -> text.daysShort[ordinal]
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> text.days[ordinal].take(1)
        }
    }

    companion object {
        fun of(dayOfWeek: Int): DayOfWeek {
            if (dayOfWeek < 1 || dayOfWeek > 7) throw DateTimeException("Invalid value for DayOfWeek: $dayOfWeek")
            return entries[dayOfWeek - 1]
        }

        fun from(temporal: TemporalAccessor): DayOfWeek = of(temporal.get(ChronoField.DAY_OF_WEEK))
    }
}

class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Temporal, Comparable<LocalDate> {
    private val epochDay: Long = daysFromCivil(year.toLong(), monthValue, dayOfMonth)

    val month: Month get() = Month.of(monthValue)
    val dayOfWeek: DayOfWeek get() = DayOfWeek.of((floorMod(epochDay + 3, 7) + 1).toInt())
    val dayOfYear: Int get() = dateFieldValue(year, monthValue, dayOfMonth, epochDay, ChronoField.DAY_OF_YEAR)!!.toInt()

    fun getYear(): Int = year
    fun getMonthValue(): Int = monthValue
    fun getMonth(): Month = month
    fun getDayOfMonth(): Int = dayOfMonth
    fun getDayOfWeek(): DayOfWeek = dayOfWeek
    fun getDayOfYear(): Int = dayOfYear

    fun toEpochDay(): Long = epochDay
    fun isLeapYear(): Boolean = isLeapYear(year.toLong())
    fun lengthOfMonth(): Int = lengthOfMonth(year.toLong(), monthValue)
    fun lengthOfYear(): Int = if (isLeapYear()) 366 else 365

    fun atStartOfDay(): LocalDateTime = LocalDateTime.of(this, LocalTime.MIDNIGHT)
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(atStartOfDay(), zone)
    fun atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)
    fun atTime(hour: Int, minute: Int): LocalDateTime = atTime(LocalTime.of(hour, minute))
    fun atTime(hour: Int, minute: Int, second: Int): LocalDateTime = atTime(LocalTime.of(hour, minute, second))
    fun atTime(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalDateTime = atTime(LocalTime.of(hour, minute, second, nanoOfSecond))

    fun plusDays(days: Long): LocalDate = if (days == 0L) this else ofEpochDay(epochDay + days)
    fun plusWeeks(weeks: Long): LocalDate = plusDays(weeks * 7)
    fun plusMonths(months: Long): LocalDate {
        if (months == 0L) return this
        val calc = year.toLong() * 12 + (monthValue - 1) + months
        val newYear = floorDiv(calc, 12)
        val newMonth = (floorMod(calc, 12) + 1).toInt()
        return resolvePreviousValid(newYear.toInt(), newMonth, dayOfMonth)
    }
    fun plusYears(years: Long): LocalDate = if (years == 0L) this else resolvePreviousValid((year + years).toInt(), monthValue, dayOfMonth)
    fun minusDays(days: Long): LocalDate = plusDays(-days)
    fun minusWeeks(weeks: Long): LocalDate = plusWeeks(-weeks)
    fun minusMonths(months: Long): LocalDate = plusMonths(-months)
    fun minusYears(years: Long): LocalDate = plusYears(-years)

    override fun plus(amountToAdd: Long, unit: TemporalUnit): LocalDate = when (unit) {
        ChronoUnit.DAYS -> plusDays(amountToAdd)
        ChronoUnit.WEEKS -> plusWeeks(amountToAdd)
        ChronoUnit.MONTHS -> plusMonths(amountToAdd)
        ChronoUnit.YEARS -> plusYears(amountToAdd)
        ChronoUnit.DECADES -> plusYears(amountToAdd * 10)
        ChronoUnit.CENTURIES -> plusYears(amountToAdd * 100)
        ChronoUnit.MILLENNIA -> plusYears(amountToAdd * 1000)
        else -> throw DateTimeException("Unsupported unit: $unit")
    }

    override fun minus(amountToSubtract: Long, unit: TemporalUnit): LocalDate = plus(-amountToSubtract, unit)

    override fun until(endExclusive: Temporal, unit: TemporalUnit): Long {
        val end = from(endExclusive)
        return when (unit) {
            ChronoUnit.DAYS -> end.epochDay - epochDay
            ChronoUnit.WEEKS -> (end.epochDay - epochDay) / 7
            ChronoUnit.MONTHS -> monthsUntil(end)
            ChronoUnit.YEARS -> monthsUntil(end) / 12
            ChronoUnit.DECADES -> monthsUntil(end) / 120
            ChronoUnit.CENTURIES -> monthsUntil(end) / 1200
            ChronoUnit.MILLENNIA -> monthsUntil(end) / 12000
            else -> throw DateTimeException("Unsupported unit: $unit")
        }
    }

    private fun monthsUntil(end: LocalDate): Long {
        val packed1 = (year.toLong() * 12 + monthValue - 1) * 32 + dayOfMonth
        val packed2 = (end.year.toLong() * 12 + end.monthValue - 1) * 32 + end.dayOfMonth
        return (packed2 - packed1) / 32
    }

    fun withYear(year: Int): LocalDate = resolvePreviousValid(year, monthValue, dayOfMonth)
    fun withMonth(month: Int): LocalDate = resolvePreviousValid(year, month, dayOfMonth)
    fun withDayOfMonth(dayOfMonth: Int): LocalDate = of(year, monthValue, dayOfMonth)
    fun withDayOfYear(dayOfYear: Int): LocalDate = ofYearDay(year, dayOfYear)

    fun isAfter(other: LocalDate): Boolean = epochDay > other.epochDay
    fun isBefore(other: LocalDate): Boolean = epochDay < other.epochDay
    fun isEqual(other: LocalDate): Boolean = epochDay == other.epochDay
    override fun compareTo(other: LocalDate): Int = epochDay.compareTo(other.epochDay)

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && field.isDateBased
    override fun getLong(field: TemporalField): Long =
        dateFieldValue(year, monthValue, dayOfMonth, epochDay, field) ?: throw DateTimeException("Unsupported field: $field")

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun equals(other: Any?): Boolean = other is LocalDate && other.epochDay == epochDay
    override fun hashCode(): Int = epochDay.hashCode()
    override fun toString(): String {
        val y = if (year < 0 || year > 9999) (if (year > 0) "+" else "") + year.toString() else padNumber(year.toLong(), 4)
        return "$y-${padNumber(monthValue.toLong(), 2)}-${padNumber(dayOfMonth.toLong(), 2)}"
    }

    companion object {
        val MIN: LocalDate = LocalDate(-999999999, 1, 1)
        val MAX: LocalDate = LocalDate(999999999, 12, 31)
        val EPOCH: LocalDate = LocalDate(1970, 1, 1)

        private fun resolvePreviousValid(year: Int, month: Int, day: Int): LocalDate =
            LocalDate(year, month, minOf(day, lengthOfMonth(year.toLong(), month)))

        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate {
            ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
            ChronoField.DAY_OF_MONTH.checkValidValue(dayOfMonth.toLong())
            if (dayOfMonth > lengthOfMonth(year.toLong(), month)) {
                throw DateTimeException("Invalid date '${Month.of(month)} $dayOfMonth'")
            }
            return LocalDate(year, month, dayOfMonth)
        }

        fun of(year: Int, month: Month, dayOfMonth: Int): LocalDate = of(year, month.value, dayOfMonth)

        fun ofEpochDay(epochDay: Long): LocalDate {
            val c = civilFromDays(epochDay)
            return LocalDate(c.year.toInt(), c.month, c.day)
        }

        fun ofYearDay(year: Int, dayOfYear: Int): LocalDate = ofEpochDay(daysFromCivil(year.toLong(), 1, 1) + dayOfYear - 1)

        fun ofInstant(instant: Instant, zone: ZoneId): LocalDate = ZonedDateTime.ofInstant(instant, zone).toLocalDate()

        fun now(): LocalDate = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDate = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): LocalDate = when (temporal) {
            is LocalDate -> temporal
            is LocalDateTime -> temporal.toLocalDate()
            is ZonedDateTime -> temporal.toLocalDate()
            is OffsetDateTime -> temporal.toLocalDate()
            else -> {
                if (temporal.isSupported(ChronoField.EPOCH_DAY)) {
                    ofEpochDay(temporal.getLong(ChronoField.EPOCH_DAY))
                } else {
                    throw DateTimeException("Unable to obtain LocalDate from TemporalAccessor: $temporal")
                }
            }
        }

        fun parse(text: CharSequence?): LocalDate = parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalDate = formatter.parse(text!!, ::from)
    }
}

class Year private constructor(val value: Int) : Comparable<Year> {
    fun getValue(): Int = value
    fun isLeap(): Boolean = isLeapYear(value.toLong())
    fun length(): Int = if (isLeap()) 366 else 365
    fun atDay(dayOfYear: Int): LocalDate = LocalDate.ofYearDay(value, dayOfYear)
    fun atMonth(month: Int): YearMonth = YearMonth.of(value, month)
    fun plusYears(years: Long): Year = Year((value + years).toInt())
    fun minusYears(years: Long): Year = plusYears(-years)
    fun isAfter(other: Year): Boolean = value > other.value
    fun isBefore(other: Year): Boolean = value < other.value
    override fun compareTo(other: Year): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Year && other.value == value
    override fun hashCode(): Int = value
    override fun toString(): String = value.toString()

    companion object {
        fun of(isoYear: Int): Year = Year(isoYear)
        fun now(): Year = Year(LocalDate.now().year)
        fun now(zone: ZoneId): Year = Year(LocalDate.now(zone).year)
        fun from(temporal: TemporalAccessor): Year = Year(temporal.get(ChronoField.YEAR))
        fun isLeap(year: Long): Boolean = isLeapYear(year)
        fun parse(text: CharSequence): Year = Year(text.toString().trim().toInt())
    }
}

class YearMonth private constructor(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    val month: Month get() = Month.of(monthValue)
    fun getYear(): Int = year
    fun getMonthValue(): Int = monthValue
    fun getMonth(): Month = month
    fun lengthOfMonth(): Int = lengthOfMonth(year.toLong(), monthValue)
    fun isLeapYear(): Boolean = isLeapYear(year.toLong())
    fun atDay(dayOfMonth: Int): LocalDate = LocalDate.of(year, monthValue, dayOfMonth)
    fun atEndOfMonth(): LocalDate = LocalDate.of(year, monthValue, lengthOfMonth())
    fun isValidDay(dayOfMonth: Int): Boolean = dayOfMonth in 1..lengthOfMonth()

    private val packed: Long get() = year.toLong() * 12 + monthValue - 1

    fun plusMonths(months: Long): YearMonth {
        val calc = packed + months
        return YearMonth(floorDiv(calc, 12).toInt(), (floorMod(calc, 12) + 1).toInt())
    }
    fun plusYears(years: Long): YearMonth = YearMonth((year + years).toInt(), monthValue)
    fun minusMonths(months: Long): YearMonth = plusMonths(-months)
    fun minusYears(years: Long): YearMonth = plusYears(-years)
    fun withMonth(month: Int): YearMonth = of(year, month)
    fun withYear(year: Int): YearMonth = of(year, monthValue)

    fun isAfter(other: YearMonth): Boolean = packed > other.packed
    fun isBefore(other: YearMonth): Boolean = packed < other.packed
    override fun compareTo(other: YearMonth): Int = packed.compareTo(other.packed)
    override fun equals(other: Any?): Boolean = other is YearMonth && other.packed == packed
    override fun hashCode(): Int = packed.hashCode()
    override fun toString(): String = "${padNumber(year.toLong(), 4)}-${padNumber(monthValue.toLong(), 2)}"
    fun format(formatter: DateTimeFormatter): String = formatter.format(atDay(1))

    companion object {
        fun of(year: Int, month: Int): YearMonth {
            ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
            return YearMonth(year, month)
        }
        fun of(year: Int, month: Month): YearMonth = YearMonth(year, month.value)
        fun now(): YearMonth = from(LocalDate.now())
        fun now(zone: ZoneId): YearMonth = from(LocalDate.now(zone))
        fun from(temporal: TemporalAccessor): YearMonth =
            YearMonth(temporal.get(ChronoField.YEAR), temporal.get(ChronoField.MONTH_OF_YEAR))
        fun parse(text: CharSequence?): YearMonth = parse(text, DateTimeFormatter.ofPattern("uuuu-MM"))
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): YearMonth = formatter.parse(text!!, ::from)
    }
}

class MonthDay private constructor(val monthValue: Int, val dayOfMonth: Int) : Comparable<MonthDay> {
    val month: Month get() = Month.of(monthValue)
    fun getMonthValue(): Int = monthValue
    fun getMonth(): Month = month
    fun getDayOfMonth(): Int = dayOfMonth
    fun atYear(year: Int): LocalDate = LocalDate.of(year, monthValue, minOf(dayOfMonth, lengthOfMonth(year.toLong(), monthValue)))
    fun isValidYear(year: Int): Boolean = dayOfMonth <= lengthOfMonth(year.toLong(), monthValue)
    fun isAfter(other: MonthDay): Boolean = compareTo(other) > 0
    fun isBefore(other: MonthDay): Boolean = compareTo(other) < 0
    override fun compareTo(other: MonthDay): Int = (monthValue * 32 + dayOfMonth).compareTo(other.monthValue * 32 + other.dayOfMonth)
    override fun equals(other: Any?): Boolean = other is MonthDay && other.monthValue == monthValue && other.dayOfMonth == dayOfMonth
    override fun hashCode(): Int = monthValue * 32 + dayOfMonth
    override fun toString(): String = "--${padNumber(monthValue.toLong(), 2)}-${padNumber(dayOfMonth.toLong(), 2)}"

    companion object {
        fun of(month: Int, dayOfMonth: Int): MonthDay {
            ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
            if (dayOfMonth < 1 || dayOfMonth > Month.of(month).maxLength()) throw DateTimeException("Illegal value for DayOfMonth field, value $dayOfMonth is not valid for month ${Month.of(month)}")
            return MonthDay(month, dayOfMonth)
        }
        fun of(month: Month, dayOfMonth: Int): MonthDay = of(month.value, dayOfMonth)
        fun now(): MonthDay = from(LocalDate.now())
        fun now(zone: ZoneId): MonthDay = from(LocalDate.now(zone))
        fun from(temporal: TemporalAccessor): MonthDay = MonthDay(temporal.get(ChronoField.MONTH_OF_YEAR), temporal.get(ChronoField.DAY_OF_MONTH))
        fun parse(text: CharSequence?): MonthDay = parse(text, DateTimeFormatter.ofPattern("--MM-dd"))
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): MonthDay = formatter.parse(text!!, ::from)
    }
}
