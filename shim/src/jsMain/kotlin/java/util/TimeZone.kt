package java.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.floorDiv

/** A java.util.TimeZone over the shim's ZoneId; offsets come from Intl where the host has it. */
open class TimeZone(private val zone: ZoneId) {
    /** Kotlin reads `getID()` as the `id` property. */
    val id: String get() = zone.id
    fun getID(): String = zone.id

    fun toZoneId(): ZoneId = zone

    /** Offset from UTC in milliseconds at [date] (epoch milliseconds), daylight saving included. */
    fun getOffset(date: Long): Int = zone.offsetAt(floorDiv(date, 1000L)).totalSeconds * 1000

    /** Standard offset in milliseconds: the smaller of this year's January and July offsets. */
    val rawOffset: Int get() = if (zone is ZoneOffset) zone.totalSeconds * 1000 else minOf(januaryOffset(), julyOffset()) * 1000
    fun getRawOffset(): Int = rawOffset

    fun getDSTSavings(): Int = if (zone is ZoneOffset) 0 else (maxOf(januaryOffset(), julyOffset()) - minOf(januaryOffset(), julyOffset())) * 1000
    fun useDaylightTime(): Boolean = getDSTSavings() != 0
    fun inDaylightTime(date: Date): Boolean = getOffset(date.time) != rawOffset
    fun observesDaylightTime(): Boolean = useDaylightTime()

    fun getDisplayName(): String = id
    fun getDisplayName(locale: Locale): String = id
    fun getDisplayName(daylight: Boolean, style: Int): String = id
    fun getDisplayName(daylight: Boolean, style: Int, locale: Locale): String = id

    fun hasSameRules(other: TimeZone?): Boolean = other != null && rawOffset == other.rawOffset && getDSTSavings() == other.getDSTSavings()
    fun clone(): TimeZone = TimeZone(zone)

    private fun januaryOffset(): Int = offsetOn(LocalDate.now(zone).year, 1)
    private fun julyOffset(): Int = offsetOn(LocalDate.now(zone).year, 7)
    private fun offsetOn(year: Int, month: Int): Int =
        zone.offsetAt(LocalDate.of(year, month, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)).totalSeconds

    override fun equals(other: Any?): Boolean = other is TimeZone && other.zone.id == zone.id
    override fun hashCode(): Int = zone.id.hashCode()
    override fun toString(): String = "TimeZone[$id]"

    companion object {
        const val SHORT = 0
        const val LONG = 1

        private var default: TimeZone? = null

        fun getDefault(): TimeZone = default ?: TimeZone(ZoneId.systemDefault()).also { default = it }

        fun setDefault(zone: TimeZone?) {
            default = zone
        }

        /** Unknown ids give GMT, as on the JVM. */
        fun getTimeZone(id: String): TimeZone = TimeZone(runCatching { ZoneId.of(id) }.getOrElse { ZoneId.of("GMT") })
        fun getTimeZone(zoneId: ZoneId): TimeZone = TimeZone(zoneId)

        fun getAvailableIDs(): Array<String> = ZoneId.getAvailableZoneIds().toTypedArray()
    }
}
