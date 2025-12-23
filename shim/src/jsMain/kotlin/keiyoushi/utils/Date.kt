/**
 * JS-compatible implementation of keiyoushi.utils.Date
 */
package keiyoushi.utils

import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * Tries to parse a date string, returning 0L on failure.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun SimpleDateFormat.tryParse(date: String?): Long {
    date ?: return 0L

    return try {
        parse(date)?.time ?: 0L
    } catch (_: ParseException) {
        0L
    }
}

