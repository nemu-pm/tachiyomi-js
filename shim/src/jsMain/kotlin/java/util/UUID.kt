package java.util

import kotlin.random.Random

/** Version 4 UUIDs from [Random]; [fromString] keeps the text as given, lowercased. */
class UUID private constructor(private val text: String) {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is UUID && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        fun randomUUID(): UUID {
            val b = Random.nextBytes(16)
            b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
            b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            return UUID(
                hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" +
                    hex.substring(16, 20) + "-" + hex.substring(20),
            )
        }

        fun fromString(name: String): UUID = UUID(name.lowercase())
    }
}
