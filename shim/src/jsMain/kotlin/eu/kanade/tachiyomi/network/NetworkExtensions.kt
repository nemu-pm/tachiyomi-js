package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

// extensions-lib 1.6 request builders.

val DEFAULT_HEADERS: Headers = Headers.Builder().build()
val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build()
val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = GET(url.toHttpUrl(), headers, cache)

fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .post(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun PUT(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .put(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun DELETE(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .delete(body)
    .headers(headers)
    .cacheControl(cache)
    .build()

/** HTTP is synchronous under the host bridge, so awaiting a call is just executing it. */
suspend fun Call.await(): Response = execute()

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

fun interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

/** Progress is not observable through the sync bridge; the listener is called once on completion by the host. */
fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call =
    newCall(request.newBuilder().cacheControl(CacheControl.Builder().noStore().build()).build())
