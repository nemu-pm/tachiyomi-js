package androidx.preference

import android.content.Context
import android.widget.EditText
import kotlinx.serialization.json.*

/**
 * Preference shims that capture schema for host UI rendering.
 * When setupPreferenceScreen is called, preferences are collected
 * and the schema can be exported via PreferenceRegistry.
 */

/**
 * Global registry for preference schemas.
 * Host can call getSchema() after extension setup to get the schema JSON.
 */
object PreferenceRegistry {
    private val schemas = mutableMapOf<String, MutableList<JsonObject>>()
    private var currentScreen: String? = null

    fun beginScreen(name: String) {
        currentScreen = name
        schemas.getOrPut(name) { mutableListOf() }
    }

    fun addPreference(def: JsonObject) {
        val screen = currentScreen ?: "default"
        schemas.getOrPut(screen) { mutableListOf() }.add(def)
    }

    fun getSchemaJson(screen: String = "default"): String {
        val list = schemas[screen] ?: emptyList()
        return Json.encodeToString(JsonArray.serializer(), JsonArray(list))
    }

    fun getAllSchemasJson(): String {
        val all = schemas.values.flatten()
        return Json.encodeToString(JsonArray.serializer(), JsonArray(all))
    }

    fun clear() {
        schemas.clear()
        currentScreen = null
    }
}

open class Preference(val context: Context) {
    var key: String = ""
    var title: CharSequence? = null
    var summary: CharSequence? = null
    var isEnabled: Boolean = true
    var isVisible: Boolean = true
    var isPersistent: Boolean = true
    var isSelectable: Boolean = true
    var isSingleLineTitle: Boolean = false
    var isIconSpaceReserved: Boolean = false
    var order: Int = DEFAULT_ORDER
    var dependency: String? = null
    var dialogTitle: CharSequence? = null
    var dialogMessage: CharSequence? = null
    var summaryProvider: SummaryProvider<*>? = null

    // Java-style setters the JVM derives from the properties above; Kotlin/JS does not.
    fun setEnabled(enabled: Boolean) { isEnabled = enabled }
    fun setVisible(visible: Boolean) { isVisible = visible }
    fun setPersistent(persistent: Boolean) { isPersistent = persistent }
    fun setSelectable(selectable: Boolean) { isSelectable = selectable }

    /** The default value given to [setDefaultValue], exported to the schema as-is. */
    protected var defaultValue: Any? = null
        private set

    var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    var onPreferenceClickListener: OnPreferenceClickListener? = null

    open fun setDefaultValue(value: Any?) {
        defaultValue = value
    }

    open fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {
        onPreferenceChangeListener = listener
    }

    open fun setOnPreferenceChangeListener(block: (Preference, Any?) -> Boolean) {
        onPreferenceChangeListener = object : OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference, newValue: Any?) = block(preference, newValue)
        }
    }

    open fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {
        onPreferenceClickListener = listener
    }

    open fun setOnPreferenceClickListener(block: (Preference) -> Boolean) {
        onPreferenceClickListener = object : OnPreferenceClickListener {
            override fun onPreferenceClick(preference: Preference) = block(preference)
        }
    }

    /** Runs the change listener the way the host does before a value is persisted; true means accept. */
    fun callChangeListener(newValue: Any?): Boolean = onPreferenceChangeListener?.onPreferenceChange(this, newValue) ?: true

    fun setTitle(title: CharSequence?) {
        this.title = title
    }

    fun setSummary(summary: CharSequence?) {
        this.summary = summary
    }

    fun setKey(key: String) {
        this.key = key
    }

    fun getKey(): String = key

    /** Schema fields every preference kind shares. */
    protected fun JsonObjectBuilder.putCommon(type: String) {
        put("type", type)
        put("key", key)
        put("title", title?.toString() ?: key)
        summary?.let { put("summary", it.toString()) }
        if (!isEnabled) put("enabled", false)
        if (!isVisible) put("visible", false)
        dependency?.let { put("dependency", it) }
    }

    open fun registerSchema() {
        // Base class doesn't register - subclasses override
    }

    interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }

    fun interface SummaryProvider<T : Preference> {
        fun provideSummary(preference: T): CharSequence?
    }

    companion object {
        const val DEFAULT_ORDER = Int.MAX_VALUE
    }
}

/** A preference that holds others: the screen and categories. */
open class PreferenceGroup(context: Context) : Preference(context) {
    private val preferences = mutableListOf<Preference>()

    val preferenceCount: Int get() = preferences.size

    open fun addPreference(preference: Preference): Boolean {
        preferences.add(preference)
        preference.registerSchema()
        return true
    }

    fun getPreference(index: Int): Preference = preferences[index]

    fun removePreference(preference: Preference): Boolean = preferences.remove(preference)

    fun removeAll() = preferences.clear()

    @Suppress("UNCHECKED_CAST")
    fun <T : Preference> findPreference(key: CharSequence): T? {
        for (preference in preferences) {
            if (preference.key == key.toString()) return preference as T
            if (preference is PreferenceGroup) preference.findPreference<T>(key)?.let { return it }
        }
        return null
    }
}

open class PreferenceScreen(context: Context) : PreferenceGroup(context) {
    init {
        PreferenceRegistry.beginScreen("default")
    }
}

// Category preference for grouping
open class PreferenceCategory(context: Context) : PreferenceGroup(context) {
    // Categories are a visual separator; their children register themselves
    override fun registerSchema() {}
}

open class ListPreference(context: Context) : Preference(context) {
    var entries: Array<out CharSequence> = emptyArray()
    var entryValues: Array<out CharSequence> = emptyArray()
    var value: String? = null

    /** The entry shown for the current value, as the Android getter. */
    val entry: CharSequence? get() = value?.let { v -> findIndexOfValue(v).takeIf { it >= 0 }?.let { entries.getOrNull(it) } }

    override fun setDefaultValue(value: Any?) {
        super.setDefaultValue(value)
        this.value = value?.toString()
    }

    fun setEntries(entries: Array<out CharSequence>) {
        this.entries = entries
    }

    fun setEntryValues(entryValues: Array<out CharSequence>) {
        this.entryValues = entryValues
    }

    fun setValueIndex(index: Int) {
        value = entryValues.getOrNull(index)?.toString()
    }

    fun findIndexOfValue(value: String?): Int {
        return entryValues.indexOfFirst { it.toString() == value }
    }

    override fun registerSchema() {
        if (key.isNotEmpty()) {
            PreferenceRegistry.addPreference(buildJsonObject {
                putCommon("select")
                put("values", JsonArray(entryValues.map { JsonPrimitive(it.toString()) }))
                put("titles", JsonArray(entries.map { JsonPrimitive(it.toString()) }))
                defaultValue?.let { put("default", it.toString()) }
            })
        }
    }
}

open class MultiSelectListPreference(context: Context) : Preference(context) {
    var entries: Array<out CharSequence> = emptyArray()
    var entryValues: Array<out CharSequence> = emptyArray()
    var values: Set<String> = emptySet()

    override fun setDefaultValue(value: Any?) {
        super.setDefaultValue(value)
        @Suppress("UNCHECKED_CAST")
        (value as? Set<String>)?.let { values = it }
    }

    fun setEntries(entries: Array<out CharSequence>) {
        this.entries = entries
    }

    fun setEntryValues(entryValues: Array<out CharSequence>) {
        this.entryValues = entryValues
    }

    fun findIndexOfValue(value: String?): Int = entryValues.indexOfFirst { it.toString() == value }

    override fun registerSchema() {
        if (key.isNotEmpty()) {
            PreferenceRegistry.addPreference(buildJsonObject {
                putCommon("multi-select")
                put("values", JsonArray(entryValues.map { JsonPrimitive(it.toString()) }))
                put("titles", JsonArray(entries.map { JsonPrimitive(it.toString()) }))
                @Suppress("UNCHECKED_CAST")
                (defaultValue as? Set<String>)?.let { put("default", JsonArray(it.map { v -> JsonPrimitive(v) })) }
            })
        }
    }
}

open class EditTextPreference(context: Context) : Preference(context) {
    var text: String? = null

    override fun setDefaultValue(value: Any?) {
        super.setDefaultValue(value)
        this.text = value?.toString()
    }

    fun setOnBindEditTextListener(listener: OnBindEditTextListener?) {
        // No-op - there is no EditText to bind in the sandbox
    }

    fun setOnBindEditTextListener(listener: (EditText) -> Unit) {
        // No-op - there is no EditText to bind in the sandbox
    }

    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: EditText)
    }

    override fun registerSchema() {
        if (key.isNotEmpty()) {
            PreferenceRegistry.addPreference(buildJsonObject {
                putCommon("text")
                defaultValue?.let { put("default", it.toString()) }
            })
        }
    }
}

/** Two-state preference base: switches and check boxes. */
open class TwoStatePreference(context: Context) : Preference(context) {
    var isChecked: Boolean = false
    var summaryOn: CharSequence? = null
    var summaryOff: CharSequence? = null

    override fun setDefaultValue(value: Any?) {
        super.setDefaultValue(value)
        this.isChecked = value as? Boolean ?: false
    }

    override fun registerSchema() {
        if (key.isNotEmpty()) {
            PreferenceRegistry.addPreference(buildJsonObject {
                putCommon("switch")
                put("default", defaultValue as? Boolean ?: false)
            })
        }
    }
}

open class SwitchPreferenceCompat(context: Context) : TwoStatePreference(context)

open class SwitchPreference(context: Context) : TwoStatePreference(context)

open class CheckBoxPreference(context: Context) : TwoStatePreference(context)

open class SeekBarPreference(context: Context) : Preference(context) {
    var value: Int = 0
    var min: Int = 0
    var max: Int = 100
    var seekBarIncrement: Int = 0
    var showSeekBarValue: Boolean = false

    override fun setDefaultValue(value: Any?) {
        super.setDefaultValue(value)
        (value as? Int)?.let { this.value = it }
    }

    override fun registerSchema() {
        if (key.isNotEmpty()) {
            PreferenceRegistry.addPreference(buildJsonObject {
                putCommon("number")
                put("min", min)
                put("max", max)
                (defaultValue as? Int)?.let { put("default", it) }
            })
        }
    }
}
