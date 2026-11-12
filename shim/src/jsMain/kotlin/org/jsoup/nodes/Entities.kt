package org.jsoup.nodes

import com.fleeksoft.ksoup.nodes.Entities as KsoupEntities

/** HTML entity escaping, over Ksoup's tables. */
object Entities {
    fun unescape(string: String): String = KsoupEntities.unescape(string)

    fun escape(string: String): String = KsoupEntities.escape(string)

    fun isNamedEntity(name: String): Boolean = KsoupEntities.isNamedEntity(name)
}
