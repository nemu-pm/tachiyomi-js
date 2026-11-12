package keiyoushi.lib.cronet

import okhttp3.OkHttpClient
import okhttp3.Response

/*
 * lib:cronet routes OkHttp calls through the device's Cronet via reflection on OkHttp internals.
 * Upstream itself stays on OkHttp off Android, so the sandbox keeps the client unchanged.
 */

fun OkHttpClient.Builder.cronet(): OkHttpClient.Builder = this

/** Whether the call went through Cronet rather than staying on OkHttp */
val Response.isCronet: Boolean get() = false
