package android.os

/** The runtime is single-threaded: there is one looper and posted work runs inline. */
class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper = main
    }
}

class Handler(val looper: Looper = Looper.getMainLooper()) {
    fun post(r: () -> Unit): Boolean {
        r()
        return true
    }

    fun postDelayed(r: () -> Unit, delayMillis: Long): Boolean {
        r()
        return true
    }

    fun removeCallbacks(r: () -> Unit) {}
    fun removeCallbacksAndMessages(token: Any?) {}
}
