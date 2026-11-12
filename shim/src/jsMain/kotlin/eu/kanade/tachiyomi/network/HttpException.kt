package eu.kanade.tachiyomi.network

/** Thrown by `Call.awaitSuccess()` when the response is not 2xx, as in extensions-lib 1.6. */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
