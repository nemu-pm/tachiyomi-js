package eu.kanade.tachiyomi.source.model

import android.net.Uri

open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: Uri? = null,
) {
    val number: Int get() = index + 1
}
