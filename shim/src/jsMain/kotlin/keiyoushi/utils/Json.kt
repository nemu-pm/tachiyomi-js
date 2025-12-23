/**
 * JS-compatible implementation of keiyoushi.utils.Json
 * 
 * This provides the same API as keiyoushi's core/Json.kt but uses
 * JS-compatible alternatives:
 * - Uses body.string() instead of body.byteStream() (not available in JS)
 * - Uses decodeFromString instead of decodeFromStream (not available in JS)
 * - Creates Json instance directly instead of using injekt
 */
package keiyoushi.utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Response

// Global Json instance (JS doesn't have injekt, so we create directly)
val jsonInstance: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Parses JSON string into an object of type [T].
 */
inline fun <reified T> String.parseAs(json: Json = jsonInstance): T =
    json.decodeFromString(this)

/**
 * Parses JSON string into an object of type [T], applying a [transform] function to the string before parsing.
 */
inline fun <reified T> String.parseAs(json: Json = jsonInstance, transform: (String) -> String): T =
    transform(this).parseAs(json)

/**
 * Parses the response body into an object of type [T].
 * 
 * Note: Uses body.string() instead of decodeFromStream (not available in JS)
 */
inline fun <reified T> Response.parseAs(json: Json = jsonInstance): T =
    json.decodeFromString(body.string())

/**
 * Parses the response body into an object of type [T], applying a transformation to the raw JSON string before parsing.
 */
inline fun <reified T> Response.parseAs(json: Json = jsonInstance, transform: (String) -> String): T =
    body.string().parseAs(json, transform)

/**
 * Parses a [JsonElement] into an object of type [T].
 */
inline fun <reified T> JsonElement.parseAs(json: Json = jsonInstance): T =
    json.decodeFromJsonElement(this)

/**
 * Serializes the object to a JSON string.
 */
inline fun <reified T> T.toJsonString(json: Json = jsonInstance): String =
    json.encodeToString(this)

