package java.time.format

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.IntlBridge
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.floorMod
import java.time.padNumber
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.TemporalQuery
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * DateTimeFormatter on a small token engine shared with SimpleDateFormat.
 *
 * Patterns follow the java.time letters (G u y Y M L d D E e c a H k K h m s S A n N VV v z O X x Z,
 * '…' literals and […] optional sections). Text matching is always case-insensitive and accepts
 * both the long and the short name of a locale (plus the English ones), which is a superset of
 * Java's behaviour that only ever makes a scraper's parse succeed where Java's would too.
 *
 * Strict (DateTimeFormatter) parsing consumes the whole text and resolves only fields that were
 * actually parsed, like Java; lenient (SimpleDateFormat) parsing accepts any digit count, stops
 * at the first unparsed character and fills missing fields from the epoch.
 */

enum class TextStyle {
    FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE;

    fun isStandalone(): Boolean = ordinal % 2 == 1
    fun asStandalone(): TextStyle = entries[ordinal or 1]
    fun asNormal(): TextStyle = entries[ordinal and 1.inv()]
}

enum class SignStyle { NORMAL, ALWAYS, NEVER, NOT_NEGATIVE, EXCEEDS_PAD }

enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }

enum class ResolverStyle { STRICT, SMART, LENIENT }

class DateTimeParseException(
    message: String,
    private val parsedString: CharSequence,
    private val errorIndex: Int,
    cause: Throwable? = null,
) : DateTimeException(message, cause) {
    fun getParsedString(): String = parsedString.toString()
    fun getErrorIndex(): Int = errorIndex
}

// ---------------------------------------------------------------------------------------------
// Parse state

internal class ParseContext(val text: String, val lenient: Boolean) {
    var fields: HashMap<TemporalField, Long> = HashMap()
    var zone: ZoneId? = null
    var errorIndex: Int = 0

    fun fail(pos: Int): Int {
        if (pos > errorIndex) errorIndex = pos
        return -1
    }

    fun snapshot(): Pair<HashMap<TemporalField, Long>, ZoneId?> = Pair(HashMap(fields), zone)

    fun restore(state: Pair<HashMap<TemporalField, Long>, ZoneId?>) {
        fields = state.first
        zone = state.second
    }

    /** SimpleDateFormat skips blanks in front of every field. */
    fun skipBlanks(pos: Int): Int {
        var p = pos
        if (lenient) while (p < text.length && (text[p] == ' ' || text[p] == '\t')) p++
        return p
    }
}

internal fun parseTokens(tokens: List<Token>, formatter: DateTimeFormatter, ctx: ParseContext, start: Int): Int {
    var pos = start
    for (i in tokens.indices) {
        val token = tokens[i]
        // Adjacent value parsing: a variable-width number leaves the digits that the fixed-width
        // numbers right after it need (yyyyMMdd).
        var reserved = 0
        if (token is Num && token.minWidth != token.maxWidth) {
            var j = i + 1
            while (j < tokens.size && tokens[j].fixedWidth > 0) {
                reserved += tokens[j].fixedWidth
                j++
            }
        }
        pos = token.parse(formatter, ctx, pos, reserved)
        if (pos < 0) return -1
    }
    return pos
}

internal fun formatTokens(tokens: List<Token>, formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
    for (token in tokens) token.format(formatter, temporal, sb)
}

private fun fieldValue(temporal: TemporalAccessor, field: TemporalField): Long {
    if (field is ChronoField) {
        if (temporal.isSupported(field)) return temporal.getLong(field)
    } else if (field.isSupportedBy(temporal)) {
        return field.getFrom(temporal)
    }
    throw DateTimeException("Unsupported field: $field")
}

private fun fieldOrNull(temporal: TemporalAccessor, field: ChronoField): Long? =
    if (temporal.isSupported(field)) temporal.getLong(field) else null

private const val ZONE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789/_+-:~."

private fun zoneRun(text: String, pos: Int): String {
    var end = pos
    while (end < text.length && ZONE_CHARS.indexOf(text[end]) >= 0) end++
    return text.substring(pos, end)
}

// ---------------------------------------------------------------------------------------------
// Tokens

internal abstract class Token {
    /** Appends the field to [sb]; throws DateTimeException when the temporal lacks it. */
    abstract fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder)

    /** Returns the position after the token, or -1 (with [ParseContext.errorIndex] set) on failure. */
    abstract fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int

    /** Digit count for fixed-width numbers, used by adjacent value parsing; -1 otherwise. */
    open val fixedWidth: Int get() = -1
}

internal class Lit(private val text: String) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        sb.append(text)
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        if (ctx.text.startsWith(text, pos, ignoreCase = true)) return pos + text.length
        // Lenient parsing lets blanks around a literal vary ("Jan 5,2024" vs "Jan 5, 2024").
        if (ctx.lenient && text.isNotBlank()) {
            val trimmed = text.trim()
            val p = ctx.skipBlanks(pos)
            if (ctx.text.startsWith(trimmed, p, ignoreCase = true)) return ctx.skipBlanks(p + trimmed.length)
        }
        return ctx.fail(pos)
    }
}

internal class Num(
    val field: TemporalField,
    val minWidth: Int,
    val maxWidth: Int,
    private val sign: SignStyle,
    /** Base value for two-digit years (`yy`), or -1 for a plain number. */
    private val reducedBase: Int = -1,
) : Token() {
    override val fixedWidth: Int get() = if (minWidth == maxWidth) minWidth else -1

    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        var value = fieldValue(temporal, field)
        if (reducedBase >= 0) value = floorMod(value, pow10(minWidth))
        val negative = value < 0
        var digits = (if (negative) -value else value).toString()
        if (digits.length > maxWidth) {
            throw DateTimeException("Field $field cannot be printed as the value $value exceeds the maximum print width of $maxWidth")
        }
        digits = digits.padStart(minWidth, '0')
        when (sign) {
            SignStyle.NORMAL -> if (negative) sb.append('-')
            SignStyle.ALWAYS -> sb.append(if (negative) '-' else '+')
            SignStyle.NEVER -> {}
            SignStyle.NOT_NEGATIVE -> if (negative) throw DateTimeException("Field $field cannot be printed as the value $value cannot be negative according to the SignStyle")
            SignStyle.EXCEEDS_PAD -> if (negative) sb.append('-') else if (digits.length > minWidth) sb.append('+')
        }
        sb.append(digits)
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val text = ctx.text
        var p = ctx.skipBlanks(pos)
        var negative = false
        if (p < text.length && (text[p] == '+' || text[p] == '-')) {
            val signAllowed = ctx.lenient || (sign != SignStyle.NEVER && sign != SignStyle.NOT_NEGATIVE)
            if (!signAllowed) return ctx.fail(p)
            negative = text[p] == '-'
            p++
        }
        var run = 0
        while (p + run < text.length && text[p + run].isDigit()) run++
        val min = if (ctx.lenient) 1 else minWidth
        val max = if (ctx.lenient) maxOf(maxWidth, 18) else maxWidth
        var take = run - reserved
        if (take > max) take = max
        if (take < min) return ctx.fail(p)
        var value = text.substring(p, p + take).toLong()
        if (reducedBase >= 0 && take == minWidth) value = reduced(value, ctx.lenient)
        if (negative) value = -value
        ctx.fields[field] = value
        return p + take
    }

    private fun reduced(value: Long, lenient: Boolean): Long {
        val range = pow10(minWidth)
        if (lenient) {
            // SimpleDateFormat: within 80 years before and 20 after today.
            val pivot = LocalDate.now().year.toLong() - 80
            var year = pivot - floorMod(pivot, range) + value
            if (year < pivot) year += range
            return year
        }
        val base = reducedBase.toLong()
        var year = base - floorMod(base, range) + value
        if (year < base) year += range
        return year
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}

internal class Fraction(private val minWidth: Int, private val maxWidth: Int, private val decimalPoint: Boolean) : Token() {
    override val fixedWidth: Int get() = if (minWidth == maxWidth && !decimalPoint) minWidth else -1

    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val nanos = fieldValue(temporal, ChronoField.NANO_OF_SECOND)
        var digits = padNumber(nanos, 9).take(maxWidth)
        while (digits.length > minWidth && digits.endsWith('0')) digits = digits.dropLast(1)
        if (digits.isEmpty()) return
        if (decimalPoint) sb.append('.')
        sb.append(digits)
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val text = ctx.text
        var p = pos
        if (decimalPoint) {
            if (p < text.length && (text[p] == '.' || text[p] == ',')) p++ else return if (minWidth == 0) pos else ctx.fail(p)
        }
        var run = 0
        while (p + run < text.length && text[p + run].isDigit()) run++
        val take = if (ctx.lenient) run else minOf(run, maxWidth)
        if (take < minWidth || (decimalPoint && take == 0 && minWidth > 0)) return ctx.fail(p)
        val digits = text.substring(p, p + take).take(9).padEnd(9, '0')
        ctx.fields[ChronoField.NANO_OF_SECOND] = digits.toLong()
        return p + take
    }
}

internal class Text(private val field: ChronoField, private val style: TextStyle) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val value = fieldValue(temporal, field)
        sb.append(TextNames.name(field, value, style, formatter.locale) ?: value.toString())
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val p = ctx.skipBlanks(pos)
        for ((name, value) in TextNames.candidates(field, style, formatter.locale)) {
            if (ctx.text.startsWith(name, p, ignoreCase = true)) {
                ctx.fields[field] = value
                return p + name.length
            }
        }
        // Lenient numbers for a text field ("MMM" against "01").
        if (ctx.lenient) {
            var run = 0
            while (p + run < ctx.text.length && ctx.text[p + run].isDigit()) run++
            if (run in 1..2) {
                ctx.fields[field] = ctx.text.substring(p, p + run).toLong()
                return p + run
            }
        }
        return ctx.fail(p)
    }
}

/** `appendText(field, Map)`: caller-supplied names. */
internal class TextMap(private val field: TemporalField, private val names: Map<Long, String>) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val value = fieldValue(temporal, field)
        sb.append(names[value] ?: value.toString())
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val p = ctx.skipBlanks(pos)
        for ((value, name) in names.entries.sortedByDescending { it.value.length }) {
            if (ctx.text.startsWith(name, p, ignoreCase = true)) {
                ctx.fields[field] = value
                return p + name.length
            }
        }
        return ctx.fail(p)
    }
}

/** Pattern letters `e` / `c`: locale-relative day-of-week number (1-2 letters) or name (3+). */
internal class LocalizedDow(private val count: Int, private val standalone: Boolean) : Token() {
    private fun delegate(locale: Locale): Token = when (count) {
        1, 2 -> Num(WeekFields.of(locale).dayOfWeek(), count, count, SignStyle.NOT_NEGATIVE)
        3 -> Text(ChronoField.DAY_OF_WEEK, if (standalone) TextStyle.SHORT_STANDALONE else TextStyle.SHORT)
        4 -> Text(ChronoField.DAY_OF_WEEK, if (standalone) TextStyle.FULL_STANDALONE else TextStyle.FULL)
        else -> Text(ChronoField.DAY_OF_WEEK, if (standalone) TextStyle.NARROW_STANDALONE else TextStyle.NARROW)
    }

    override val fixedWidth: Int get() = if (count <= 2) count else -1

    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) =
        delegate(formatter.locale).format(formatter, temporal, sb)

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int =
        delegate(formatter.locale).parse(formatter, ctx, pos, reserved)
}

/**
 * Offsets with Java's pattern grammar: `+HH`, `+HHmm`, `+HH:mm`, `+HHMM`, `+HH:MM`, `+HHMMss`,
 * `+HH:MM:ss`, `+HHMMSS`, `+HH:MM:SS` (lower case parts print only when non-zero).
 */
internal class Offset(private val pattern: String, private val noOffsetText: String) : Token() {
    private val colon = pattern.contains(':')
    private val minutesAlways = pattern.contains("MM")
    private val minutesOptional = pattern.contains("mm")
    private val secondsAlways = pattern.contains("SS")
    private val secondsOptional = pattern.contains("ss")

    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val total = offsetSeconds(formatter, temporal)
        if (total == 0L) {
            sb.append(noOffsetText)
            return
        }
        val abs = if (total < 0) -total else total
        val h = abs / 3600
        val m = (abs / 60) % 60
        val s = abs % 60
        sb.append(if (total < 0) '-' else '+').append(padNumber(h, 2))
        if (minutesAlways || (minutesOptional && (m != 0L || s != 0L))) {
            if (colon) sb.append(':')
            sb.append(padNumber(m, 2))
            if (secondsAlways || (secondsOptional && s != 0L)) {
                if (colon) sb.append(':')
                sb.append(padNumber(s, 2))
            }
        }
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val p = ctx.skipBlanks(pos)
        if (noOffsetText.isNotEmpty() && ctx.text.startsWith(noOffsetText, p, ignoreCase = true)) {
            ctx.fields[ChronoField.OFFSET_SECONDS] = 0
            return p + noOffsetText.length
        }
        for (zero in arrayOf("Z", "UTC", "GMT")) {
            if (ctx.text.startsWith(zero, p, ignoreCase = true) && !followedByOffset(ctx.text, p + zero.length)) {
                ctx.fields[ChronoField.OFFSET_SECONDS] = 0
                return p + zero.length
            }
        }
        val end = parseOffset(ctx.text, p) ?: return ctx.fail(p)
        ctx.fields[ChronoField.OFFSET_SECONDS] = end.first
        return end.second
    }

    companion object {
        fun followedByOffset(text: String, pos: Int): Boolean = pos < text.length && (text[pos] == '+' || text[pos] == '-')

        /** `±h`, `±hh`, `±hhmm`, `±hh:mm`, `±hhmmss`, `±hh:mm:ss` → (seconds, end). */
        fun parseOffset(text: String, pos: Int): Pair<Long, Int>? {
            var p = pos
            if (p >= text.length || (text[p] != '+' && text[p] != '-')) return null
            val negative = text[p] == '-'
            p++
            fun digits(max: Int): Int? {
                var n = 0
                while (n < max && p + n < text.length && text[p + n].isDigit()) n++
                if (n == 0) return null
                val v = text.substring(p, p + n).toInt()
                p += n
                return v
            }
            val h = digits(2) ?: return null
            var m = 0
            var s = 0
            var save = p
            if (p < text.length && text[p] == ':') p++
            val mm = if (p + 1 < text.length && text[p].isDigit() && text[p + 1].isDigit()) digits(2) else null
            if (mm == null) {
                p = save
            } else {
                m = mm
                save = p
                if (p < text.length && text[p] == ':') p++
                val ss = if (p + 1 < text.length && text[p].isDigit() && text[p + 1].isDigit()) digits(2) else null
                if (ss == null) p = save else s = ss
            }
            if (h > 18 || m > 59 || s > 59) return null
            val total = (h * 3600 + m * 60 + s).toLong()
            return Pair(if (negative) -total else total, p)
        }

        fun offsetSeconds(formatter: DateTimeFormatter, temporal: TemporalAccessor): Long {
            fieldOrNull(temporal, ChronoField.OFFSET_SECONDS)?.let { return it }
            temporal.offsetOrNull()?.let { return it.totalSeconds.toLong() }
            val zone = temporal.zoneOrNull() ?: formatter.zone
            if (zone != null && temporal.isSupported(ChronoField.EPOCH_DAY) && temporal.isSupported(ChronoField.NANO_OF_DAY)) {
                val local = LocalDateTime.from(temporal).toEpochSecond(ZoneOffset.UTC)
                return zone.offsetForLocal(local).totalSeconds.toLong()
            }
            throw DateTimeException("Unable to extract ZoneOffset from temporal $temporal")
        }
    }
}

/** Pattern letter `O`: `GMT+8` (short) or `GMT+08:00` (full). */
internal class LocalizedOffset(private val style: TextStyle) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val total = Offset.offsetSeconds(formatter, temporal)
        sb.append("GMT")
        if (total == 0L) return
        val abs = if (total < 0) -total else total
        val h = abs / 3600
        val m = (abs / 60) % 60
        val s = abs % 60
        sb.append(if (total < 0) '-' else '+')
        if (style == TextStyle.FULL) {
            sb.append(padNumber(h, 2)).append(':').append(padNumber(m, 2))
            if (s != 0L) sb.append(':').append(padNumber(s, 2))
        } else {
            sb.append(h)
            if (m != 0L || s != 0L) {
                sb.append(':').append(padNumber(m, 2))
                if (s != 0L) sb.append(':').append(padNumber(s, 2))
            }
        }
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        var p = ctx.skipBlanks(pos)
        val prefix = arrayOf("GMT", "UTC", "UT").firstOrNull { ctx.text.startsWith(it, p, ignoreCase = true) } ?: return ctx.fail(p)
        p += prefix.length
        if (!Offset.followedByOffset(ctx.text, p)) {
            ctx.fields[ChronoField.OFFSET_SECONDS] = 0
            return p
        }
        val parsed = Offset.parseOffset(ctx.text, p) ?: return ctx.fail(p)
        ctx.fields[ChronoField.OFFSET_SECONDS] = parsed.first
        return parsed.second
    }
}

/** Pattern letters `z` / `v`: zone names from Intl when printing; abbreviations or ids when parsing. */
internal class ZoneText(private val style: TextStyle) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val zone = temporal.zoneOrNull() ?: formatter.zone ?: temporal.offsetOrNull()
            ?: throw DateTimeException("Unable to extract ZoneId from temporal $temporal")
        if (zone is ZoneOffset) {
            sb.append(zone.id)
            return
        }
        val epochSecond = fieldOrNull(temporal, ChronoField.INSTANT_SECONDS) ?: Instant.now().epochSecond
        val long = style == TextStyle.FULL || style == TextStyle.FULL_STANDALONE
        sb.append(IntlBridge.zoneName(zone.id, epochSecond, long, formatter.locale.toLanguageTag()) ?: zone.id)
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val p = ctx.skipBlanks(pos)
        val run = zoneRun(ctx.text, p)
        if (run.isEmpty()) return ctx.fail(p)
        // Try the longest prefix of the run that names a zone, so "PST," or "UTC." still match.
        var end = run.length
        while (end > 0) {
            val candidate = run.substring(0, end)
            val zone = ZoneNames.resolve(candidate)
            if (zone != null) {
                ctx.zone = zone
                if (zone is ZoneOffset) ctx.fields[ChronoField.OFFSET_SECONDS] = zone.totalSeconds.toLong()
                return p + end
            }
            end--
            while (end > 0 && ZONE_CHARS.indexOf(run[end - 1]) >= 0 && run[end - 1].isLetterOrDigit()) end--
        }
        return ctx.fail(p)
    }
}

/** Pattern letters `VV`: a zone id. */
internal class ZoneIdTok : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val zone = temporal.zoneOrNull() ?: formatter.zone ?: temporal.offsetOrNull()
            ?: throw DateTimeException("Unable to extract ZoneId from temporal $temporal")
        sb.append(zone.id)
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val p = ctx.skipBlanks(pos)
        val run = zoneRun(ctx.text, p)
        if (run.isEmpty()) return ctx.fail(p)
        val zone = ZoneNames.resolve(run) ?: return ctx.fail(p)
        ctx.zone = zone
        if (zone is ZoneOffset) ctx.fields[ChronoField.OFFSET_SECONDS] = zone.totalSeconds.toLong()
        return p + run.length
    }
}

/** ISO_INSTANT: always UTC, fraction printed in groups of three digits. */
internal class InstantTok(private val fractionalDigits: Int = -2) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val seconds = fieldValue(temporal, ChronoField.INSTANT_SECONDS)
        val nanos = fieldOrNull(temporal, ChronoField.NANO_OF_SECOND) ?: 0L
        val ldt = LocalDateTime.ofEpochSecond(seconds, nanos.toInt(), ZoneOffset.UTC)
        sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(ldt)).append('T')
        sb.append(padNumber(ldt.hour.toLong(), 2)).append(':').append(padNumber(ldt.minute.toLong(), 2)).append(':').append(padNumber(ldt.second.toLong(), 2))
        val digits = when {
            fractionalDigits >= 0 -> fractionalDigits
            fractionalDigits == -1 -> padNumber(nanos, 9).trimEnd('0').length
            nanos == 0L -> 0
            nanos % 1_000_000 == 0L -> 3
            nanos % 1000 == 0L -> 6
            else -> 9
        }
        if (digits > 0) sb.append('.').append(padNumber(nanos, 9).take(digits))
        sb.append('Z')
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int =
        parseTokens(DateTimeFormatter.ISO_OFFSET_DATE_TIME.tokens, formatter, ctx, pos)
}

internal class Optional(private val tokens: List<Token>) : Token() {
    override fun format(formatter: DateTimeFormatter, temporal: TemporalAccessor, sb: StringBuilder) {
        val mark = sb.length
        try {
            formatTokens(tokens, formatter, temporal, sb)
        } catch (e: DateTimeException) {
            sb.setLength(mark)
        }
    }

    override fun parse(formatter: DateTimeFormatter, ctx: ParseContext, pos: Int, reserved: Int): Int {
        val state = ctx.snapshot()
        val end = parseTokens(tokens, formatter, ctx, pos)
        if (end < 0) {
            ctx.restore(state)
            return pos
        }
        return end
    }
}

// ---------------------------------------------------------------------------------------------
// Names

internal object TextNames {
    private val cache = HashMap<String, List<Pair<String, Long>>>()

    fun name(field: ChronoField, value: Long, style: TextStyle, locale: Locale): String? = when (field) {
        ChronoField.MONTH_OF_YEAR -> if (value in 1..12) Month.of(value.toInt()).getDisplayName(style, locale) else null
        ChronoField.DAY_OF_WEEK -> if (value in 1..7) DayOfWeek.of(value.toInt()).getDisplayName(style, locale) else null
        ChronoField.AMPM_OF_DAY -> {
            val text = IntlBridge.text(locale.toLanguageTag())
            val full = if (value == 0L) text.am else text.pm
            if (style == TextStyle.NARROW || style == TextStyle.NARROW_STANDALONE) full.take(1) else full
        }
        ChronoField.ERA -> when (style) {
            TextStyle.FULL, TextStyle.FULL_STANDALONE -> if (value == 0L) "Before Christ" else "Anno Domini"
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> if (value == 0L) "B" else "A"
            else -> if (value == 0L) "BC" else "AD"
        }
        else -> null
    }

    /** Names accepted when parsing [field], longest first; both widths, locale and English. */
    fun candidates(field: ChronoField, style: TextStyle, locale: Locale): List<Pair<String, Long>> {
        val key = "${field.name}|${style.name}|${locale.toLanguageTag()}"
        return cache.getOrPut(key) { build(field, style, locale) }
    }

    private fun build(field: ChronoField, style: TextStyle, locale: Locale): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        fun add(name: String?, value: Long) {
            if (name.isNullOrEmpty()) return
            out.add(Pair(name, value))
            if (name.endsWith('.')) out.add(Pair(name.dropLast(1), value))
        }
        val narrow = style == TextStyle.NARROW || style == TextStyle.NARROW_STANDALONE
        val locales = if (locale.getLanguage().isEmpty() || locale.getLanguage() == "en") listOf(locale) else listOf(locale, Locale.ENGLISH)
        val styles = if (narrow) listOf(TextStyle.NARROW) else listOf(TextStyle.FULL, TextStyle.FULL_STANDALONE, TextStyle.SHORT, TextStyle.SHORT_STANDALONE)
        when (field) {
            ChronoField.MONTH_OF_YEAR -> for (loc in locales) for (s in styles) for (m in 1..12) add(Month.of(m).getDisplayName(s, loc), m.toLong())
            ChronoField.DAY_OF_WEEK -> for (loc in locales) for (s in styles) for (d in 1..7) add(DayOfWeek.of(d).getDisplayName(s, loc), d.toLong())
            ChronoField.AMPM_OF_DAY -> {
                for (loc in locales) {
                    val text = IntlBridge.text(loc.toLanguageTag())
                    add(text.am, 0); add(text.pm, 1)
                }
                add("AM", 0); add("PM", 1); add("A.M.", 0); add("P.M.", 1)
                if (narrow) { add("A", 0); add("P", 1) }
            }
            ChronoField.ERA -> {
                add("Anno Domini", 1); add("Before Christ", 0); add("AD", 1); add("BC", 0); add("CE", 1); add("BCE", 0)
                if (narrow) { add("A", 1); add("B", 0) }
            }
            else -> {}
        }
        return out.distinctBy { it.first.lowercase() }.sortedByDescending { it.first.length }
    }
}

internal object ZoneNames {
    private val abbreviations: Map<String, Int> = mapOf(
        "UTC" to 0, "UT" to 0, "GMT" to 0, "Z" to 0, "WET" to 0,
        "EST" to -5, "EDT" to -4, "CST" to -6, "CDT" to -5, "MST" to -7, "MDT" to -6, "PST" to -8, "PDT" to -7,
        "AKST" to -9, "AKDT" to -8, "HST" to -10, "AST" to -4, "ADT" to -3, "NST" to -3, "BRT" to -3, "ART" to -3,
        "CLT" to -4, "COT" to -5, "PET" to -5, "VET" to -4,
        "BST" to 1, "IST" to 5, "CET" to 1, "CEST" to 2, "EET" to 2, "EEST" to 3, "WEST" to 1, "MSK" to 3, "TRT" to 3,
        "SAST" to 2, "EAT" to 3, "WAT" to 1, "CAT" to 2, "GST" to 4, "PKT" to 5, "ICT" to 7, "WIB" to 7, "WITA" to 8,
        "WIT" to 9, "SGT" to 8, "MYT" to 8, "PHT" to 8, "HKT" to 8, "AWST" to 8, "JST" to 9, "KST" to 9,
        "ACST" to 9, "AEST" to 10, "AEDT" to 11, "NZST" to 12, "NZDT" to 13,
    ).mapValues { it.value * 3600 } + mapOf("IST" to 5 * 3600 + 1800, "NST" to -(3 * 3600 + 1800), "ACST" to 9 * 3600 + 1800)

    fun resolve(name: String): ZoneId? {
        if (name.isEmpty()) return null
        abbreviations[name.uppercase()]?.let { return ZoneOffset.ofTotalSeconds(it) }
        val zone = runCatching { ZoneId.of(name) }.getOrNull() ?: return null
        if (zone is ZoneOffset) return zone
        if (name.startsWith("UTC") || name.startsWith("GMT") || name.startsWith("UT")) return zone
        if (!IntlBridge.available || IntlBridge.isValidZone(name) || ZoneId.FALLBACK_OFFSETS.containsKey(name)) return zone
        return null
    }
}

// ---------------------------------------------------------------------------------------------
// Parsed result

/** Fields parsed from text, resolved to date/time/zone the way Java's SMART resolver does. */
internal class Parsed(
    private val text: CharSequence,
    val fields: Map<TemporalField, Long>,
    private val parsedZone: ZoneId?,
    formatterZone: ZoneId?,
    private val lenient: Boolean,
    val endIndex: Int,
) : TemporalAccessor {
    private val zone: ZoneId? = parsedZone ?: formatterZone
    private val offset: ZoneOffset? = fields[ChronoField.OFFSET_SECONDS]?.let { ZoneOffset.ofTotalSeconds(it.toInt()) }

    private val year: Long? = fields[ChronoField.YEAR]
        ?: fields[ChronoField.PROLEPTIC_MONTH]?.let { java.time.floorDiv(it, 12) }
        ?: fields[ChronoField.YEAR_OF_ERA]?.let { yoe -> if ((fields[ChronoField.ERA] ?: 1L) == 1L) yoe else 1 - yoe }
    private val month: Long? = fields[ChronoField.MONTH_OF_YEAR] ?: fields[ChronoField.PROLEPTIC_MONTH]?.let { floorMod(it, 12) + 1 }

    private val dateTime: LocalDateTime? by lazy { if (lenient) lenientDateTime() else null }
    private val date: LocalDate? by lazy { if (lenient) dateTime?.toLocalDate() else strictDate() }
    private val time: LocalTime? by lazy { if (lenient) dateTime?.toLocalTime() else strictTime() }

    private fun strictDate(): LocalDate? {
        fields[ChronoField.EPOCH_DAY]?.let { return LocalDate.ofEpochDay(it) }
        val y = year ?: return null
        val doy = fields[ChronoField.DAY_OF_YEAR]
        if (doy != null && month == null) return LocalDate.ofYearDay(y.toInt(), doy.toInt())
        val m = month ?: return null
        val d = fields[ChronoField.DAY_OF_MONTH] ?: return null
        return LocalDate.of(y.toInt(), m.toInt(), d.toInt())
    }

    private fun hourOfDay(): Long? {
        fields[ChronoField.HOUR_OF_DAY]?.let { return it }
        fields[ChronoField.CLOCK_HOUR_OF_DAY]?.let { return if (it == 24L) 0L else it }
        val h12 = fields[ChronoField.HOUR_OF_AMPM] ?: fields[ChronoField.CLOCK_HOUR_OF_AMPM]?.let { if (it == 12L) 0L else it } ?: return null
        return (fields[ChronoField.AMPM_OF_DAY] ?: 0L) * 12 + h12
    }

    private fun nanoOfSecond(): Long? =
        fields[ChronoField.NANO_OF_SECOND]
            ?: fields[ChronoField.MILLI_OF_SECOND]?.let { it * 1_000_000 }
            ?: fields[ChronoField.MICRO_OF_SECOND]?.let { it * 1000 }

    private fun strictTime(): LocalTime? {
        fields[ChronoField.NANO_OF_DAY]?.let { return LocalTime.ofNanoOfDay(it) }
        fields[ChronoField.MILLI_OF_DAY]?.let { return LocalTime.ofNanoOfDay(it * 1_000_000) }
        fields[ChronoField.SECOND_OF_DAY]?.let { return LocalTime.ofSecondOfDay(it).plusNanos(nanoOfSecond() ?: 0) }
        val hour = hourOfDay()
        val minute = fields[ChronoField.MINUTE_OF_HOUR]
        val second = fields[ChronoField.SECOND_OF_MINUTE]
        val nano = nanoOfSecond()
        if (hour == null) return if (minute == null && second == null && nano == null) null else null
        return LocalTime.of(hour.toInt(), (minute ?: 0).toInt(), (second ?: 0).toInt(), (nano ?: 0).toInt())
    }

    /** SimpleDateFormat semantics: the epoch fills in what the text left out and values roll over. */
    private fun lenientDateTime(): LocalDateTime {
        val y = year ?: 1970
        var ldt = LocalDate.of(y.toInt(), 1, 1).atStartOfDay()
        fields[ChronoField.EPOCH_DAY]?.let { ldt = LocalDate.ofEpochDay(it).atStartOfDay() } ?: run {
            val doy = fields[ChronoField.DAY_OF_YEAR]
            if (doy != null && month == null) {
                ldt = ldt.plusDays(doy - 1)
            } else {
                ldt = ldt.plusMonths((month ?: 1) - 1).plusDays((fields[ChronoField.DAY_OF_MONTH] ?: 1) - 1)
            }
        }
        fields[ChronoField.NANO_OF_DAY]?.let { return ldt.plusNanos(it) }
        fields[ChronoField.MILLI_OF_DAY]?.let { return ldt.plusNanos(it * 1_000_000) }
        fields[ChronoField.SECOND_OF_DAY]?.let { return ldt.plusSeconds(it).plusNanos(nanoOfSecond() ?: 0) }
        return ldt.plusHours(hourOfDay() ?: 0)
            .plusMinutes(fields[ChronoField.MINUTE_OF_HOUR] ?: 0)
            .plusSeconds(fields[ChronoField.SECOND_OF_MINUTE] ?: 0)
            .plusNanos(nanoOfSecond() ?: 0)
    }

    private fun localDateTime(): LocalDateTime? {
        if (lenient) return dateTime
        val d = date ?: return null
        val t = time ?: return null
        return LocalDateTime.of(d, t)
    }

    private fun instantSeconds(): Long? {
        fields[ChronoField.INSTANT_SECONDS]?.let { return it }
        val ldt = localDateTime() ?: return null
        val off = offset ?: zone?.offsetForLocal(ldt.toEpochSecond(ZoneOffset.UTC)) ?: return null
        return ldt.toEpochSecond(off)
    }

    override fun zoneOrNull(): ZoneId? = zone
    override fun offsetOrNull(): ZoneOffset? = offset

    override fun isSupported(field: TemporalField): Boolean {
        if (fields.containsKey(field)) return true
        if (field !is ChronoField) return false
        return when (field) {
            ChronoField.INSTANT_SECONDS -> instantSeconds() != null
            ChronoField.OFFSET_SECONDS -> offset != null
            ChronoField.YEAR -> year != null
            ChronoField.MONTH_OF_YEAR -> month != null
            else -> when {
                field.isDateBased -> date != null
                field.isTimeBased -> time != null || (lenient && field == ChronoField.NANO_OF_SECOND)
                else -> false
            }
        }
    }

    override fun getLong(field: TemporalField): Long {
        fields[field]?.let { return it }
        if (field is ChronoField) {
            when (field) {
                ChronoField.INSTANT_SECONDS -> instantSeconds()?.let { return it }
                ChronoField.OFFSET_SECONDS -> offset?.let { return it.totalSeconds.toLong() }
                ChronoField.YEAR -> year?.let { return it }
                ChronoField.MONTH_OF_YEAR -> month?.let { return it }
                else -> {
                    if (field.isDateBased) date?.let { return it.getLong(field) }
                    if (field.isTimeBased) time?.let { return it.getLong(field) }
                }
            }
        }
        throw DateTimeException("Unsupported field: $field")
    }

    override fun toString(): String = "{$fields},ISO,${zone ?: ""} resolved to ${date ?: ""}${time?.let { "T$it" } ?: ""}"
}

/** A temporal whose zone queries answer the formatter's override zone. */
private class ZoneOverride(private val inner: TemporalAccessor, private val zone: ZoneId) : TemporalAccessor {
    override fun isSupported(field: TemporalField): Boolean = inner.isSupported(field)
    override fun getLong(field: TemporalField): Long = inner.getLong(field)
    override fun zoneOrNull(): ZoneId = zone
    override fun offsetOrNull(): ZoneOffset? = inner.offsetOrNull()
    override fun toString(): String = inner.toString()
}

// ---------------------------------------------------------------------------------------------
// Formatter

class DateTimeFormatter internal constructor(
    internal val tokens: List<Token>,
    val locale: Locale,
    val zone: ZoneId?,
    internal val defaults: Map<TemporalField, Long>,
    internal val lenient: Boolean,
) {
    fun getLocale(): Locale = locale
    fun getZone(): ZoneId? = zone

    fun withLocale(locale: Locale): DateTimeFormatter = if (locale == this.locale) this else DateTimeFormatter(tokens, locale, zone, defaults, lenient)
    fun withZone(zone: ZoneId?): DateTimeFormatter = if (zone == this.zone) this else DateTimeFormatter(tokens, locale, zone, defaults, lenient)
    fun withResolverStyle(resolverStyle: ResolverStyle): DateTimeFormatter = this
    fun withChronology(chrono: Any?): DateTimeFormatter = this
    internal fun withLenient(lenient: Boolean): DateTimeFormatter = DateTimeFormatter(tokens, locale, zone, defaults, lenient)

    fun format(temporal: TemporalAccessor): String {
        val sb = StringBuilder()
        formatTo(temporal, sb)
        return sb.toString()
    }

    fun formatTo(temporal: TemporalAccessor, appendable: StringBuilder) {
        formatTokens(tokens, this, adjust(temporal), appendable)
    }

    private fun adjust(temporal: TemporalAccessor): TemporalAccessor {
        val z = zone ?: return temporal
        if (temporal is ZonedDateTime) return if (temporal.zone == z) temporal else temporal.withZoneSameInstant(z)
        if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) return ZonedDateTime.ofInstant(Instant.from(temporal), z)
        if (temporal is LocalDateTime) return ZonedDateTime.of(temporal, z)
        if (temporal.zoneOrNull() != null) return temporal
        return ZoneOverride(temporal, z)
    }

    fun parse(text: CharSequence): TemporalAccessor = parseAt(text, 0, requireAll = true)

    fun <R> parse(text: CharSequence, query: TemporalQuery<R>): R {
        val parsed = parseAt(text, 0, requireAll = true)
        try {
            return query.queryFrom(parsed)
        } catch (e: DateTimeParseException) {
            throw e
        } catch (e: DateTimeException) {
            throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0, e)
        }
    }

    /** Parses from [start]; with [requireAll] the whole text must be consumed. */
    internal fun parseAt(text: CharSequence, start: Int, requireAll: Boolean): Parsed {
        val str = text.toString()
        val ctx = ParseContext(str, lenient)
        val end = parseTokens(tokens, this, ctx, start)
        if (end < 0) throw DateTimeParseException("Text '$text' could not be parsed at index ${ctx.errorIndex}", text, ctx.errorIndex)
        if (requireAll && end < str.length) throw DateTimeParseException("Text '$text' could not be parsed, unparsed text found at index $end", text, end)
        for ((field, value) in defaults) if (!ctx.fields.containsKey(field)) ctx.fields[field] = value
        return Parsed(text, ctx.fields, ctx.zone, zone, lenient, end)
    }

    override fun toString(): String = "DateTimeFormatter(${tokens.size} tokens, $locale${zone?.let { ", $it" } ?: ""})"

    companion object {
        private fun builder(): DateTimeFormatterBuilder = DateTimeFormatterBuilder()

        val ISO_LOCAL_DATE: DateTimeFormatter = builder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .toFormatter(Locale.ROOT)

        val ISO_LOCAL_TIME: DateTimeFormatter = builder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter(Locale.ROOT)

        val ISO_LOCAL_DATE_TIME: DateTimeFormatter = builder()
            .append(ISO_LOCAL_DATE).appendLiteral('T').append(ISO_LOCAL_TIME)
            .toFormatter(Locale.ROOT)

        val ISO_OFFSET_DATE: DateTimeFormatter = builder().append(ISO_LOCAL_DATE).appendOffsetId().toFormatter(Locale.ROOT)
        val ISO_OFFSET_TIME: DateTimeFormatter = builder().append(ISO_LOCAL_TIME).appendOffsetId().toFormatter(Locale.ROOT)
        val ISO_OFFSET_DATE_TIME: DateTimeFormatter = builder().append(ISO_LOCAL_DATE_TIME).appendOffsetId().toFormatter(Locale.ROOT)

        val ISO_ZONED_DATE_TIME: DateTimeFormatter = builder()
            .append(ISO_OFFSET_DATE_TIME)
            .optionalStart().appendLiteral('[').appendZoneRegionId().appendLiteral(']')
            .toFormatter(Locale.ROOT)

        val ISO_DATE_TIME: DateTimeFormatter = builder()
            .append(ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffsetId()
            .optionalStart().appendLiteral('[').appendZoneRegionId().appendLiteral(']')
            .toFormatter(Locale.ROOT)

        val ISO_DATE: DateTimeFormatter = builder().append(ISO_LOCAL_DATE).optionalStart().appendOffsetId().toFormatter(Locale.ROOT)
        val ISO_TIME: DateTimeFormatter = builder().append(ISO_LOCAL_TIME).optionalStart().appendOffsetId().toFormatter(Locale.ROOT)

        val BASIC_ISO_DATE: DateTimeFormatter = builder()
            .appendValue(ChronoField.YEAR, 4).appendValue(ChronoField.MONTH_OF_YEAR, 2).appendValue(ChronoField.DAY_OF_MONTH, 2)
            .optionalStart().appendOffset("+HHMM", "Z")
            .toFormatter(Locale.ROOT)

        val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter(listOf(InstantTok()), Locale.ROOT, null, emptyMap(), false)

        val RFC_1123_DATE_TIME: DateTimeFormatter = builder()
            .optionalStart().appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT).appendLiteral(", ").optionalEnd()
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral(' ')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT).appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4).appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2).optionalEnd()
            .appendLiteral(' ').appendOffset("+HHMM", "GMT")
            .toFormatter(Locale.ENGLISH)

        fun ofPattern(pattern: String): DateTimeFormatter = ofPattern(pattern, Locale.getDefault())
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = builder().appendPattern(pattern).toFormatter(locale)

        // en-US CLDR patterns; other locales get the same shapes.
        private fun datePattern(style: FormatStyle): String = when (style) {
            FormatStyle.FULL -> "EEEE, MMMM d, y"
            FormatStyle.LONG -> "MMMM d, y"
            FormatStyle.MEDIUM -> "MMM d, y"
            FormatStyle.SHORT -> "M/d/yy"
        }

        private fun timePattern(style: FormatStyle): String = when (style) {
            FormatStyle.FULL -> "h:mm:ss a zzzz"
            FormatStyle.LONG -> "h:mm:ss a z"
            FormatStyle.MEDIUM -> "h:mm:ss a"
            FormatStyle.SHORT -> "h:mm a"
        }

        fun ofLocalizedDate(dateStyle: FormatStyle): DateTimeFormatter = ofPattern(datePattern(dateStyle))
        fun ofLocalizedTime(timeStyle: FormatStyle): DateTimeFormatter = ofPattern(timePattern(timeStyle))
        fun ofLocalizedDateTime(dateTimeStyle: FormatStyle): DateTimeFormatter = ofLocalizedDateTime(dateTimeStyle, dateTimeStyle)
        fun ofLocalizedDateTime(dateStyle: FormatStyle, timeStyle: FormatStyle): DateTimeFormatter =
            ofPattern("${datePattern(dateStyle)}, ${timePattern(timeStyle)}")
    }
}

// ---------------------------------------------------------------------------------------------
// Builder

class DateTimeFormatterBuilder {
    private val stack = ArrayList<ArrayList<Token>>().apply { add(ArrayList()) }
    private val defaults = HashMap<TemporalField, Long>()

    private fun add(token: Token): DateTimeFormatterBuilder {
        stack[stack.size - 1].add(token)
        return this
    }

    fun parseCaseInsensitive(): DateTimeFormatterBuilder = this
    fun parseCaseSensitive(): DateTimeFormatterBuilder = this
    fun parseStrict(): DateTimeFormatterBuilder = this
    fun parseLenient(): DateTimeFormatterBuilder = this

    fun parseDefaulting(field: TemporalField, value: Long): DateTimeFormatterBuilder {
        defaults[field] = value
        return this
    }

    fun appendValue(field: TemporalField): DateTimeFormatterBuilder = add(Num(field, 1, 19, SignStyle.NORMAL))
    fun appendValue(field: TemporalField, width: Int): DateTimeFormatterBuilder = add(Num(field, width, width, SignStyle.NOT_NEGATIVE))
    fun appendValue(field: TemporalField, minWidth: Int, maxWidth: Int, signStyle: SignStyle): DateTimeFormatterBuilder =
        add(Num(field, minWidth, maxWidth, signStyle))

    fun appendValueReduced(field: TemporalField, width: Int, maxWidth: Int, baseValue: Int): DateTimeFormatterBuilder =
        add(Num(field, width, maxWidth, SignStyle.NOT_NEGATIVE, reducedBase = baseValue))

    fun appendValueReduced(field: TemporalField, width: Int, maxWidth: Int, baseDate: LocalDate): DateTimeFormatterBuilder =
        appendValueReduced(field, width, maxWidth, baseDate.year)

    fun appendFraction(field: TemporalField, minWidth: Int, maxWidth: Int, decimalPoint: Boolean): DateTimeFormatterBuilder {
        if (field != ChronoField.NANO_OF_SECOND) throw IllegalArgumentException("Only NANO_OF_SECOND fractions are supported: $field")
        return add(Fraction(minWidth, maxWidth, decimalPoint))
    }

    fun appendText(field: TemporalField): DateTimeFormatterBuilder = appendText(field, TextStyle.FULL)
    fun appendText(field: TemporalField, textStyle: TextStyle): DateTimeFormatterBuilder {
        if (field !is ChronoField) throw IllegalArgumentException("Unsupported text field: $field")
        return add(Text(field, textStyle))
    }

    fun appendText(field: TemporalField, textLookup: Map<Long, String>): DateTimeFormatterBuilder = add(TextMap(field, textLookup))

    fun appendLiteral(literal: Char): DateTimeFormatterBuilder = add(Lit(literal.toString()))
    fun appendLiteral(literal: String): DateTimeFormatterBuilder = if (literal.isEmpty()) this else add(Lit(literal))

    fun append(formatter: DateTimeFormatter): DateTimeFormatterBuilder {
        formatter.tokens.forEach { add(it) }
        defaults.putAll(formatter.defaults)
        return this
    }

    fun appendOptional(formatter: DateTimeFormatter): DateTimeFormatterBuilder {
        defaults.putAll(formatter.defaults)
        return add(Optional(formatter.tokens))
    }

    fun optionalStart(): DateTimeFormatterBuilder {
        stack.add(ArrayList())
        return this
    }

    fun optionalEnd(): DateTimeFormatterBuilder {
        if (stack.size <= 1) throw IllegalStateException("Cannot call optionalEnd() as there was no previous call to optionalStart()")
        val tokens = stack.removeAt(stack.size - 1)
        if (tokens.isNotEmpty()) add(Optional(tokens))
        return this
    }

    fun appendOffset(pattern: String, noOffsetText: String): DateTimeFormatterBuilder = add(Offset(pattern, noOffsetText))
    fun appendOffsetId(): DateTimeFormatterBuilder = appendOffset("+HH:MM:ss", "Z")
    fun appendLocalizedOffset(style: TextStyle): DateTimeFormatterBuilder = add(LocalizedOffset(style))
    fun appendZoneId(): DateTimeFormatterBuilder = add(ZoneIdTok())
    fun appendZoneRegionId(): DateTimeFormatterBuilder = add(ZoneIdTok())
    fun appendZoneOrOffsetId(): DateTimeFormatterBuilder = add(ZoneIdTok())
    fun appendZoneText(textStyle: TextStyle): DateTimeFormatterBuilder = add(ZoneText(textStyle))
    fun appendGenericZoneText(textStyle: TextStyle): DateTimeFormatterBuilder = add(ZoneText(textStyle))
    fun appendInstant(): DateTimeFormatterBuilder = add(InstantTok())
    fun appendInstant(fractionalDigits: Int): DateTimeFormatterBuilder = add(InstantTok(fractionalDigits))

    fun appendPattern(pattern: String): DateTimeFormatterBuilder {
        var pos = 0
        while (pos < pattern.length) {
            val c = pattern[pos]
            when {
                c in 'A'..'Z' || c in 'a'..'z' -> {
                    val start = pos
                    while (pos < pattern.length && pattern[pos] == c) pos++
                    appendLetter(c, pos - start)
                }
                c == '\'' -> {
                    if (pos + 1 < pattern.length && pattern[pos + 1] == '\'') {
                        appendLiteral('\'')
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
                        if (!closed) throw IllegalArgumentException("Pattern ends with an incomplete string literal: $pattern")
                        appendLiteral(sb.toString())
                    }
                }
                c == '[' -> { optionalStart(); pos++ }
                c == ']' -> { optionalEnd(); pos++ }
                c == '{' || c == '}' || c == '#' -> throw IllegalArgumentException("Pattern includes reserved character: '$c'")
                else -> { appendLiteral(c); pos++ }
            }
        }
        return this
    }

    private fun appendLetter(c: Char, count: Int) {
        fun tooMany(): Nothing = throw IllegalArgumentException("Too many pattern letters: $c")
        when (c) {
            'G' -> appendText(ChronoField.ERA, when (count) { 1, 2, 3 -> TextStyle.SHORT; 4 -> TextStyle.FULL; 5 -> TextStyle.NARROW; else -> tooMany() })
            'u', 'y', 'Y' -> {
                val field = if (c == 'u') ChronoField.YEAR else ChronoField.YEAR_OF_ERA
                when (count) {
                    1 -> appendValue(field, 1, 19, SignStyle.NORMAL)
                    2 -> appendValueReduced(field, 2, 2, 2000)
                    else -> appendValue(field, count, 19, if (count < 4) SignStyle.NORMAL else SignStyle.EXCEEDS_PAD)
                }
            }
            'M', 'L' -> when (count) {
                1 -> appendValue(ChronoField.MONTH_OF_YEAR)
                2 -> appendValue(ChronoField.MONTH_OF_YEAR, 2)
                3 -> appendText(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.SHORT_STANDALONE else TextStyle.SHORT)
                4 -> appendText(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.FULL_STANDALONE else TextStyle.FULL)
                5 -> appendText(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.NARROW_STANDALONE else TextStyle.NARROW)
                else -> tooMany()
            }
            'd' -> when (count) { 1 -> appendValue(ChronoField.DAY_OF_MONTH); 2 -> appendValue(ChronoField.DAY_OF_MONTH, 2); else -> tooMany() }
            'D' -> when (count) {
                1 -> appendValue(ChronoField.DAY_OF_YEAR)
                2 -> appendValue(ChronoField.DAY_OF_YEAR, 2, 3, SignStyle.NOT_NEGATIVE)
                3 -> appendValue(ChronoField.DAY_OF_YEAR, 3)
                else -> tooMany()
            }
            'E' -> appendText(ChronoField.DAY_OF_WEEK, when (count) { 1, 2, 3 -> TextStyle.SHORT; 4 -> TextStyle.FULL; 5 -> TextStyle.NARROW; else -> tooMany() })
            'e', 'c' -> { if (count > 5) tooMany(); add(LocalizedDow(count, c == 'c')) }
            'a' -> { if (count > 1) tooMany(); appendText(ChronoField.AMPM_OF_DAY, TextStyle.SHORT) }
            'H', 'k', 'K', 'h', 'm', 's' -> {
                val field = when (c) {
                    'H' -> ChronoField.HOUR_OF_DAY
                    'k' -> ChronoField.CLOCK_HOUR_OF_DAY
                    'K' -> ChronoField.HOUR_OF_AMPM
                    'h' -> ChronoField.CLOCK_HOUR_OF_AMPM
                    'm' -> ChronoField.MINUTE_OF_HOUR
                    else -> ChronoField.SECOND_OF_MINUTE
                }
                when (count) { 1 -> appendValue(field); 2 -> appendValue(field, 2); else -> tooMany() }
            }
            'S' -> appendFraction(ChronoField.NANO_OF_SECOND, count, count, false)
            'A' -> appendValue(ChronoField.MILLI_OF_DAY, count, 19, SignStyle.NOT_NEGATIVE)
            'n' -> appendValue(ChronoField.NANO_OF_SECOND, count, 19, SignStyle.NOT_NEGATIVE)
            'N' -> appendValue(ChronoField.NANO_OF_DAY, count, 19, SignStyle.NOT_NEGATIVE)
            'V' -> { if (count != 2) throw IllegalArgumentException("Pattern letter count must be 2: V"); appendZoneId() }
            'v' -> appendGenericZoneText(when (count) { 1 -> TextStyle.SHORT; 4 -> TextStyle.FULL; else -> throw IllegalArgumentException("Wrong number of pattern letters: v") })
            'z' -> appendZoneText(when (count) { 1, 2, 3 -> TextStyle.SHORT; 4 -> TextStyle.FULL; else -> tooMany() })
            'O' -> appendLocalizedOffset(when (count) { 1 -> TextStyle.SHORT; 4 -> TextStyle.FULL; else -> throw IllegalArgumentException("Pattern letter count must be 1 or 4: O") })
            'X', 'x' -> {
                val pattern = when (count) { 1 -> "+HHmm"; 2 -> "+HHMM"; 3 -> "+HH:MM"; 4 -> "+HHMMss"; 5 -> "+HH:MM:ss"; else -> tooMany() }
                val zero = if (c == 'X') "Z" else when (count) { 1 -> "+00"; 2, 4 -> "+0000"; else -> "+00:00" }
                appendOffset(pattern, zero)
            }
            'Z' -> when (count) {
                1, 2, 3 -> appendOffset("+HHMM", "+0000")
                4 -> appendLocalizedOffset(TextStyle.FULL)
                5 -> appendOffset("+HH:MM:ss", "Z")
                else -> tooMany()
            }
            'p' -> {}
            'w', 'W', 'Q', 'q', 'F', 'B', 'g' -> throw IllegalArgumentException("Unsupported pattern letter: $c")
            else -> throw IllegalArgumentException("Unknown pattern letter: $c")
        }
    }

    fun toFormatter(): DateTimeFormatter = toFormatter(Locale.getDefault())

    fun toFormatter(locale: Locale): DateTimeFormatter {
        while (stack.size > 1) optionalEnd()
        return DateTimeFormatter(ArrayList(stack[0]), locale, null, HashMap(defaults), false)
    }
}
