package javax.crypto.spec

import java.security.spec.AlgorithmParameterSpec

class GCMParameterSpec(private val tLen: Int, private val src: ByteArray) : AlgorithmParameterSpec {
    val iv: ByteArray get() = src.copyOf()
    fun getTLen(): Int = tLen
    fun getIV(): ByteArray = iv
}
