package java.net

/** No DNS in the sandbox; the host resolves names. Kept so `Dns` implementations compile. */
class InetAddress private constructor(val hostName: String, val hostAddress: String) {
    fun getHostName(): String = hostName
    fun getHostAddress(): String = hostAddress
    override fun toString(): String = "$hostName/$hostAddress"

    companion object {
        fun getByName(host: String): InetAddress = InetAddress(host, host)
        fun getAllByName(host: String): Array<InetAddress> = arrayOf(getByName(host))
    }
}

class UnknownHostException(message: String? = null) : java.io.IOException(message)
