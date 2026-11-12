package android.util

/**
 * android.util.LruCache: a bounded map that evicts the least recently used entry first.
 * [sizeOf] counts every entry as 1 unless overridden, as on Android.
 */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize <= 0" }
    }

    private val map = LinkedHashMap<K, V>()
    private var currentSize = 0

    operator fun get(key: K): V? {
        map[key]?.let { value ->
            // Re-insert so the entry becomes the most recently used.
            map.remove(key)
            map[key] = value
            return value
        }
        val created = create(key) ?: return null
        put(key, created)
        return created
    }

    fun put(key: K, value: V): V? {
        val previous = map.remove(key)
        if (previous != null) currentSize -= sizeOf(key, previous)
        map[key] = value
        currentSize += sizeOf(key, value)
        trimToSize(maxSize)
        return previous
    }

    fun remove(key: K): V? {
        val previous = map.remove(key) ?: return null
        currentSize -= sizeOf(key, previous)
        entryRemoved(false, key, previous, null)
        return previous
    }

    fun trimToSize(maxSize: Int) {
        while (currentSize > maxSize && map.isNotEmpty()) {
            val eldest = map.entries.first()
            map.remove(eldest.key)
            currentSize -= sizeOf(eldest.key, eldest.value)
            entryRemoved(true, eldest.key, eldest.value, null)
        }
    }

    fun evictAll() = trimToSize(-1)

    fun size(): Int = currentSize
    fun maxSize(): Int = maxSize
    fun snapshot(): Map<K, V> = LinkedHashMap(map)

    protected open fun create(key: K): V? = null
    protected open fun sizeOf(key: K, value: V): Int = 1
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}
}
