package java.text

import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.SignStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.TemporalField
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ParseException(message: String, val errorOffset: Int = 0) : Exception(message) {
    fun getErrorOffset(): Int = errorOffset
}

class ParsePosition(var index: Int) {
    var errorIndex: Int = -1

    fun getIndex(): Int = index
    fun setIndex(index: Int) {
        this.index = index
    }

    fun getErrorIndex(): Int = errorIndex
    fun setErrorIndex(errorIndex: Int) {
        this.errorIndex = errorIndex
    }

    override fun toString(): String = "java.text.ParsePosition[index=$index,errorIndex=$errorIndex]"
}

abstract class DateFormat {
    var timeZone: TimeZone = TimeZone.getDefault()
    var isLenient: Boolean = true

    /** A calendar in this format's zone; extensions read the zone or the current time off it. */
    val calendar: Calendar get() = Calendar.getInstance(timeZone)

    fun getTimeZone(): TimeZone = timeZone
    fun setTimeZone(zone: TimeZone) {
        timeZone = zone
    }

    fun setLenient(lenient: Boolean) {
        isLenient = lenient
    }

    fun getCalendar(): Calendar = calendar

    /** Parses from [pos]; on failure returns null with [ParsePosition.errorIndex] set. Trailing text is ignored. */
    abstract fun parse(source: String, pos: ParsePosition): Date?

    abstract fun format(date: Date): String

    fun parse(source: String): Date {
        val pos = ParsePosition(0)
        return parse(source, pos) ?: throw ParseException("Unparseable date: \"$source\"", pos.errorIndex)
    }

    fun format(date: Long): String = format(Date(date))

    companion object {
        const val FULL = 0
        const val LONG = 1
        const val MEDIUM = 2
        const val SHORT = 3
        const val DEFAULT = MEDIUM
    }
}

/**
 * SimpleDateFormat on the java.time formatter engine in lenient mode: digits of any count, blanks
 * skipped before fields, out-of-range values rolled over and missing fields taken from the epoch.
 *
 * Differences from the JVM: `w`, `W` and `F` are aligned (every week starts on the 1st) rather than
 * locale weeks; `isLenient = false` rejects out-of-range fields but still ignores trailing text in
 * [parse]; two-digit years resolve to the 80 years before and 20 after today.
 */
open class SimpleDateFormat(pattern: String, private val locale: Locale = Locale.getDefault()) : DateFormat() {
    private var pattern: String = pattern
    private var formatter: DateTimeFormatter = build(pattern, locale)

    fun toPattern(): String = pattern

    fun applyPattern(pattern: String) {
        this.pattern = pattern
        formatter = build(pattern, locale)
    }

    fun set2DigitYearStart(startDate: Date) {
        // Two-digit years always resolve within 80 years before today, like the JVM default.
    }

    fun get2DigitYearStart(): Date = Date(Date.currentTimeMillis() - 80L * 365 * 24 * 3600 * 1000)

    override fun parse(source: String, pos: ParsePosition): Date? {
        val start = pos.index
        if (start < 0 || start > source.length) {
            pos.errorIndex = start
            return null
        }
        val parsed = try {
            formatter.withZone(timeZone.toZoneId()).parseAt(source, start, requireAll = false)
        } catch (e: DateTimeParseException) {
            pos.errorIndex = e.getErrorIndex()
            return null
        }
        if (!isLenient) {
            for ((field, value) in parsed.fields) {
                if (field is ChronoField && runCatching { field.checkValidValue(value) }.isFailure) {
                    pos.errorIndex = start
                    return null
                }
            }
        }
        val millis = try {
            parsed.getLong(ChronoField.INSTANT_SECONDS) * 1000 + parsed.getLong(ChronoField.NANO_OF_SECOND) / 1_000_000
        } catch (e: DateTimeException) {
            pos.errorIndex = start
            return null
        }
        pos.index = parsed.endIndex
        return Date(millis)
    }

    override fun format(date: Date): String = formatter.withZone(timeZone.toZoneId()).format(Instant.ofEpochMilli(date.time))

    fun clone(): SimpleDateFormat = SimpleDateFormat(pattern, locale).also {
        it.timeZone = timeZone
        it.isLenient = isLenient
    }

    override fun equals(other: Any?): Boolean = other is SimpleDateFormat && other.pattern == pattern && other.locale == locale && other.timeZone == timeZone
    override fun hashCode(): Int = pattern.hashCode() xor locale.hashCode()
    override fun toString(): String = "SimpleDateFormat($pattern)"

    private companion object {
        fun build(pattern: String, locale: Locale): DateTimeFormatter {
            val b = DateTimeFormatterBuilder()
            var pos = 0
            while (pos < pattern.length) {
                val c = pattern[pos]
                when {
                    c == '\'' -> {
                        if (pos + 1 < pattern.length && pattern[pos + 1] == '\'') {
                            b.appendLiteral('\'')
                            pos += 2
                        } else {
                            val sb = StringBuilder()
                            pos++
                            var closed = false
                            while (pos < pattern.length) {
                                if (pattern[pos] == '\'') {
                                    if (pos + 1 < pattern.length && pattern[pos + 1] == '\'') {
                                        sb.append('\'')
                                        pos += 2
                                    } else {
                                        pos++
                                        closed = true
                                        break
                                    }
                                } else {
                                    sb.append(pattern[pos])
                                    pos++
                                }
                            }
                            if (!closed) throw IllegalArgumentException("Unterminated quote")
                            b.appendLiteral(sb.toString())
                        }
                    }
                    c in 'A'..'Z' || c in 'a'..'z' -> {
                        val start = pos
                        while (pos < pattern.length && pattern[pos] == c) pos++
                        letter(b, c, pos - start)
                    }
                    else -> {
                        b.appendLiteral(c)
                        pos++
                    }
                }
            }
            return b.toFormatter(locale).withLenient(true)
        }

        private fun number(b: DateTimeFormatterBuilder, field: TemporalField, count: Int) {
            when (count) {
                1 -> b.appendValue(field)
                2 -> b.appendValue(field, 2)
                else -> b.appendValue(field, count, 19, SignStyle.NOT_NEGATIVE)
            }
        }

        private fun letter(b: DateTimeFormatterBuilder, c: Char, count: Int) {
            when (c) {
                'G' -> b.appendText(ChronoField.ERA, TextStyle.SHORT)
                'y', 'Y' -> if (count == 2) b.appendValueReduced(ChronoField.YEAR, 2, 2, 2000) else b.appendValue(ChronoField.YEAR, count, 19, SignStyle.NORMAL)
                'M', 'L' -> when {
                    count <= 2 -> number(b, ChronoField.MONTH_OF_YEAR, count)
                    count == 3 -> b.appendText(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.SHORT_STANDALONE else TextStyle.SHORT)
                    else -> b.appendText(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.FULL_STANDALONE else TextStyle.FULL)
                }
                'w' -> number(b, ChronoField.ALIGNED_WEEK_OF_YEAR, count)
                'W' -> number(b, ChronoField.ALIGNED_WEEK_OF_MONTH, count)
                'D' -> number(b, ChronoField.DAY_OF_YEAR, count)
                'd' -> number(b, ChronoField.DAY_OF_MONTH, count)
                'F' -> number(b, ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH, count)
                'E' -> b.appendText(ChronoField.DAY_OF_WEEK, if (count < 4) TextStyle.SHORT else TextStyle.FULL)
                'u' -> number(b, ChronoField.DAY_OF_WEEK, count)
                'a' -> b.appendText(ChronoField.AMPM_OF_DAY, TextStyle.SHORT)
                'H' -> number(b, ChronoField.HOUR_OF_DAY, count)
                'k' -> number(b, ChronoField.CLOCK_HOUR_OF_DAY, count)
                'K' -> number(b, ChronoField.HOUR_OF_AMPM, count)
                'h' -> number(b, ChronoField.CLOCK_HOUR_OF_AMPM, count)
                'm' -> number(b, ChronoField.MINUTE_OF_HOUR, count)
                's' -> number(b, ChronoField.SECOND_OF_MINUTE, count)
                'S' -> number(b, ChronoField.MILLI_OF_SECOND, count)
                'z' -> b.appendZoneText(if (count < 4) TextStyle.SHORT else TextStyle.FULL)
                'Z' -> b.appendOffset("+HHMM", "+0000")
                'X' -> when (count) {
                    1 -> b.appendOffset("+HH", "Z")
                    2 -> b.appendOffset("+HHMM", "Z")
                    3 -> b.appendOffset("+HH:MM", "Z")
                    else -> throw IllegalArgumentException("invalid ISO 8601 format: length=$count")
                }
                else -> throw IllegalArgumentException("Illegal pattern character '$c'")
            }
        }
    }
}
