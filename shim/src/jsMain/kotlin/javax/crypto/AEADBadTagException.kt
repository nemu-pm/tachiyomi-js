package javax.crypto

open class BadPaddingException(message: String? = null) : Exception(message)

class AEADBadTagException(message: String? = null) : BadPaddingException(message)

class IllegalBlockSizeException(message: String? = null) : Exception(message)
