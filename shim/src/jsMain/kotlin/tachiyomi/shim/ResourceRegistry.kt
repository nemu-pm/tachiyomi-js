package tachiyomi.shim

/**
 * Classpath resources for the sandbox. Generated code registers the extension's bundled files
 * (i18n `.properties`) at load; the host may also expose a `__tachiyomi_resources` object.
 */
object ResourceRegistry {
    private val resources = HashMap<String, ByteArray>()

    fun register(path: String, content: String) {
        resources[normalize(path)] = content.encodeToByteArray()
    }

    fun registerBytes(path: String, content: ByteArray) {
        resources[normalize(path)] = content
    }

    fun get(path: String): ByteArray? = resources[normalize(path)] ?: hostResource(normalize(path))

    fun has(path: String): Boolean = get(path) != null

    fun clear() = resources.clear()

    private fun normalize(path: String): String = path.removePrefix("/")

    private fun hostResource(path: String): ByteArray? {
        val value = js("(typeof __tachiyomi_resources === 'object' && __tachiyomi_resources !== null) ? __tachiyomi_resources[path] : undefined")
        return (value as? String)?.encodeToByteArray()
    }
}
