package javax.net.ssl

import java.security.SecureRandom
import java.security.cert.X509Certificate

/*
 * javax.net.ssl surface for sources that install a trust-all TLS configuration on their client.
 * The sandbox's HTTP goes through the host, so none of this reaches a socket: the classes exist
 * so the code compiles and the builder calls are no-ops.
 */

interface KeyManager

interface TrustManager

interface X509TrustManager : TrustManager {
    /** `out` so overrides may declare `Array<X509Certificate>` or `Array<X509Certificate?>` as the JVM platform type allows. */
    fun getAcceptedIssuers(): Array<out X509Certificate?>
    fun checkClientTrusted(chain: Array<X509Certificate>, authType: String)
    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String)
}

interface SSLSession

fun interface HostnameVerifier {
    fun verify(hostname: String, session: SSLSession?): Boolean
}

open class SSLSocketFactory

class SSLContext private constructor(val protocol: String) {
    private var initialized = false

    fun getProtocol(): String = protocol

    fun init(keyManagers: Array<out KeyManager>?, trustManagers: Array<out TrustManager>?, random: SecureRandom?) {
        initialized = true
    }

    val socketFactory: SSLSocketFactory
        get() {
            check(initialized) { "SSLContext is not initialized" }
            return SSLSocketFactory()
        }

    fun getSocketFactory(): SSLSocketFactory = socketFactory

    companion object {
        fun getInstance(protocol: String): SSLContext = SSLContext(protocol)
        fun getDefault(): SSLContext = SSLContext("Default").apply { init(null, null, null) }
    }
}
