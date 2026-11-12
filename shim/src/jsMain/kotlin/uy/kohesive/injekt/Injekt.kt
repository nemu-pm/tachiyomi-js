package uy.kohesive.injekt

import android.app.Application
import android.content.SharedPreferences
import android.content.BridgedSharedPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.reflect.KClass

/**
 * Injekt shim: a fixed registry of the singletons extensions pull in (`Injekt.get()`),
 * matching what the Mihon app registers for its extension process.
 */
object Injekt {
    private val registry = HashMap<KClass<*>, Any>()

    init {
        registry[Application::class] = ApplicationStub()
        registry[NetworkHelper::class] = NetworkHelper()
        registry[Json::class] = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
        registry[ProtoBuf::class] = ProtoBuf {}
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T =
        registry[type] as? T ?: throw IllegalStateException("No instance registered for ${type.simpleName}")

    fun <T : Any> getInstance(type: KClass<T>): T = get(type)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(type: KClass<T>): T? = registry[type] as? T

    fun <T : Any> register(type: KClass<T>, instance: T) {
        registry[type] = instance
    }

    fun <T : Any> addSingleton(type: KClass<T>, instance: T) = register(type, instance)
}

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.get(T::class) }

/** The app's Application: only `getSharedPreferences` matters to sources. */
class ApplicationStub : Application() {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        BridgedSharedPreferences.getInstance(name)
}
