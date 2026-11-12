package java.lang

import java.util.Date

object System {
    fun currentTimeMillis(): Long = Date.currentTimeMillis()
    
    fun getProperty(key: String): String? {
        return when (key) {
            "http.agent" -> "Tachiyomi/1.0"
            else -> null
        }
    }
    
    fun getProperty(key: String, def: String): String = getProperty(key) ?: def

    fun nanoTime(): Long = currentTimeMillis() * 1_000_000L

    fun arraycopy(src: ByteArray, srcPos: Int, dest: ByteArray, destPos: Int, length: Int) {
        src.copyInto(dest, destPos, srcPos, srcPos + length)
    }

    fun arraycopy(src: IntArray, srcPos: Int, dest: IntArray, destPos: Int, length: Int) {
        src.copyInto(dest, destPos, srcPos, srcPos + length)
    }

    fun arraycopy(src: Array<Any?>, srcPos: Int, dest: Array<Any?>, destPos: Int, length: Int) {
        src.copyInto(dest, destPos, srcPos, srcPos + length)
    }
}

