package java.time

/*
 * Shared arithmetic and host bridges for the java.time shim.
 *
 * Dates are proleptic Gregorian, converted with the civil-from-days algorithms (Howard Hinnant).
 * Zone rules and localized names come from the host's Intl.DateTimeFormat when it exists (node,
 * browsers and JavaScriptCore ship ICU); without it, zones fall back to a fixed-offset table and
 * names to English.
 */

internal const val SECONDS_PER_DAY = 86400L
internal const val NANOS_PER_SECOND = 1_000_000_000L

internal fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
}

internal fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

internal fun isLeapYear(year: Long): Boolean = (year % 4 == 0L) && (year % 100 != 0L || year % 400 == 0L)

internal fun lengthOfMonth(year: Long, month: Int): Int = when (month) {
    2 -> if (isLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** Days since 1970-01-01 for a proleptic Gregorian date. */
internal fun daysFromCivil(year: Long, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = floorDiv(y, 400)
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

internal class Civil(val year: Long, val month: Int, val day: Int)

internal fun civilFromDays(epochDay: Long): Civil {
    val z = epochDay + 719468
    val era = floorDiv(z, 146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return Civil(if (m <= 2) y + 1 else y, m, d)
}

internal fun padNumber(value: Long, width: Int): String {
    val negative = value < 0
    var digits = (if (negative) -value else value).toString()
    while (digits.length < width) digits = "0$digits"
    return if (negative) "-$digits" else digits
}

internal fun currentEpochMillis(): Long = kotlin.js.Date.now().toLong()

/** Bridges into the host's Intl API; every entry point degrades to null when Intl is missing. */
internal object IntlBridge {
    private val formatterCache: dynamic = js("({})")
    private val textCache = HashMap<String, LocaleText>()

    val available: Boolean = js("typeof Intl !== 'undefined' && typeof Intl.DateTimeFormat === 'function'") as Boolean

    fun isValidZone(id: String): Boolean {
        if (!available) return false
        return try {
            js("new Intl.DateTimeFormat('en-US', { timeZone: id })")
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun systemZoneId(): String? {
        if (!available) return null
        return try {
            val tz = js("Intl.DateTimeFormat().resolvedOptions().timeZone")
            (tz as? String)?.takeIf { it.isNotEmpty() }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * The wall-clock time in [zoneId] at [epochSecond], expressed as epoch seconds of the same
     * civil fields read as UTC; the zone offset is the difference between the two.
     */
    fun wallClockEpochSeconds(zoneId: String, epochSecond: Long): Long? {
        if (!available) return null
        return try {
            val cache = formatterCache
            val ms = epochSecond.toDouble() * 1000.0
            val result: Double = js(
                """
                (function () {
                    var fmt = cache[zoneId];
                    if (!fmt) {
                        fmt = new Intl.DateTimeFormat('en-US', {
                            timeZone: zoneId, hourCycle: 'h23', era: 'short',
                            year: 'numeric', month: 'numeric', day: 'numeric',
                            hour: 'numeric', minute: 'numeric', second: 'numeric'
                        });
                        cache[zoneId] = fmt;
                    }
                    var parts = fmt.formatToParts(new Date(ms));
                    var o = {};
                    for (var i = 0; i < parts.length; i++) o[parts[i].type] = parts[i].value;
                    var y = parseInt(o.year, 10);
                    if (o.era && o.era.charAt(0) === 'B') y = 1 - y;
                    var h = parseInt(o.hour, 10);
                    if (h === 24) h = 0;
                    var d = new Date(0);
                    d.setUTCFullYear(y, parseInt(o.month, 10) - 1, parseInt(o.day, 10));
                    d.setUTCHours(h, parseInt(o.minute, 10), parseInt(o.second, 10), 0);
                    return d.getTime() / 1000;
                })()
                """
            ) as Double
            result.toLong()
        } catch (e: Throwable) {
            null
        }
    }

    /** `timeZoneName: 'short'` / `'long'` for [zoneId] at [epochSecond], e.g. "PST" or "Pacific Standard Time". */
    fun zoneName(zoneId: String, epochSecond: Long, long: Boolean, localeTag: String): String? {
        if (!available) return null
        return try {
            val ms = epochSecond.toDouble() * 1000.0
            val style = if (long) "long" else "short"
            val tag = normalizeTag(localeTag)
            val name = js(
                """
                (function () {
                    var parts = new Intl.DateTimeFormat(tag, { timeZone: zoneId, timeZoneName: style }).formatToParts(new Date(ms));
                    for (var i = 0; i < parts.length; i++) if (parts[i].type === 'timeZoneName') return parts[i].value;
                    return null;
                })()
                """
            )
            name as? String
        } catch (e: Throwable) {
            null
        }
    }

    fun text(localeTag: String): LocaleText = textCache.getOrPut(localeTag) { buildText(localeTag) }

    private fun normalizeTag(tag: String): String = if (tag.isEmpty() || tag == "und") "en" else tag

    private fun buildText(localeTag: String): LocaleText {
        if (!available) return LocaleText.ENGLISH
        return try {
            val tag = normalizeTag(localeTag)
            val months = names(tag, "month", "long", false)
            val monthsShort = names(tag, "month", "short", false)
            val monthsStandalone = names(tag, "month", "long", true)
            val monthsShortStandalone = names(tag, "month", "short", true)
            val days = names(tag, "weekday", "long", true)
            val daysShort = names(tag, "weekday", "short", true)
            val ampm = dayPeriods(tag)
            LocaleText(
                months = months ?: LocaleText.ENGLISH.months,
                monthsShort = monthsShort ?: LocaleText.ENGLISH.monthsShort,
                monthsStandalone = monthsStandalone ?: months ?: LocaleText.ENGLISH.months,
                monthsShortStandalone = monthsShortStandalone ?: monthsShort ?: LocaleText.ENGLISH.monthsShort,
                days = days ?: LocaleText.ENGLISH.days,
                daysShort = daysShort ?: LocaleText.ENGLISH.daysShort,
                am = ampm?.get(0) ?: "AM",
                pm = ampm?.get(1) ?: "PM",
            )
        } catch (e: Throwable) {
            LocaleText.ENGLISH
        }
    }

    /**
     * Month names come from a formatter that also prints the day so ICU picks the "format"
     * (inflected) form Java uses for `MMMM`; [standalone] asks for the bare month instead (`LLLL`).
     * Weekdays index Monday..Sunday; 2024-01-01 was a Monday.
     */
    private fun names(tag: String, kind: String, style: String, standalone: Boolean): Array<String>? {
        val result = js(
            """
            (function () {
                var opts = { timeZone: 'UTC' };
                opts[kind] = style;
                if (kind === 'month' && !standalone) opts.day = 'numeric';
                var fmt = new Intl.DateTimeFormat(tag, opts);
                var out = [];
                var count = kind === 'month' ? 12 : 7;
                for (var i = 0; i < count; i++) {
                    var date = kind === 'month' ? new Date(Date.UTC(2024, i, 15)) : new Date(Date.UTC(2024, 0, 1 + i));
                    var parts = fmt.formatToParts(date);
                    var value = null;
                    for (var j = 0; j < parts.length; j++) if (parts[j].type === kind) { value = parts[j].value; break; }
                    if (value === null) return null;
                    out.push(value);
                }
                return out;
            })()
            """
        )
        if (result == null) return null
        val arr = result as Array<String>
        return if (arr.size == (if (kind == "month") 12 else 7)) arr else null
    }

    private fun dayPeriods(tag: String): Array<String>? {
        val result = js(
            """
            (function () {
                var fmt = new Intl.DateTimeFormat(tag, { timeZone: 'UTC', hour: 'numeric', hour12: true });
                function period(h) {
                    var parts = fmt.formatToParts(new Date(Date.UTC(2024, 0, 1, h)));
                    for (var j = 0; j < parts.length; j++) if (parts[j].type === 'dayPeriod') return parts[j].value;
                    return null;
                }
                var am = period(9), pm = period(21);
                return (am && pm) ? [am, pm] : null;
            })()
            """
        )
        return result as? Array<String>
    }
}

/** Localized names used by text pattern letters. Months index 1..12 via [month]; days 1..7 Monday..Sunday. */
internal class LocaleText(
    val months: Array<String>,
    val monthsShort: Array<String>,
    val monthsStandalone: Array<String>,
    val monthsShortStandalone: Array<String>,
    val days: Array<String>,
    val daysShort: Array<String>,
    val am: String,
    val pm: String,
) {
    companion object {
        val ENGLISH = LocaleText(
            months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
            monthsShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            monthsStandalone = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
            monthsShortStandalone = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
            daysShort = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            am = "AM",
            pm = "PM",
        )
    }
}
