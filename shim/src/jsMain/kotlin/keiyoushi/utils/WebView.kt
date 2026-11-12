package keiyoushi.utils

// Stub for keiyoushi core utils/WebView.kt: the public surface with the same signatures, so
// sources that only sometimes reach for a WebView compile; running one throws. The Android
// WebView types come from the shim's android.webkit placeholders.

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val NO_WEBVIEW = "WebView is not available in the tachiyomi-js sandbox"

class RenderProcessGoneException internal constructor(message: String = "WebView render process gone") : Exception(message)

class WebViewTimeoutException internal constructor(timeout: Duration) : Exception("WebView timed out after $timeout")

enum class InjectionEvent(internal val value: Int) {
    DOCUMENT_START(0),
    DOCUMENT_END(1),
}

fun interface ScriptHandle {
    fun remove()
}

interface WebMessageReplyProxy {
    val sourceOrigin: String?
    val isMainFrame: Boolean?
    fun postMessage(message: String)
    fun evaluateJs(script: String, callback: ((String?) -> Unit)? = null)
}

@Suppress("UNUSED_PARAMETER", "unused")
class WebViewScope<T> internal constructor() {
    fun resolve(value: T) {}
    fun reject(error: Throwable) {}

    var javaScriptEnabled: Boolean = true
    var domStorageEnabled: Boolean = true
    var blockImages: Boolean = false
    var useWideViewPort: Boolean = false
    var loadWithOverviewMode: Boolean = false
    var userAgent: String = ""

    fun setOriginMatchedHeader(name: String, value: String, allowedOriginRules: Set<String>) {}
    val originMatchedHeaderSupported: Boolean get() = false

    fun onPageStarted(block: (url: String) -> Unit) {}
    fun onPageFinished(block: (url: String) -> Unit) {}
    fun onReceivedError(block: (request: WebResourceRequest, error: WebResourceError) -> Unit) {}
    fun interceptRequest(block: (WebResourceRequest) -> WebResourceResponse?) {}
    fun proxyRequest(request: WebResourceRequest, client: OkHttpClient, edit: Request.Builder.() -> Unit = {}): WebResourceResponse? = null
    fun webMessageBridge(
        name: String,
        allowedOriginRules: Set<String> = setOf("*"),
        worldName: String = "",
        handler: (message: String, replyProxy: WebMessageReplyProxy) -> Unit,
    ) {}
    fun webMessageBridge(
        name: String,
        allowedOriginRules: Set<String> = setOf("*"),
        worldName: String = "",
        handler: (message: String) -> Unit,
    ) {}
    fun injectAtDocumentStart(script: String, allowedOriginRules: Set<String> = setOf("*")): ScriptHandle = ScriptHandle {}
    fun addJavaScriptOnEvent(
        script: String,
        event: InjectionEvent = InjectionEvent.DOCUMENT_START,
        worldName: String = "",
        allowedOriginRules: Set<String> = setOf("*"),
    ): ScriptHandle = ScriptHandle {}
    fun loadUrl(url: String, headers: Map<String, String> = emptyMap()) {}
    fun loadData(baseUrl: String, html: String, mimeType: String = "text/html") {}
    @NativeWebViewApi
    fun withNativeWebView(block: (WebView) -> Unit) {}
    fun evaluateJs(script: String, callback: ((String) -> Unit)? = null) {}
    fun stopLoading() {}
    fun poll(interval: Duration = 500.milliseconds, block: () -> Unit) {}
}

@Suppress("UNUSED_PARAMETER")
suspend fun <T> runWebView(timeout: Duration = 30.seconds, configure: WebViewScope<T>.() -> Unit): T =
    throw UnsupportedOperationException(NO_WEBVIEW)

@Suppress("UNUSED_PARAMETER")
fun <T> runWebViewBlocking(call: Call, timeout: Duration = 30.seconds, configure: WebViewScope<T>.() -> Unit): T =
    throw UnsupportedOperationException(NO_WEBVIEW)

@Suppress("UNUSED_PARAMETER")
suspend fun getLocalStorage(url: String, key: String): String? =
    throw UnsupportedOperationException(NO_WEBVIEW)

@RequiresOptIn(message = "Avoid depending on native WebView")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class NativeWebViewApi
