package android.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher

/**
 * Stub widget classes - UI widgets are not used in JS.
 */

/**
 * External function provided by host environment for showing toasts.
 * Host must provide: globalThis.tachiyomiShowToast(message, durationMs)
 */
@JsName("tachiyomiShowToast")
private external fun externalShowToast(message: String, durationMs: Int): Unit

/**
 * Toast implementation that delegates to host environment (e.g., sonner in frontend).
 */
class Toast private constructor(context: Context?) {
    private var text: CharSequence = ""
    private var duration: Int = LENGTH_SHORT
    
    fun setText(text: CharSequence) { this.text = text }
    fun setDuration(duration: Int) { this.duration = duration }
    
    fun show() {
        val durationMs = if (duration == LENGTH_LONG) 5000 else 2000
        try {
            externalShowToast(text.toString(), durationMs)
        } catch (e: dynamic) {
            // Host didn't provide toast implementation - silent fallback
            console.log("Toast: $text")
        }
    }
    
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        
        /** `text` is nullable as the Java platform type: sources pass `e.message`. */
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast {
            return Toast(context).apply {
                setText(text ?: "")
                setDuration(duration)
            }
        }
    }
}

open class View {
    var rootView: View = this
    var isEnabled: Boolean = true
    var visibility: Int = 0

    fun <T : View> findViewById(id: Int): T? = null

    fun setOnClickListener(listener: ((View) -> Unit)?) {}

    /** Runs now; there is no UI thread to post to. */
    fun post(action: () -> Unit): Boolean {
        action()
        return true
    }
}

open class EditText : View() {
    var text: Editable = EditableImpl("")
    var error: CharSequence? = null
    var hint: CharSequence? = null
    var inputType: Int = 0

    private val textWatchers = mutableListOf<TextWatcher>()

    fun setText(value: CharSequence?) {
        text = EditableImpl(value?.toString() ?: "")
    }

    fun setSelection(index: Int) {}
    fun setSelection(start: Int, stop: Int) {}
    fun selectAll() {}
    fun setHorizontallyScrolling(scrolling: Boolean) {}
    fun setSingleLine(single: Boolean = true) {}

    fun addTextChangedListener(watcher: TextWatcher) {
        textWatchers.add(watcher)
    }

    fun removeTextChangedListener(watcher: TextWatcher) {
        textWatchers.remove(watcher)
    }
}

open class Button : View()

open class TextView : View() {
    var text: CharSequence = ""
}

private class EditableImpl(private var content: String) : Editable() {
    override val length: Int get() = content.length
    override fun get(index: Int): Char = content[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = content.subSequence(startIndex, endIndex)
    override fun toString(): String = content
}
