package java.security

open class GeneralSecurityException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)
