package okhttp3.internal

import java.io.Closeable
import okhttp3.Response

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Throwable) {
    }
}

fun Response.closeQuietly() {
    try {
        close()
    } catch (e: Throwable) {
    }
}
