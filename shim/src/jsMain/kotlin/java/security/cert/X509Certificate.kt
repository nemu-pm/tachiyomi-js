package java.security.cert

/** Only named in trust-manager signatures; the sandbox never sees a certificate. */
open class Certificate(val type: String)

open class X509Certificate : Certificate("X.509")

class CertificateException(message: String? = null, cause: Throwable? = null) : java.security.GeneralSecurityException(message, cause)
