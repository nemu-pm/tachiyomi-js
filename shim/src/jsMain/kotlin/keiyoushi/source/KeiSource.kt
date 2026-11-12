package keiyoushi.source

// Ported from keiyoushi core source/KeiSource.kt. Dropped for the sandbox: the reflection that
// un-lazies HttpSource.headers (the shim's is a plain getter), the checks for the app's own
// interceptors, the compression interceptor (the host transport decodes), and the zstd file
// cache of fetched filters (kept in memory for the worker's lifetime). Atomics and the
// concurrent map are plain fields: the worker runs one call at a time.

import android.util.Log
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.BaseUrlFetchInterceptor
import keiyoushi.network.BrowserCallInterceptor
import keiyoushi.network.BrowserHeadersInterceptor
import keiyoushi.network.CacheControlInterceptor
import keiyoushi.network.Fetch
import keiyoushi.network.RateLimitInterceptor
import keiyoushi.network.fetch
import keiyoushi.network.kind
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.System
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private val BASE_URL_FETCH_TIMEOUT = 10.seconds

abstract class KeiSource : HttpSource() {

    /**
     * Customizes the OkHttpClient builder. Subclasses can override this to configure timeouts,
     * add application/network interceptors, or configure cookie/cache/dns settings.
     */
    protected open fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this

    final override val client: OkHttpClient by lazy {
        network.client.newBuilder().apply {
            // first, so source interceptors only see the fetched url
            fetchableBaseUrl?.let { preferences ->
                addInterceptor(BaseUrlFetchInterceptor({ baseUrl }) { fetchBaseUrlsBlocking(preferences) })
            }

            configureClient()

            // the browser headers for requests with a Fetch
            addInterceptor(BrowserCallInterceptor({ baseUrl }, { imageFetch }))

            // allow caching when server doesn't explicitly say anything
            addNetworkInterceptor(CacheControlInterceptor())

            addNetworkInterceptor(BrowserHeadersInterceptor)

            // keep the source's rate limiter as the last network interceptor
            networkInterceptors().apply {
                val rateLimiter = firstInstanceOrNull<RateLimitInterceptor>()
                if (rateLimiter != null) {
                    remove(rateLimiter)
                    add(rateLimiter)
                }
            }
        }.build()
    }

    /**
     * Base url preferences of a source overriding [fetchBaseUrls].
     * Do not override by hand.
     */
    protected open val fetchableBaseUrl: FetchableBaseUrl? get() = null

    /**
     * Fetches the site's current base urls, in the site's order: one url, or every mirror it lists.
     *
     * Needs a `mirrors(...)` or `custom(...)` baseUrl. Runs once per source per app launch, before its first request:
     * the urls replace the mirror list, or the first one becomes the custom url and its default.
     *
     * @param client the client to use for the lookup: requests on [KeiSource.client] wait for it to finish.
     */
    protected open suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = emptyList()

    private fun fetchBaseUrlsBlocking(preferences: FetchableBaseUrl) {
        // Without the interceptor that waits for this lookup, and with its own request slots
        val lookupClient = client.newBuilder()
            .dispatcher(Dispatcher())
            .callTimeout(BASE_URL_FETCH_TIMEOUT)
            .apply { interceptors().removeAll { it is BaseUrlFetchInterceptor } }
            .build()
        try {
            val urls = runBlocking { withTimeout(BASE_URL_FETCH_TIMEOUT) { fetchBaseUrls(lookupClient) } }
                .mapNotNull { it.toHttpUrlOrNull()?.resolve("/")?.toString()?.removeSuffix("/") }
                .distinct()
            if (urls.isNotEmpty()) preferences.update(urls)
        } catch (e: Exception) {
            Log.e(name, "Failed to fetch base urls", e)
        }
    }

    /**
     * Customizes the base Headers builder. Subclasses can override this to append source-specific
     * headers.
     *
     * Note: `User-Agent` and `Referer` are set here. The other browser headers, `Origin` included, are added per
     * request kind; headers set here or on a request are kept.
     */
    protected open fun Headers.Builder.configureHeaders(): Headers.Builder = this

    final override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .configureHeaders()

    /**
     * Fetches a page of popular manga from the source.
     */
    abstract override suspend fun getPopularManga(page: Int): MangasPage

    /**
     * Whether the source supports fetching a list of latest manga updates.
     */
    override val supportsLatest get() = true

    /**
     * Fetches a page of latest manga updates from the source.
     */
    abstract override suspend fun getLatestUpdates(page: Int): MangasPage

    /**
     * Fetches a page of manga matching the query and filters.
     */
    abstract suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage

    /**
     * Fetches details of a single manga directly using its HTTP URL.
     * Used for resolving URL search queries in the browser or search bar.
     */
    protected open suspend fun getMangaByUrl(url: HttpUrl): SManga? = throw Exception("getMangaByUrl not implemented")

    /**
     * Fetches a page of manga list from given url.
     * Not needed for most sources
     */
    protected open suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val manga = getMangaByUrl(url)

        return MangasPage(listOfNotNull(manga), hasNextPage = false)
    }

    final override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        query.toHttpUrlOrNull()?.also { url ->
            return getMangasByUrl(url, page)
        }

        return getSearchMangaList(page, query, filters)
    }

    private var filterFetchInFlight = false
    private var filterFetchAttemptCount = 0
    private val maxFilterFetchAttempts = 3

    // The fetched filter data, for the life of the worker
    private var filterCache: JsonElement? = null
    private var filterCacheTime = 0L

    /**
     * Whether this source fetches its filters from the network
     * implement [fetchFilterData] when true
     */
    protected open val supportsFilterFetching: Boolean get() = false

    /**
     * Text shown as a filter header hint while filters are unavailable
     * Do not override by hand
     */
    protected open val filterFetchHint: String get() = "Tap 'Reset' to load filters"

    /**
     * Fetches filter data from the network.
     *
     * Only called when [supportsFilterFetching] is true, on a background coroutine.
     * Throwing is treated as a failed attempt and retried (see [maxFilterFetchAttempts]);
     */
    protected open suspend fun fetchFilterData(): JsonElement = JsonNull

    /**
     * Builds a [FilterList] from previously cached/fetched filter data.
     *
     * This is called synchronously every time [getFilterList] is invoked, so it must be
     * fast and must NOT perform I/O or network requests — only convert [data] into filters.
     *
     * @param data The cached filter data or null when no data is available yet
     */
    protected open fun getFilterList(data: JsonElement?): FilterList = FilterList()

    final override fun getFilterList(): FilterList {
        if (!supportsFilterFetching) return getFilterList(data = null)

        val (cached, isFresh) = readFilterCacheState()
        val parsed = cached?.let {
            try {
                getFilterList(it)
            } catch (e: Exception) {
                Log.e(name, "Failed to parse filter data", e)
                null
            }
        }

        if (parsed == null || !isFresh) {
            triggerBackgroundFilterFetch()
        }

        if (parsed != null) return parsed

        return FilterList(
            buildList {
                addAll(getFilterList(data = null))
                if (isNotEmpty()) {
                    add(Filter.Separator())
                }
                add(Filter.Header(filterFetchHint))
            },
        )
    }

    private data class FilterCacheState(val data: JsonElement?, val isFresh: Boolean)

    private fun readFilterCacheState(): FilterCacheState {
        val data = filterCache ?: return FilterCacheState(data = null, isFresh = false)
        val age = System.currentTimeMillis() - filterCacheTime
        return FilterCacheState(data, isFresh = age <= 3.days.inWholeMilliseconds)
    }

    private fun writeFilterCache(data: JsonElement) {
        filterCache = data
        filterCacheTime = System.currentTimeMillis()
    }

    private fun triggerBackgroundFilterFetch() {
        if (filterFetchAttemptCount >= maxFilterFetchAttempts) return
        if (filterFetchInFlight) return
        filterFetchInFlight = true

        GlobalScope.launch(Dispatchers.Default) {
            try {
                val data = fetchFilterData()
                writeFilterCache(data)
                filterFetchAttemptCount = 0
            } catch (e: Exception) {
                Log.e(name, "Failed to fetch filter data", e)
                filterFetchAttemptCount++
            } finally {
                filterFetchInFlight = false
            }
        }
    }

    /**
     * Fetches updated information for a manga.
     *
     * Depending on the provided flags or source availability, this may include
     * updated manga metadata, available chapters, or both.
     *
     * @param manga The manga to fetch updates for.
     * @param chapters Existing chapters of the manga
     * @param fetchDetails Whether to include updated manga details.
     * @param fetchChapters Whether to include available chapters.
     */
    abstract suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate

    private val updatesInFlight = mutableSetOf<String>()

    final override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        check(fetchDetails || fetchChapters) { "getMangaUpdate called with nothing to fetch (fetchDetails=false, fetchChapters=false)" }

        val key = manga.url
        check(updatesInFlight.add(key)) { "getMangaUpdate must not be called concurrently for same manga" }

        try {
            val update = fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters)

            return SMangaUpdate(
                manga = update.manga.apply { initialized = true },
                chapters = update.chapters,
            )
        } finally {
            updatesInFlight.remove(key)
        }
    }

    /**
     * Whether the source supports retrieving related manga.
     *
     * Only works on Komikku
     */
    override val supportsRelatedMangas get() = false

    /**
     * Whether to fall back to searching the source by the manga's title if a direct related manga list is unavailable.
     *
     * Only works on Komikku
     */
    protected open val supportRelatedMangasBySearch get() = false

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override val disableRelatedMangasBySearch get() = !supportRelatedMangasBySearch

    @Deprecated("Hidden", level = DeprecationLevel.HIDDEN)
    final override val disableRelatedMangas get() = false

    /**
     * Fetches a list of related manga for the specified manga.
     *
     * Only works on Komikku
     */
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    /**
     * Returns the absolute web URL for the provided manga.
     */
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    /**
     * Returns the absolute web URL for the provided chapter.
     */
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     */
    abstract override suspend fun getPageList(chapter: SChapter): List<Page>

    /**
     * How page images and the covers the app loads go out: [Fetch.IMAGE] sends the headers a browser sends for them;
     * [Fetch.NONE] sends them as built.
     */
    protected open val imageFetch: Fetch get() = Fetch.IMAGE

    /**
     * Builds the request for a page's image. A GET without a [Fetch] is sent with [imageFetch].
     */
    protected open fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers)
        .build()

    final override fun imageRequest(page: Page): Request {
        val request = getImageRequest(page)
        val image = request.kind == null && request.method == "GET"
        return if (image) request.newBuilder().fetch(imageFetch).build() else request
    }
}
