package org.jsoup

import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.toString
import com.fleeksoft.ksoup.Ksoup as KsoupImpl

/**
 * Jsoup compatibility wrapper over Ksoup.
 *
 * Ksoup is a Kotlin Multiplatform port of Jsoup that provides real HTML parsing.
 * This wrapper maintains API compatibility with extension code that imports org.jsoup.
 */
object Jsoup {
    fun parse(html: String): Document = Document(KsoupImpl.parse(html))

    fun parse(html: String, baseUri: String): Document =
        Document(KsoupImpl.parse(html = html, baseUri = baseUri))

    fun parse(html: String, baseUri: String, parser: Parser): Document =
        Document(KsoupImpl.parse(html = html, baseUri = baseUri, parser = parser.impl))

    fun parse(html: String, parser: Parser): Document = parse(html, "", parser)

    /** Streams are fully buffered; a null charset means UTF-8 (no meta sniffing). */
    fun parse(input: InputStream, charsetName: String?, baseUri: String): Document =
        parse(decode(input, charsetName), baseUri)

    fun parse(input: InputStream, charsetName: String?, baseUri: String, parser: Parser): Document =
        parse(decode(input, charsetName), baseUri, parser)

    fun parseBodyFragment(bodyHtml: String): Document = parse(bodyHtml)

    fun parseBodyFragment(bodyHtml: String, baseUri: String): Document = parse(bodyHtml, baseUri)

    private fun decode(input: InputStream, charsetName: String?): String {
        val bytes = input.use { it.readBytes() }
        return if (charsetName == null) bytes.decodeToString() else bytes.toString(Charset.forName(charsetName))
    }
}
