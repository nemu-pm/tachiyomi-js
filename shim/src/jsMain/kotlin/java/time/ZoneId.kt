package java.time

import java.time.format.TextStyle
import java.util.Locale

/**
 * Time zones. Region ids ("Asia/Tokyo") resolve their rules through the host's Intl API, so
 * DST is honoured wherever ICU is present; without Intl the id falls back to a fixed offset
 * from [FALLBACK_OFFSETS] (or UTC when unknown), which is the same approximation the old
 * TimeZone shim made everywhere.
 */
open class ZoneId protected constructor(val id: String) {
    open val rules: ZoneRules get() = ZoneRules(this)

    /** Offset in force at the given instant. */
    open fun offsetAt(epochSecond: Long): ZoneOffset = ZoneOffset.UTC

    /**
     * Offset for a local date-time (seconds of the civil fields read as UTC). In an overlap the
     * earlier offset wins, in a gap the later one, matching Java's resolution.
     */
    open fun offsetForLocal(localEpochSecond: Long): ZoneOffset {
        val o1 = offsetAt(localEpochSecond)
        val o2 = offsetAt(localEpochSecond - o1.totalSeconds)
        if (o1.totalSeconds == o2.totalSeconds) return o1
        val o3 = offsetAt(localEpochSecond - o2.totalSeconds)
        if (o2.totalSeconds == o3.totalSeconds) return o2
        // Gap: neither candidate is stable, keep the larger (later) offset like Java does.
        return if (o1.totalSeconds > o2.totalSeconds) o1 else o2
    }

    fun getId(): String = id
    open fun normalized(): ZoneId = this

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val long = style == TextStyle.FULL || style == TextStyle.FULL_STANDALONE
        return IntlBridge.zoneName(id, currentEpochMillis() / 1000, long, locale.toLanguageTag()) ?: id
    }

    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    companion object {
        /** Fixed offsets (seconds) used only when Intl is unavailable; standard time, no DST. */
        internal val FALLBACK_OFFSETS: Map<String, Int> = mapOf(
            "Asia/Ho_Chi_Minh" to 7 * 3600, "Asia/Saigon" to 7 * 3600, "Asia/Bangkok" to 7 * 3600,
            "Asia/Jakarta" to 7 * 3600, "Asia/Tokyo" to 9 * 3600, "Asia/Seoul" to 9 * 3600,
            "Asia/Shanghai" to 8 * 3600, "Asia/Taipei" to 8 * 3600, "Asia/Singapore" to 8 * 3600,
            "Asia/Hong_Kong" to 8 * 3600, "Asia/Manila" to 8 * 3600, "Asia/Kolkata" to 5 * 3600 + 1800,
            "Asia/Dubai" to 4 * 3600, "Asia/Riyadh" to 3 * 3600, "Europe/Istanbul" to 3 * 3600,
            "Europe/Moscow" to 3 * 3600, "Europe/Paris" to 3600, "Europe/Madrid" to 3600,
            "Europe/Berlin" to 3600, "Europe/Rome" to 3600, "Europe/London" to 0, "Europe/Lisbon" to 0,
            "Africa/Casablanca" to 3600, "America/Sao_Paulo" to -3 * 3600, "America/Argentina/Buenos_Aires" to -3 * 3600,
            "America/Santiago" to -4 * 3600, "America/New_York" to -5 * 3600, "America/Chicago" to -6 * 3600,
            "America/Denver" to -7 * 3600, "America/Los_Angeles" to -8 * 3600, "America/Mexico_City" to -6 * 3600,
            "Australia/Sydney" to 10 * 3600, "Pacific/Auckland" to 12 * 3600,
        )

        private val regionCache = HashMap<String, ZoneId>()

        fun of(zoneId: String): ZoneId {
            when (zoneId) {
                "Z", "UTC", "GMT", "UT" -> return if (zoneId == "Z") ZoneOffset.UTC else ZoneRegion(zoneId, fixed = 0)
            }
            if (zoneId.startsWith("+") || zoneId.startsWith("-")) return ZoneOffset.of(zoneId)
            for (prefix in arrayOf("UTC", "GMT", "UT")) {
                if (zoneId.startsWith(prefix) && zoneId.length > prefix.length) {
                    val rest = zoneId.substring(prefix.length)
                    if (rest.startsWith("+") || rest.startsWith("-")) {
                        val offset = ZoneOffset.of(rest)
                        return ZoneRegion(prefix + offset.id, fixed = offset.totalSeconds)
                    }
                }
            }
            if (!Regex("""^[A-Za-z][A-Za-z0-9~/._+-]*$""").matches(zoneId)) {
                throw ZoneRulesException("Invalid ID for region-based ZoneId, invalid format: $zoneId")
            }
            return regionCache.getOrPut(zoneId) {
                when {
                    IntlBridge.isValidZone(zoneId) -> ZoneRegion(zoneId, fixed = null)
                    else -> ZoneRegion(zoneId, fixed = FALLBACK_OFFSETS[zoneId] ?: 0)
                }
            }
        }

        fun of(zoneId: String, aliasMap: Map<String, String>): ZoneId = of(aliasMap[zoneId] ?: zoneId)

        private var systemDefaultCached: ZoneId? = null

        fun systemDefault(): ZoneId = systemDefaultCached ?: run {
            val id = IntlBridge.systemZoneId()
            val zone = if (id != null) runCatching { of(id) }.getOrNull() ?: ZoneOffset.UTC else ZoneOffset.UTC
            systemDefaultCached = zone
            zone
        }

        fun getAvailableZoneIds(): Set<String> = FALLBACK_OFFSETS.keys
    }
}

/** A region id with rules from Intl, or a fixed offset when [fixed] is set. */
class ZoneRegion internal constructor(id: String, private val fixed: Int?) : ZoneId(id) {
    override fun offsetAt(epochSecond: Long): ZoneOffset {
        if (fixed != null) return ZoneOffset.ofTotalSeconds(fixed)
        val wall = IntlBridge.wallClockEpochSeconds(id, epochSecond) ?: return ZoneOffset.ofTotalSeconds(FALLBACK_OFFSETS[id] ?: 0)
        return ZoneOffset.ofTotalSeconds((wall - epochSecond).toInt())
    }

    override fun normalized(): ZoneId = if (fixed != null) ZoneOffset.ofTotalSeconds(fixed) else this
}

class ZoneOffset private constructor(val totalSeconds: Int) : ZoneId(idFor(totalSeconds)), Comparable<ZoneOffset> {
    override fun offsetAt(epochSecond: Long): ZoneOffset = this
    override fun offsetForLocal(localEpochSecond: Long): ZoneOffset = this
    override val rules: ZoneRules get() = ZoneRules(this)

    fun getTotalSeconds(): Int = totalSeconds

    override fun compareTo(other: ZoneOffset): Int = other.totalSeconds.compareTo(totalSeconds)

    companion object {
        val UTC: ZoneOffset = ZoneOffset(0)
        val MIN: ZoneOffset = ZoneOffset(-18 * 3600)
        val MAX: ZoneOffset = ZoneOffset(18 * 3600)

        private val cache = HashMap<Int, ZoneOffset>()

        private fun idFor(total: Int): String {
            if (total == 0) return "Z"
            val abs = if (total < 0) -total else total
            val h = abs / 3600
            val m = (abs / 60) % 60
            val s = abs % 60
            val sb = StringBuilder(if (total < 0) "-" else "+")
            sb.append(padNumber(h.toLong(), 2)).append(':').append(padNumber(m.toLong(), 2))
            if (s != 0) sb.append(':').append(padNumber(s.toLong(), 2))
            return sb.toString()
        }

        fun ofTotalSeconds(totalSeconds: Int): ZoneOffset {
            if (totalSeconds == 0) return UTC
            if (totalSeconds < -18 * 3600 || totalSeconds > 18 * 3600) throw DateTimeException("Zone offset not in valid range: -18:00 to +18:00")
            return cache.getOrPut(totalSeconds) { ZoneOffset(totalSeconds) }
        }

        fun ofHours(hours: Int): ZoneOffset = ofTotalSeconds(hours * 3600)
        fun ofHoursMinutes(hours: Int, minutes: Int): ZoneOffset = ofTotalSeconds(hours * 3600 + minutes * 60)
        fun ofHoursMinutesSeconds(hours: Int, minutes: Int, seconds: Int): ZoneOffset = ofTotalSeconds(hours * 3600 + minutes * 60 + seconds)

        /** Accepts `Z`, `+h`, `+hh`, `+hhmm`, `+hh:mm`, `+hhmmss`, `+hh:mm:ss`. */
        fun of(offsetId: String): ZoneOffset {
            if (offsetId == "Z" || offsetId == "z") return UTC
            val m = Regex("""^([+-])(\d{1,2})(?::?(\d{2})(?::?(\d{2}))?)?$""").find(offsetId)
                ?: throw DateTimeException("Invalid ID for ZoneOffset, invalid format: $offsetId")
            val sign = if (m.groupValues[1] == "-") -1 else 1
            val h = m.groupValues[2].toInt()
            val min = m.groupValues[3].toIntOrNull() ?: 0
            val s = m.groupValues[4].toIntOrNull() ?: 0
            return ofTotalSeconds(sign * (h * 3600 + min * 60 + s))
        }
    }
}

class ZoneRules internal constructor(private val zone: ZoneId) {
    fun getOffset(instant: Instant): ZoneOffset = zone.offsetAt(instant.epochSecond)
    fun getOffset(localDateTime: LocalDateTime): ZoneOffset = zone.offsetForLocal(localDateTime.toEpochSecond(ZoneOffset.UTC))
    val isFixedOffset: Boolean get() = zone is ZoneOffset || (zone is ZoneRegion && zone.normalized() is ZoneOffset)
    fun isDaylightSavings(instant: Instant): Boolean {
        // Compare with the offset half a year away; the smaller of the two is standard time.
        val now = getOffset(instant).totalSeconds
        val other = zone.offsetAt(instant.epochSecond + 182L * 86400).totalSeconds
        return now > other
    }
}
