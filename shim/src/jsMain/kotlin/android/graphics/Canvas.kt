package android.graphics

/**
 * Android Canvas shim for Kotlin/JS.
 * Draws onto a Bitmap using raw pixel operations.
 */
class Canvas(private val bitmap: Bitmap) {
    // Current transform, Android's row-major 3x3 without perspective: canvas x = a*x + c*y + tx, y = b*x + d*y + ty.
    private var a = 1f
    private var b = 0f
    private var c = 0f
    private var d = 1f
    private var tx = 0f
    private var ty = 0f
    private val saveStack = mutableListOf<FloatArray>()

    private val isIntegerTranslate: Boolean
        get() = a == 1f && b == 0f && c == 0f && d == 1f && tx == kotlin.math.round(tx) && ty == kotlin.math.round(ty)

    /** Pre-concatenates N, so N applies to points first (Android's translate/scale/rotate semantics). */
    private fun preConcat(na: Float, nb: Float, nc: Float, nd: Float, ntx: Float, nty: Float) {
        val a2 = a * na + c * nb
        val b2 = b * na + d * nb
        val c2 = a * nc + c * nd
        val d2 = b * nc + d * nd
        val tx2 = a * ntx + c * nty + tx
        val ty2 = b * ntx + d * nty + ty
        a = a2; b = b2; c = c2; d = d2; tx = tx2; ty = ty2
    }

    /**
     * Get the backing bitmap.
     */
    fun getBitmap(): Bitmap = bitmap

    /**
     * Get canvas width.
     */
    val width: Int get() = bitmap.width

    /**
     * Get canvas height.
     */
    val height: Int get() = bitmap.height

    /**
     * Fill the entire canvas with a color.
     */
    fun drawColor(color: Int) {
        bitmap.fill(color)
    }

    /**
     * Draw a bitmap onto this canvas.
     * The entire source bitmap is drawn at (left, top).
     */
    fun drawBitmap(src: Bitmap, left: Float, top: Float, paint: Paint?) {
        drawBitmap(src, null, RectF(left, top, left + src.width, top + src.height), paint)
    }

    /**
     * Draw a region of the source bitmap to a region on this canvas.
     * This is the primary method used by image descrambling extensions.
     */
    fun drawBitmap(src: Bitmap, srcRect: Rect?, dstRect: Rect, paint: Paint?) {
        if (isIntegerTranslate) {
            val sl = srcRect?.left ?: 0
            val st = srcRect?.top ?: 0
            val sr = srcRect?.right ?: src.width
            val sb = srcRect?.bottom ?: src.height
            val dx = tx.toInt()
            val dy = ty.toInt()
            if (sr - sl == dstRect.width && sb - st == dstRect.height) {
                bitmap.copyRegion(src, sl, st, sr, sb, dstRect.left + dx, dstRect.top + dy, dstRect.right + dx, dstRect.bottom + dy)
                return
            }
        }
        drawBitmap(src, srcRect, RectF(dstRect), paint)
    }

    /**
     * Draws src (or the srcRect part of it) stretched into dstRect under the current transform.
     * Nearest-neighbour: each covered canvas pixel is inverse-mapped to a source pixel.
     */
    fun drawBitmap(src: Bitmap, srcRect: Rect?, dstRect: RectF, paint: Paint?) {
        val sl = srcRect?.left ?: 0
        val st = srcRect?.top ?: 0
        val sw = (srcRect?.right ?: src.width) - sl
        val sh = (srcRect?.bottom ?: src.height) - st
        val dw = dstRect.right - dstRect.left
        val dh = dstRect.bottom - dstRect.top
        if (sw <= 0 || sh <= 0 || dw <= 0f || dh <= 0f) return

        val det = a * d - b * c
        if (det == 0f) return
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det

        // Canvas-space bounding box of the transformed destination rectangle.
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        var i = 0
        for (y in floatArrayOf(dstRect.top, dstRect.bottom)) for (x in floatArrayOf(dstRect.left, dstRect.right)) {
            xs[i] = a * x + c * y + tx
            ys[i] = b * x + d * y + ty
            i++
        }
        val minX = kotlin.math.floor(xs.min()).toInt().coerceAtLeast(0)
        val maxX = kotlin.math.ceil(xs.max()).toInt().coerceAtMost(bitmap.width)
        val minY = kotlin.math.floor(ys.min()).toInt().coerceAtLeast(0)
        val maxY = kotlin.math.ceil(ys.max()).toInt().coerceAtMost(bitmap.height)

        val scaleX = sw / dw
        val scaleY = sh / dh
        for (py in minY until maxY) {
            val cy = py + 0.5f - ty
            for (px in minX until maxX) {
                val cx = px + 0.5f - tx
                val u = ia * cx + ic * cy
                val v = ib * cx + id * cy
                if (u < dstRect.left || u >= dstRect.right || v < dstRect.top || v >= dstRect.bottom) continue
                val sx = sl + ((u - dstRect.left) * scaleX).toInt()
                val sy = st + ((v - dstRect.top) * scaleY).toInt()
                if (sx < 0 || sy < 0 || sx >= src.width || sy >= src.height) continue
                bitmap.setPixel(px, py, src.getPixel(sx, sy))
            }
        }
    }

    /**
     * Draw a bitmap through a matrix, on top of the canvas transform.
     */
    fun drawBitmap(src: Bitmap, matrix: Matrix?, paint: Paint?) {
        save()
        if (matrix != null) concat(matrix)
        drawBitmap(src, null, RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), paint)
        restore()
    }

    /**
     * Draw a rectangle filled with paint color.
     */
    fun drawRect(rect: Rect, paint: Paint) {
        drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
    }

    /**
     * Draw a rectangle filled with paint color.
     */
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val color = paint.color
        // Fills the canvas-space bounding box: exact for axis-aligned transforms.
        val xs = floatArrayOf(a * left + c * top + tx, a * right + c * top + tx, a * left + c * bottom + tx, a * right + c * bottom + tx)
        val ys = floatArrayOf(b * left + d * top + ty, b * right + d * top + ty, b * left + d * bottom + ty, b * right + d * bottom + ty)
        val l = kotlin.math.round(xs.min()).toInt().coerceIn(0, bitmap.width)
        val t = kotlin.math.round(ys.min()).toInt().coerceIn(0, bitmap.height)
        val r = kotlin.math.round(xs.max()).toInt().coerceIn(0, bitmap.width)
        val bt = kotlin.math.round(ys.max()).toInt().coerceIn(0, bitmap.height)

        for (y in t until bt) {
            for (x in l until r) {
                bitmap.setPixel(x, y, color)
            }
        }
    }

    /**
     * Save the current transform; returns the value for restoreToCount.
     */
    fun save(): Int {
        saveStack.add(floatArrayOf(a, b, c, d, tx, ty))
        return saveStack.size
    }

    fun getSaveCount(): Int = saveStack.size + 1

    /**
     * Restore the most recently saved transform.
     */
    fun restore() {
        if (saveStack.isNotEmpty()) {
            val s = saveStack.removeAt(saveStack.lastIndex)
            a = s[0]; b = s[1]; c = s[2]; d = s[3]; tx = s[4]; ty = s[5]
        }
    }

    fun restoreToCount(saveCount: Int) {
        while (saveStack.size >= saveCount && saveStack.isNotEmpty()) restore()
    }

    fun translate(dx: Float, dy: Float) = preConcat(1f, 0f, 0f, 1f, dx, dy)

    fun scale(sx: Float, sy: Float) = preConcat(sx, 0f, 0f, sy, 0f, 0f)

    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        translate(px, py)
        scale(sx, sy)
        translate(-px, -py)
    }

    fun rotate(degrees: Float) {
        val radians = degrees * (kotlin.math.PI / 180.0)
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        preConcat(cos, sin, -sin, cos, 0f, 0f)
    }

    fun rotate(degrees: Float, px: Float, py: Float) {
        translate(px, py)
        rotate(degrees)
        translate(-px, -py)
    }

    fun skew(sx: Float, sy: Float) = preConcat(1f, sy, sx, 1f, 0f, 0f)

    fun concat(matrix: Matrix) {
        val v = FloatArray(9)
        matrix.getValues(v)
        preConcat(v[0], v[3], v[1], v[4], v[2], v[5])
    }

    fun setMatrix(matrix: Matrix?) {
        a = 1f; b = 0f; c = 0f; d = 1f; tx = 0f; ty = 0f
        if (matrix != null) concat(matrix)
    }

    /**
     * Clip to rectangle (simplified - just validates bounds in draw ops).
     */
    fun clipRect(rect: Rect): Boolean {
        // No-op - bounds checking happens in draw operations
        return true
    }

    fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        return true
    }
}

/**
 * Simple Paint class for drawing operations.
 */
open class Paint(flags: Int = 0) {
    var color: Int = Color.BLACK
    var alpha: Int = 255
    var style: Style = Style.FILL
    var strokeWidth: Float = 1f
    var isAntiAlias: Boolean = (flags and ANTI_ALIAS_FLAG) != 0
    var textSize: Float = 12f
    var typeface: Typeface? = null

    enum class Style {
        FILL,
        STROKE,
        FILL_AND_STROKE
    }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }
}

/**
 * Simple Typeface stub.
 */
class Typeface private constructor(val family: String, val style: Int) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        val DEFAULT = Typeface("sans-serif", NORMAL)
        val DEFAULT_BOLD = Typeface("sans-serif", BOLD)
        val SANS_SERIF = Typeface("sans-serif", NORMAL)
        val SERIF = Typeface("serif", NORMAL)
        val MONOSPACE = Typeface("monospace", NORMAL)

        fun create(family: String?, style: Int): Typeface {
            return Typeface(family ?: "sans-serif", style)
        }

        fun create(family: Typeface?, style: Int): Typeface {
            return Typeface(family?.family ?: "sans-serif", style)
        }
    }
}

/**
 * Simple Matrix class for transformations.
 */
class Matrix {
    private var values = floatArrayOf(
        1f, 0f, 0f,  // scale X, skew X, translate X
        0f, 1f, 0f,  // skew Y, scale Y, translate Y
        0f, 0f, 1f   // perspective
    )

    fun reset() {
        values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }

    fun setTranslate(dx: Float, dy: Float) {
        reset()
        values[2] = dx
        values[5] = dy
    }

    fun setScale(sx: Float, sy: Float) {
        reset()
        values[0] = sx
        values[4] = sy
    }

    fun setRotate(degrees: Float) {
        reset()
        val radians = degrees * (kotlin.math.PI / 180.0).toFloat()
        val cos = kotlin.math.cos(radians.toDouble()).toFloat()
        val sin = kotlin.math.sin(radians.toDouble()).toFloat()
        values[0] = cos
        values[1] = -sin
        values[3] = sin
        values[4] = cos
    }

    fun preTranslate(dx: Float, dy: Float) {
        values[2] += dx
        values[5] += dy
    }

    fun postTranslate(dx: Float, dy: Float) {
        values[2] += dx
        values[5] += dy
    }

    fun getTranslateX(): Float = values[2]
    fun getTranslateY(): Float = values[5]

    fun getValues(out: FloatArray) {
        values.copyInto(out, 0, 0, minOf(9, out.size))
    }

    fun setValues(values: FloatArray) {
        values.copyInto(this.values, 0, 0, minOf(9, values.size))
    }
}

