/**
 * Template for generating Main.kt entry point for Tachiyomi extensions.
 * This file contains the runtime bridge between Kotlin/JS and the extension API.
 */
object MainKtTemplate {
    
    /**
     * Generate Main.kt content for an extension.
     * @param packageName Full package name (e.g., "eu.kanade.tachiyomi.extension.all.mangadex")
     * @param extClassName Extension class name (e.g., "MangaDex")
     */
    fun generate(packageName: String, extClassName: String): String {
        return TEMPLATE
            .replace("{{PACKAGE_NAME}}", packageName)
            .replace("{{EXT_CLASS_NAME}}", extClassName)
    }
    
    private val TEMPLATE = """
@file:OptIn(ExperimentalJsExport::class)

package tachiyomi.generated

import {{PACKAGE_NAME}}.{{EXT_CLASS_NAME}}
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlin.js.JsExport

private val json = Json { 
    prettyPrint = false
    ignoreUnknownKeys = true
}

// Log buffer for capturing println output
private val logBuffer = mutableListOf<String>()

private fun log(msg: String) {
    logBuffer.add(msg)
    println(msg)
}

@JsExport
fun getLogs(): String {
    val logs = logBuffer.toList()
    logBuffer.clear()
    return json.encodeToString(logs)
}

// Result wrapper - all exports return this format
private inline fun <reified T> success(data: T): String {
    return buildJsonObject {
        put("ok", true)
        put("data", json.encodeToJsonElement(data))
    }.toString()
}

private fun successJson(data: JsonElement): String {
    return buildJsonObject {
        put("ok", true)
        put("data", data)
    }.toString()
}

private fun error(e: Throwable): String {
    val stackTrace = e.stackTraceToString()
    return buildJsonObject {
        put("ok", false)
        putJsonObject("error") {
            put("type", e::class.simpleName ?: "Unknown")
            put("message", e.message ?: "No message")
            put("stack", stackTrace)
            putJsonArray("logs") { logBuffer.forEach { add(it) } }
        }
    }.toString().also { logBuffer.clear() }
}

private val instance = {{EXT_CLASS_NAME}}()
private val sources: List<Source> = when (instance) {
    is SourceFactory -> instance.createSources()
    is Source -> listOf(instance)
    else -> emptyList()
}

// Source lookup by ID (String representation of Long)
private val sourcesById: Map<String, Source> = sources.associateBy { it.id.toString() }

// Cached filters per source (for state management)
private val filterCache = mutableMapOf<String, FilterList>()

// DTO classes for JSON serialization
@Serializable
data class MangaDto(
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: Int,
    val thumbnailUrl: String?,
    val initialized: Boolean
)

@Serializable
data class ChapterDto(
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Float,
    val scanlator: String?
)

@Serializable
data class PageDto(
    val index: Int,
    val url: String,
    val imageUrl: String?
)

@Serializable
data class MangasPageDto(
    val mangas: List<MangaDto>,
    val hasNextPage: Boolean
)

private fun SManga.toDto() = MangaDto(
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
    status = status,
    thumbnailUrl = thumbnail_url,
    initialized = initialized
)

private fun SChapter.toDto() = ChapterDto(
    url = url,
    name = name,
    dateUpload = date_upload,
    chapterNumber = chapter_number,
    scanlator = scanlator
)

private fun eu.kanade.tachiyomi.source.model.Page.toDto() = PageDto(
    index = index,
    url = url,
    imageUrl = imageUrl
)

// ============================================================================
// Manifest: Returns all sources metadata (call once on load)
// ============================================================================

@JsExport
fun getManifest(): String = try {
    val sourcesJson = buildJsonArray {
        for (src in sources) {
            add(buildJsonObject {
                put("id", src.id.toString())
                put("name", src.name)
                put("lang", src.lang)
                if (src is HttpSource) {
                    put("baseUrl", src.baseUrl)
                }
                if (src is CatalogueSource) {
                    put("supportsLatest", src.supportsLatest)
                }
            })
        }
    }
    successJson(sourcesJson)
} catch (e: Throwable) { error(e) }

// ============================================================================
// Filter Schema: Serialize filters for UI rendering
// ============================================================================

private fun serializeFilter(filter: Filter<*>): JsonObject = buildJsonObject {
    put("name", filter.name)
    when (filter) {
        is Filter.Header -> put("type", "header")
        is Filter.Separator -> put("type", "separator")
        is Filter.CheckBox -> {
            put("type", "checkbox")
            put("state", filter.state)
        }
        is Filter.TriState -> {
            put("type", "tristate")
            put("state", filter.state)
        }
        is Filter.Text -> {
            put("type", "text")
            put("state", filter.state)
        }
        is Filter.Select<*> -> {
            put("type", "select")
            put("state", filter.state)
            putJsonArray("values") {
                filter.values.forEach { add(it.toString()) }
            }
        }
        is Filter.Sort -> {
            put("type", "sort")
            putJsonArray("values") {
                filter.values.forEach { add(it) }
            }
            if (filter.state != null) {
                putJsonObject("state") {
                    put("index", filter.state!!.index)
                    put("ascending", filter.state!!.ascending)
                }
            }
        }
        is Filter.Group<*> -> {
            put("type", "group")
            putJsonArray("filters") {
                @Suppress("UNCHECKED_CAST")
                (filter.state as? List<Filter<*>>)?.forEach { 
                    add(serializeFilter(it))
                }
            }
        }
    }
}

@JsExport
fun getFilterList(sourceId: String): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val filters = src.getFilterList()
    filterCache[sourceId] = filters
    val filtersJson = buildJsonArray {
        filters.forEach { add(serializeFilter(it)) }
    }
    successJson(filtersJson)
} catch (e: Throwable) { error(e) }

/**
 * Reset filters to source default state.
 * Call this before starting a new search to clear previous filter state.
 */
@JsExport
fun resetFilters(sourceId: String): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    filterCache[sourceId] = src.getFilterList()
    successJson(buildJsonObject { put("ok", true) })
} catch (e: Throwable) { error(e) }

/**
 * Apply filter state from UI.
 * @param sourceId Source identifier
 * @param filterStateJson JSON array of filter state updates:
 *   [{ "index": 0, "state": true }, { "index": 2, "state": 1 }, ...]
 *   For Group filters: { "index": 3, "filters": [{ "index": 0, "state": true }] }
 *   For Sort filters: { "index": 5, "state": { "index": 1, "ascending": false } }
 */
@JsExport
fun applyFilterState(sourceId: String, filterStateJson: String): String = try {
    val filters = filterCache[sourceId]
        ?: throw Exception("Filters not loaded for source: ${"$"}sourceId. Call getFilterList first.")
    val stateUpdates = Json.parseToJsonElement(filterStateJson).jsonArray
    
    for (update in stateUpdates) {
        val obj = update.jsonObject
        val index = obj["index"]?.jsonPrimitive?.intOrNull ?: continue
        applyStateToFilter(filters.getOrNull(index) ?: continue, obj)
    }
    
    successJson(buildJsonObject { put("ok", true) })
} catch (e: Throwable) { error(e) }

private fun applyStateToFilter(filter: Filter<*>, state: JsonObject) {
    when (filter) {
        is Filter.CheckBox -> {
            state["state"]?.jsonPrimitive?.booleanOrNull?.let { filter.state = it }
        }
        is Filter.TriState -> {
            state["state"]?.jsonPrimitive?.intOrNull?.let { filter.state = it }
        }
        is Filter.Text -> {
            state["state"]?.jsonPrimitive?.contentOrNull?.let { filter.state = it }
        }
        is Filter.Select<*> -> {
            state["state"]?.jsonPrimitive?.intOrNull?.let { filter.state = it }
        }
        is Filter.Sort -> {
            state["state"]?.jsonObject?.let { sortState ->
                val idx = sortState["index"]?.jsonPrimitive?.intOrNull ?: 0
                val asc = sortState["ascending"]?.jsonPrimitive?.booleanOrNull ?: false
                filter.state = Filter.Sort.Selection(idx, asc)
            }
        }
        is Filter.Group<*> -> {
            val groupFilters = state["filters"]?.jsonArray ?: return
            @Suppress("UNCHECKED_CAST")
            val childFilters = filter.state as? List<Filter<*>> ?: return
            for (childUpdate in groupFilters) {
                val childObj = childUpdate.jsonObject
                val childIndex = childObj["index"]?.jsonPrimitive?.intOrNull ?: continue
                applyStateToFilter(childFilters.getOrNull(childIndex) ?: continue, childObj)
            }
        }
        else -> { /* Header, Separator - no state to apply */ }
    }
}

// ============================================================================
// Data Methods (use sourceId instead of index)
// ============================================================================

@JsExport
fun getPopularManga(sourceId: String, page: Int): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val result = src.getPopularManga(page)
    success(MangasPageDto(
        mangas = result.mangas.map { it.toDto() },
        hasNextPage = result.hasNextPage
    ))
} catch (e: Throwable) { error(e) }

@JsExport
fun getLatestUpdates(sourceId: String, page: Int): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val result = src.getLatestUpdates(page)
    success(MangasPageDto(
        mangas = result.mangas.map { it.toDto() },
        hasNextPage = result.hasNextPage
    ))
} catch (e: Throwable) { error(e) }

@JsExport
fun searchManga(sourceId: String, page: Int, query: String): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val filters = filterCache[sourceId] ?: FilterList()
    val result = src.getSearchManga(page, query, filters)
    success(MangasPageDto(
        mangas = result.mangas.map { it.toDto() },
        hasNextPage = result.hasNextPage
    ))
} catch (e: Throwable) { error(e) }

@JsExport
fun getMangaDetails(sourceId: String, mangaUrl: String): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val manga = SManga.create().apply { url = mangaUrl }
    val result = src.getMangaDetails(manga)
    // Copy URL from input manga - getMangaDetails doesn't always set it
    if (result.url.isEmpty()) result.url = mangaUrl
    success(result.toDto())
} catch (e: Throwable) { error(e) }

@JsExport
fun getChapterList(sourceId: String, mangaUrl: String): String = try {
    val src = sourcesById[sourceId] as? CatalogueSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val manga = SManga.create().apply { url = mangaUrl }
    val result = src.getChapterList(manga)
    success(result.map { it.toDto() })
} catch (e: Throwable) { error(e) }

@JsExport
fun getPageList(sourceId: String, chapterUrl: String): String = try {
    val src = sourcesById[sourceId] as? HttpSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val chapter = SChapter.create().apply { url = chapterUrl }
    val result = src.getPageList(chapter)
    success(result.map { it.toDto() })
} catch (e: Throwable) { error(e) }

/**
 * Fetch an image through the source's OkHttp client (with interceptors).
 * This is required for sources that use image descrambling/processing.
 * Returns base64-encoded image bytes.
 */
@JsExport
fun fetchImage(sourceId: String, pageUrl: String, pageImageUrl: String): String = try {
    val src = sourcesById[sourceId] as? HttpSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    val page = eu.kanade.tachiyomi.source.model.Page(0, pageUrl, pageImageUrl)
    val request = src.imageRequest(page)
    
    // Execute through client WITH interceptors (needed for image descrambling)
    val response = src.client.newCall(request).execute()
    val bytes = response.body.bytes()
    response.close()
    
    // Return base64-encoded image
    val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
    success(base64)
} catch (e: Throwable) { error(e) }

/**
 * Get the source's default headers (includes Referer from headersBuilder).
 * Returns JSON object with header name-value pairs.
 */
@JsExport
fun getHeaders(sourceId: String): String = try {
    val src = sourcesById[sourceId] as? HttpSource
        ?: throw Exception("Source not found: ${"$"}sourceId")
    
    val headersMap = mutableMapOf<String, String>()
    for (name in src.headers.names()) {
        headersMap[name] = src.headers.get(name) ?: ""
    }
    success(headersMap)
} catch (e: Throwable) { error(e) }

// ============================================================================
// Preferences API
// ============================================================================

/**
 * Get settings schema for a source. 
 * Invokes setupPreferenceScreen to capture preference definitions.
 */
@JsExport
fun getSettingsSchema(sourceId: String): String = try {
    val src = sourcesById[sourceId]
        ?: throw Exception("Source not found: ${"$"}sourceId")
    
    // Clear any existing schema
    androidx.preference.PreferenceRegistry.clear()
    
    // If source is ConfigurableSource, call setupPreferenceScreen
    if (src is eu.kanade.tachiyomi.source.ConfigurableSource) {
        val context = android.content.Context()
        val screen = androidx.preference.PreferenceScreen(context)
        src.setupPreferenceScreen(screen)
    }
    
    // Return captured schema
    success(androidx.preference.PreferenceRegistry.getAllSchemasJson())
} catch (e: Throwable) { error(e) }

// ============================================================================
// Legacy index-based API (deprecated)
// ============================================================================

@JsExport
fun getSourceCount(): Int = sources.size

@JsExport
fun getSourceInfo(index: Int): String {
    val src = sources.getOrNull(index) ?: return success(emptyMap<String, String>())
    val httpSrc = src as? HttpSource
    return success(mapOf(
        "id" to src.id.toString(),
        "name" to src.name,
        "lang" to src.lang,
        "baseUrl" to (httpSrc?.baseUrl ?: "")
    ))
}
""".trimIndent()
}

