package java.util.concurrent.atomic

// JS is single-threaded, so these are plain boxes with the java.util.concurrent.atomic API.

class AtomicReference<V>(private var value: V) {
    // java.util.concurrent.atomic.AtomicReference() starts out holding null.
    @Suppress("UNCHECKED_CAST")
    constructor() : this(null as V)

    fun get(): V = value
    fun set(newValue: V) { value = newValue }
    fun lazySet(newValue: V) { value = newValue }
    fun getAndSet(newValue: V): V = value.also { value = newValue }
    fun compareAndSet(expect: V, update: V): Boolean {
        if (value != expect) return false
        value = update
        return true
    }
    fun updateAndGet(update: (V) -> V): V { value = update(value); return value }
    fun getAndUpdate(update: (V) -> V): V = value.also { value = update(it) }
    override fun toString(): String = value.toString()
}

class AtomicBoolean(private var value: Boolean = false) {
    fun get(): Boolean = value
    fun set(newValue: Boolean) { value = newValue }
    fun lazySet(newValue: Boolean) { value = newValue }
    fun getAndSet(newValue: Boolean): Boolean = value.also { value = newValue }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        if (value != expect) return false
        value = update
        return true
    }
    override fun toString(): String = value.toString()
}

class AtomicInteger(private var value: Int = 0) {
    fun get(): Int = value
    fun set(newValue: Int) { value = newValue }
    fun lazySet(newValue: Int) { value = newValue }
    fun getAndSet(newValue: Int): Int = value.also { value = newValue }
    fun compareAndSet(expect: Int, update: Int): Boolean {
        if (value != expect) return false
        value = update
        return true
    }
    fun incrementAndGet(): Int = ++value
    fun decrementAndGet(): Int = --value
    fun getAndIncrement(): Int = value++
    fun getAndDecrement(): Int = value--
    fun addAndGet(delta: Int): Int { value += delta; return value }
    fun getAndAdd(delta: Int): Int = value.also { value += delta }
    fun updateAndGet(update: (Int) -> Int): Int { value = update(value); return value }
    fun getAndUpdate(update: (Int) -> Int): Int = value.also { value = update(it) }
    fun toInt(): Int = value
    fun toLong(): Long = value.toLong()
    override fun toString(): String = value.toString()
}

class AtomicLong(private var value: Long = 0L) {
    fun get(): Long = value
    fun set(newValue: Long) { value = newValue }
    fun lazySet(newValue: Long) { value = newValue }
    fun getAndSet(newValue: Long): Long = value.also { value = newValue }
    fun compareAndSet(expect: Long, update: Long): Boolean {
        if (value != expect) return false
        value = update
        return true
    }
    fun incrementAndGet(): Long = ++value
    fun decrementAndGet(): Long = --value
    fun getAndIncrement(): Long = value++
    fun getAndDecrement(): Long = value--
    fun addAndGet(delta: Long): Long { value += delta; return value }
    fun getAndAdd(delta: Long): Long = value.also { value += delta }
    fun updateAndGet(update: (Long) -> Long): Long { value = update(value); return value }
    fun getAndUpdate(update: (Long) -> Long): Long = value.also { value = update(it) }
    fun toLong(): Long = value
    override fun toString(): String = value.toString()
}
