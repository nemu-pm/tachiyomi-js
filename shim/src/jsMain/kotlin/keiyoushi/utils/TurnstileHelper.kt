package keiyoushi.utils

// Stub for keiyoushi core utils/TurnstileHelper.kt: solving a Turnstile challenge needs a
// WebView, which the sandbox has none of. Same signature so sources compile.

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Suppress("UNUSED_PARAMETER")
suspend fun getTurnstileToken(
    url: String,
    siteKey: String,
    userAgent: String? = null,
    action: String? = null,
    cData: String? = null,
    timeout: Duration = 2.minutes,
): String = throw UnsupportedOperationException("Turnstile challenges need a WebView, which the tachiyomi-js sandbox does not have")
