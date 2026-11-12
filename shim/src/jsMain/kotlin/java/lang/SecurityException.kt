package java.lang

/** Thrown by sources when a site needs a login or an in-app purchase; the runtime only shows the message. */
open class SecurityException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)
