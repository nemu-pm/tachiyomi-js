package java.lang

/**
 * java.lang.Thread for a single-threaded runtime. `Thread.sleep` cannot block the JS event loop,
 * so it returns at once; sources that sleep between retries just retry immediately.
 */
object Thread {
    fun sleep(millis: Long) {}
    fun sleep(millis: Long, nanos: Int) {}
    fun currentThread(): Thread = this
    fun interrupt() {}
    fun isInterrupted(): Boolean = false
    fun interrupted(): Boolean = false
    val name: String get() = "main"
    val id: Long get() = 1L
}
