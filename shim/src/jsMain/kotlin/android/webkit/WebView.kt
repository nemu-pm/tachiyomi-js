package android.webkit

import java.io.InputStream

// Placeholders for the Android WebView types that keiyoushi.utils.WebView's signatures mention.
// Nothing here renders; the sandbox has no WebView, so these only let sources compile.

open class WebResourceRequest(
    val url: String,
    val method: String = "GET",
    val requestHeaders: Map<String, String> = emptyMap(),
    val isForMainFrame: Boolean = true,
) {
    fun getUrl(): String = url
    fun getMethod(): String = method
    fun getRequestHeaders(): Map<String, String> = requestHeaders
    fun hasGesture(): Boolean = false
    fun isRedirect(): Boolean = false
}

open class WebResourceError(val errorCode: Int = -1, val description: CharSequence = "") {
    fun getErrorCode(): Int = errorCode
    fun getDescription(): CharSequence = description
}

open class WebResourceResponse(
    var mimeType: String?,
    var encoding: String?,
    var data: InputStream?,
) {
    var statusCode: Int = 200
    var reasonPhrase: String = "OK"
    var responseHeaders: Map<String, String>? = null

    constructor(
        mimeType: String?,
        encoding: String?,
        statusCode: Int,
        reasonPhrase: String,
        responseHeaders: Map<String, String>?,
        data: InputStream?,
    ) : this(mimeType, encoding, data) {
        this.statusCode = statusCode
        this.reasonPhrase = reasonPhrase
        this.responseHeaders = responseHeaders
    }

    fun setStatusCodeAndReasonPhrase(statusCode: Int, reasonPhrase: String) {
        this.statusCode = statusCode
        this.reasonPhrase = reasonPhrase
    }
}

open class WebView {
    val settings: WebSettings = WebSettings()

    fun loadUrl(url: String): Nothing = throw UnsupportedOperationException(NO_WEBVIEW)
    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>): Nothing = throw UnsupportedOperationException(NO_WEBVIEW)
    fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?): Nothing = throw UnsupportedOperationException(NO_WEBVIEW)
    fun stopLoading() {}
    fun destroy() {}

    companion object {
        private const val NO_WEBVIEW = "WebView is not available in the tachiyomi-js sandbox"
    }
}

class WebSettings {
    var javaScriptEnabled: Boolean = false
    var domStorageEnabled: Boolean = false
    var userAgentString: String? = null
    var blockNetworkImage: Boolean = false
    var useWideViewPort: Boolean = false
    var loadWithOverviewMode: Boolean = false
}
