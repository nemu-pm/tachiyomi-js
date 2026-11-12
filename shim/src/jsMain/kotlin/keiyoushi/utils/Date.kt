package keiyoushi.utils

// Ported from keiyoushi core utils/Date.kt. The java.time DateTimeFormatter helpers
// (tryParseDate, tryParseDateTime, tryParseZonedDateTime) are left out until the shim has a
// java.time subset; SimpleDateFormat.parse(String, ParsePosition) is not shimmed either, so
// the deprecated helper catches the parse failure instead.

import java.text.SimpleDateFormat
import kotlin.time.Instant

@Deprecated(
    message = "Use Instant.tryParse, DateTimeFormatter.tryParseDate, tryParseDateTime, or tryParseZonedDateTime instead.",
)
fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

/**
 * Parses [date] as a full ISO 8601 instant string, for example
 * `1999-03-22T05:06:07.000Z` or `1999-03-22T05:06:07+01:00`.
 *
 * Returns 0 if [date] is null or cannot be parsed.
 */
fun Instant.Companion.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date).toEpochMilliseconds()
    } catch (_: Exception) {
        0L
    }
}
