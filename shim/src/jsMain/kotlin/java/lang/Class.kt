package java.lang

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.reflect.KClass

/**
 * java.lang.Class with identity: two `Foo::class.java` values are equal, so OkHttp request tags
 * keyed by class (`Request.tag(Foo::class.java)`) resolve.
 */
class Class<T> internal constructor(val kClass: KClass<*>?, val name: String) {
    val simpleName: String get() = kClass?.simpleName ?: name.substringAfterLast('.')
    val canonicalName: String get() = name
    val classLoader: ClassLoader get() = ClassLoader.system

    fun getName(): String = name
    fun getSimpleName(): String = simpleName
    fun getCanonicalName(): String = canonicalName
    fun getClassLoader(): ClassLoader = classLoader

    fun isInstance(obj: Any?): Boolean = obj != null && kClass?.isInstance(obj) == true

    @Suppress("UNCHECKED_CAST")
    fun cast(obj: Any?): T = obj as T

    override fun equals(other: Any?): Boolean = other is Class<*> && other.kClass == kClass && other.name == name
    override fun hashCode(): Int = (kClass?.hashCode() ?: 0) * 31 + name.hashCode()
    override fun toString(): String = "class $name"

    companion object {
        private val cache = HashMap<KClass<*>, Class<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> of(kClass: KClass<T>): Class<T> =
            cache.getOrPut(kClass) { Class<T>(kClass, kClass.simpleName ?: "Unknown") } as Class<T>

        fun forName(name: String): Class<*> = throw ClassNotFoundException(name)
    }
}

class ClassNotFoundException(message: String? = null) : Exception(message)

/** Resources come from [tachiyomi.shim.ResourceRegistry], filled by generated code or the host. */
class ClassLoader {
    fun getResourceAsStream(name: String): InputStream? =
        tachiyomi.shim.ResourceRegistry.get(name.removePrefix("/"))?.let { ByteArrayInputStream(it) }

    fun getResource(name: String): java.net.URL? =
        if (tachiyomi.shim.ResourceRegistry.get(name.removePrefix("/")) != null) java.net.URL("resource:/$name") else null

    companion object {
        val system = ClassLoader()
        fun getSystemClassLoader(): ClassLoader = system
    }
}
