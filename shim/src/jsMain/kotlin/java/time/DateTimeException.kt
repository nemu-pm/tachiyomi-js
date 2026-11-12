package java.time

open class DateTimeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Java's ZoneRulesException; thrown when a zone id is syntactically invalid. */
class ZoneRulesException(message: String) : DateTimeException(message)
