package org.jsoup.select

/**
 * Jsoup Evaluator: sources build the common ones directly (`Evaluator.Tag("br")`) instead of
 * writing a selector. Each carries the equivalent CSS query, which the Element/Elements
 * `select(Evaluator)` overloads hand to Ksoup.
 */
abstract class Evaluator(val cssQuery: String) {
    abstract fun matches(root: org.jsoup.nodes.Element, element: org.jsoup.nodes.Element): Boolean

    override fun toString(): String = cssQuery

    open class QueryEvaluator(cssQuery: String) : Evaluator(cssQuery) {
        override fun matches(root: org.jsoup.nodes.Element, element: org.jsoup.nodes.Element): Boolean =
            element.`is`(cssQuery)
    }

    class Tag(tagName: String) : QueryEvaluator(tagName)
    class Id(id: String) : QueryEvaluator("#$id")
    class Class(className: String) : QueryEvaluator(".$className")
    class Attribute(key: String) : QueryEvaluator("[$key]")
    class AttributeWithValue(key: String, value: String) : QueryEvaluator("[$key=\"$value\"]")
    class AttributeWithValueContaining(key: String, value: String) : QueryEvaluator("[$key*=\"$value\"]")
    class AttributeWithValueStarting(key: String, value: String) : QueryEvaluator("[$key^=\"$value\"]")
    class AttributeWithValueEnding(key: String, value: String) : QueryEvaluator("[$key$=\"$value\"]")
    class AllElements : QueryEvaluator("*")
}
