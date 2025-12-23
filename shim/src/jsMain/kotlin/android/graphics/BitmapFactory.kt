package android.graphics

import java.io.InputStream

/**
 * Android BitmapFactory shim for Kotlin/JS.
 * Decodes images using browser's OffscreenCanvas via ImageBridge.
 */
object BitmapFactory {
    /**
     * Decode a bitmap from an InputStream.
     */
    fun decodeStream(stream: InputStream): Bitmap {
        val bytes = stream.readAllBytes()
        return decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Decode a bitmap from a byte array.
     * Uses browser's OffscreenCanvas + createImageBitmap for reliable decoding.
     */
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap {
        require(length >= 2) { "Image data too small" }
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        
        // Use JS bridge for decoding (OffscreenCanvas)
        val decoded = ImageBridge.decodeImage(bytes)
            ?: throw IllegalArgumentException("Failed to decode image - unsupported format or corrupted data")
        
        return Bitmap.createFromPixels(decoded.width, decoded.height, decoded.pixels)
    }

    /**
     * Decode with options (simplified - ignores most options).
     */
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        if (opts?.inJustDecodeBounds == true) {
            // Just get dimensions - decode and discard
            val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
            val decoded = ImageBridge.decodeImage(bytes)
            if (decoded != null) {
                opts.outWidth = decoded.width
                opts.outHeight = decoded.height
            }
            return null
        }
        return decodeByteArray(data, offset, length)
    }

    fun decodeStream(stream: InputStream, rect: Rect?, opts: Options?): Bitmap {
        return decodeStream(stream)
    }

    /**
     * Options for decoding.
     */
    class Options {
        var inJustDecodeBounds: Boolean = false
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        var outWidth: Int = 0
        var outHeight: Int = 0
        var outMimeType: String? = null
        var inMutable: Boolean = false
    }
}

/**
 * Read all bytes from InputStream.
 */
private fun InputStream.readAllBytes(): ByteArray {
    val buffer = mutableListOf<Byte>()
    val temp = ByteArray(8192)
    var n: Int
    while (read(temp).also { n = it } > 0) {
        buffer.addAll(temp.slice(0 until n))
    }
    return buffer.toByteArray()
}
