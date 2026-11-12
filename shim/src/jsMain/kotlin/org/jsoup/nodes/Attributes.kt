package org.jsoup.nodes

import com.fleeksoft.ksoup.nodes.Attributes as KsoupAttributes

/** One attribute of an element; `key`/`value` match the JVM's synthetic `getKey()`/`getValue()`. */
class Attribute(val key: String, val value: String) {
    fun getKey(): String = key
    fun getValue(): String = value
    fun hasDeclaredValue(): Boolean = value.isNotEmpty()
    override fun toString(): String = if (value.isEmpty()) key else "$key=\"$value\""
}

/**
 * Jsoup Attributes compatibility wrapper over Ksoup. Snapshot semantics: sources only iterate
 * (`asList().filter { it.key ... }`), so edits go through `Element.attr()`.
 */
class Attributes internal constructor(private val impl: KsoupAttributes) : Iterable<Attribute> {
    override fun iterator(): Iterator<Attribute> = asList().iterator()

    fun asList(): List<Attribute> = impl.asList().map { Attribute(it.key, it.value) }

    fun get(key: String): String = impl.get(key)

    fun getIgnoreCase(key: String): String = impl.getIgnoreCase(key)

    fun hasKey(key: String): Boolean = impl.hasKey(key)

    fun hasKeyIgnoreCase(key: String): Boolean = impl.hasKeyIgnoreCase(key)

    fun size(): Int = impl.size()

    fun isEmpty(): Boolean = impl.isEmpty()

    override fun toString(): String = impl.toString()
}
