package java.util

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Java Date shim for Kotlin/JS.
 */
class Date(val time: Long) : Comparable<Date> {
    constructor() : this(currentTimeMillis())

    fun getTime(): Long = time
    fun toInstant(): Instant = Instant.ofEpochMilli(time)
    fun before(other: Date): Boolean = time < other.time
    fun after(other: Date): Boolean = time > other.time
    fun clone(): Date = Date(time)

    override fun compareTo(other: Date): Int = time.compareTo(other.time)

    /** The JVM shape, `Thu Jan 01 00:00:00 UTC 1970`, in the default time zone. */
    override fun toString(): String = TO_STRING.format(toInstant().atZone(TimeZone.getDefault().toZoneId()))

    override fun equals(other: Any?): Boolean {
        if (other !is Date) return false
        return time == other.time
    }

    override fun hashCode(): Int = time.hashCode()

    companion object {
        private val TO_STRING: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US) }

        fun currentTimeMillis(): Long {
            // Use Kotlin/JS Date.now()
            return kotlin.js.Date.now().toLong()
        }

        fun from(instant: Instant): Date = Date(instant.toEpochMilli())
    }
}
