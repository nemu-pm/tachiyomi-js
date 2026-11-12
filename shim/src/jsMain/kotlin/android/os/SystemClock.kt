package android.os

object SystemClock {
    private val start: Double = now()

    private fun now(): Double = js("(typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now()") as Double

    /** Milliseconds since this runtime started; monotonic, like the Android one. */
    fun elapsedRealtime(): Long = (now() - start).toLong()

    fun elapsedRealtimeNanos(): Long = ((now() - start) * 1_000_000).toLong()

    fun uptimeMillis(): Long = elapsedRealtime()

    fun currentThreadTimeMillis(): Long = elapsedRealtime()

    /** Busy-waits; there is no thread to sleep in a synchronous worker. */
    fun sleep(ms: Long) {
        val until = now() + ms
        while (now() < until) { /* spin */ }
    }
}
