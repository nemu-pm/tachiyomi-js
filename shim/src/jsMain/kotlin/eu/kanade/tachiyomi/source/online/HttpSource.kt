package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import java.security.MessageDigest

/**
 * extensions-lib 1.6 HttpSource: the suspend `Source` API only, no Rx and no request/parse pairs.
 *
 * `headers` is a plain getter rather than a `lazy` so that sources whose base URL changes at
 * runtime (mirror preferences) get a fresh `Referer`; upstream's `KeiSource` swaps the lazy out
 * by reflection for the same reason.
 */
@Suppress("unused")
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper by lazy { Injekt.get(NetworkHelper::class) }

    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers get() = headersBuilder().build()

    open val client: OkHttpClient
        get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.encodeToByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", network.defaultUserAgentProvider())

    override fun toString() = "$name (${lang.uppercase()})"

    /** Resolves a page's image URL when the page list only carried a page URL. */
    open suspend fun getImageUrl(page: Page): String =
        page.imageUrl ?: throw IllegalStateException("Page has no imageUrl; the source must override getImageUrl")

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    /** Host entry point for the protected [imageRequest]; generated main.kt calls this. */
    fun imageRequestForHost(page: Page): Request = imageRequest(page)

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val url = orig.replace(" ", "%20").toHttpUrlOrNull() ?: return orig
            val out = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "") + (url.encodedFragment?.let { "#$it" } ?: "")
            out
        } catch (e: Exception) {
            orig
        }
    }

    abstract fun getMangaUrl(manga: SManga): String

    abstract fun getChapterUrl(chapter: SChapter): String

    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    override fun getFilterList() = FilterList()
}
