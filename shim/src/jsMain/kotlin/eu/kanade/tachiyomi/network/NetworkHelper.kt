package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.OkHttpClient

/**
 * extensions-lib 1.6 NetworkHelper. One shared client; the host's fetch layer owns cookies,
 * caching and Cloudflare handling, so `cloudflareClient` is the same client.
 */
class NetworkHelper(@Suppress("UNUSED_PARAMETER") context: Context) {
    constructor() : this(Context())

    val client: OkHttpClient = OkHttpClient.Builder().build()

    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
