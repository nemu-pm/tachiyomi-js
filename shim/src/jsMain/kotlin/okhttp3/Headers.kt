package okhttp3

/** OkHttp Headers: ordered name/value pairs, case-insensitive lookup, original casing kept. */
class Headers private constructor(
    private val namesAndValues: List<String>,
) : Iterable<Pair<String, String>> {
    operator fun get(name: String): String? {
        var i = namesAndValues.size - 2
        while (i >= 0) {
            if (namesAndValues[i].equals(name, ignoreCase = true)) return namesAndValues[i + 1]
            i -= 2
        }
        return null
    }

    operator fun get(index: Int): Pair<String, String> = name(index) to value(index)

    fun name(index: Int): String = namesAndValues[index * 2]

    fun value(index: Int): String = namesAndValues[index * 2 + 1]

    fun values(name: String): List<String> {
        val result = mutableListOf<String>()
        for (i in namesAndValues.indices step 2) {
            if (namesAndValues[i].equals(name, ignoreCase = true)) result.add(namesAndValues[i + 1])
        }
        return result
    }

    fun names(): Set<String> {
        val result = LinkedHashSet<String>()
        for (i in namesAndValues.indices step 2) result.add(namesAndValues[i])
        return result
    }

    val size: Int get() = namesAndValues.size / 2

    @JsName("sizeFn")
    fun size(): Int = size

    fun toMultimap(): Map<String, List<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        for (i in namesAndValues.indices step 2) {
            result.getOrPut(namesAndValues[i].lowercase()) { mutableListOf() }.add(namesAndValues[i + 1])
        }
        return result
    }

    /** As the stdlib `Iterable<Pair>.toMap()` the JVM resolves this to: names as sent, last value wins. */
    fun toMap(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (i in namesAndValues.indices step 2) result[namesAndValues[i]] = namesAndValues[i + 1]
        return result
    }

    override fun iterator(): Iterator<Pair<String, String>> = (0 until size).map { this[it] }.iterator()

    fun newBuilder(): Builder = Builder().also { it.namesAndValues.addAll(namesAndValues) }

    override fun toString(): String = buildString {
        for (i in 0 until size) append(name(i)).append(": ").append(value(i)).append('\n')
    }

    override fun equals(other: Any?): Boolean = other is Headers && other.namesAndValues == namesAndValues
    override fun hashCode(): Int = namesAndValues.hashCode()

    class Builder {
        internal val namesAndValues = mutableListOf<String>()

        fun add(line: String): Builder = apply {
            val index = line.indexOf(':')
            require(index != -1) { "Unexpected header: $line" }
            add(line.substring(0, index).trim(), line.substring(index + 1))
        }

        fun add(name: String, value: String): Builder = apply {
            namesAndValues.add(name)
            namesAndValues.add(value.trim())
        }

        fun addUnsafeNonAscii(name: String, value: String): Builder = add(name, value)

        fun addAll(headers: Headers): Builder = apply {
            for (i in 0 until headers.size) add(headers.name(i), headers.value(i))
        }

        operator fun set(name: String, value: String): Builder = apply {
            removeAll(name)
            add(name, value)
        }

        fun removeAll(name: String): Builder = apply {
            var i = 0
            while (i < namesAndValues.size) {
                if (namesAndValues[i].equals(name, ignoreCase = true)) {
                    namesAndValues.removeAt(i)
                    namesAndValues.removeAt(i)
                } else {
                    i += 2
                }
            }
        }

        operator fun get(name: String): String? {
            var i = namesAndValues.size - 2
            while (i >= 0) {
                if (namesAndValues[i].equals(name, ignoreCase = true)) return namesAndValues[i + 1]
                i -= 2
            }
            return null
        }

        fun build(): Headers = Headers(namesAndValues.toList())
    }

    companion object {
        fun headersOf(vararg namesAndValues: String): Headers {
            require(namesAndValues.size % 2 == 0) { "Expected alternating header names and values" }
            val builder = Builder()
            for (i in namesAndValues.indices step 2) builder.add(namesAndValues[i], namesAndValues[i + 1])
            return builder.build()
        }

        fun Map<String, String>.toHeaders(): Headers {
            val builder = Builder()
            for ((name, value) in this) builder.add(name, value)
            return builder.build()
        }
    }
}
