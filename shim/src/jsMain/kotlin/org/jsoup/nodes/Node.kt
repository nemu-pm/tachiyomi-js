package org.jsoup.nodes

import com.fleeksoft.ksoup.nodes.Document as KsoupDocument
import com.fleeksoft.ksoup.nodes.Element as KsoupElement
import com.fleeksoft.ksoup.nodes.Node as KsoupNode
import com.fleeksoft.ksoup.nodes.TextNode as KsoupTextNode

/**
 * Jsoup Node compatibility wrapper over Ksoup: the parts of the node API that sources reach
 * for when they walk mixed element/text children (markdown conversion, description cleanup).
 */
open class Node(internal val nodeImpl: KsoupNode) {
    open fun nodeName(): String = nodeImpl.nodeName()

    open fun attr(attributeKey: String): String = nodeImpl.attr(attributeKey)

    open fun attr(attributeKey: String, attributeValue: String): Node {
        nodeImpl.attr(attributeKey, attributeValue)
        return this
    }

    open fun hasAttr(attributeKey: String): Boolean = nodeImpl.hasAttr(attributeKey)

    open fun absUrl(attributeKey: String): String = nodeImpl.absUrl(attributeKey)

    fun childNodes(): List<Node> = nodeImpl.childNodes().map(::wrapNode)

    fun childNodeSize(): Int = nodeImpl.childNodeSize()

    fun childNode(index: Int): Node = wrapNode(nodeImpl.childNode(index))

    fun parentNode(): Node? = nodeImpl.parentNode()?.let(::wrapNode)

    fun nextSibling(): Node? = nodeImpl.nextSibling()?.let(::wrapNode)

    fun previousSibling(): Node? = nodeImpl.previousSibling()?.let(::wrapNode)

    fun siblingIndex(): Int = nodeImpl.siblingIndex()

    fun firstChild(): Node? = nodeImpl.firstChild()?.let(::wrapNode)

    fun lastChild(): Node? = nodeImpl.lastChild()?.let(::wrapNode)

    open fun outerHtml(): String = nodeImpl.outerHtml()

    open fun remove(): Node {
        nodeImpl.remove()
        return this
    }

    override fun toString(): String = nodeImpl.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return nodeImpl == other.nodeImpl
    }

    override fun hashCode(): Int = nodeImpl.hashCode()
}

/**
 * Jsoup TextNode compatibility wrapper over Ksoup.
 */
class TextNode internal constructor(internal val textImpl: KsoupTextNode) : Node(textImpl) {
    constructor(text: String) : this(KsoupTextNode(text))

    fun text(): String = textImpl.text()

    fun text(text: String): TextNode {
        textImpl.text(text)
        return this
    }

    /** The unnormalised text, as Jsoup's `getWholeText()`; Kotlin callers read it as `wholeText`. */
    val wholeText: String get() = textImpl.getWholeText()

    fun getWholeText(): String = wholeText

    fun isBlank(): Boolean = textImpl.isBlank()

    companion object {
        fun createFromEncoded(encodedText: String): TextNode = TextNode(KsoupTextNode.createFromEncoded(encodedText))
    }
}

/** Wraps a Ksoup node in the matching Jsoup-compatible class. */
internal fun wrapNode(node: KsoupNode): Node = when (node) {
    is KsoupDocument -> Document(node)
    is KsoupElement -> Element(node)
    is KsoupTextNode -> TextNode(node)
    else -> Node(node)
}
