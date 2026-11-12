package kotlinx.coroutines.sync

/** Semaphore for the single-threaded runtime: permits are counted, nothing ever waits (see [Mutex]). */
class Semaphore(private val permits: Int, acquiredPermits: Int = 0) {
    var availablePermits: Int = permits - acquiredPermits
        private set

    suspend fun acquire() {
        availablePermits--
    }

    fun tryAcquire(): Boolean {
        if (availablePermits <= 0) return false
        availablePermits--
        return true
    }

    fun release() {
        availablePermits++
    }
}

suspend inline fun <T> Semaphore.withPermit(action: () -> T): T {
    acquire()
    try {
        return action()
    } finally {
        release()
    }
}
