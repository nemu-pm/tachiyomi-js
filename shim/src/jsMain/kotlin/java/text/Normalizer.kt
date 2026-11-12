package java.text

/** java.text.Normalizer over the host's String.prototype.normalize. */
object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    fun normalize(src: CharSequence, form: Form): String =
        src.toString().asDynamic().normalize(form.name).unsafeCast<String>()

    fun isNormalized(src: CharSequence, form: Form): Boolean = normalize(src, form) == src.toString()
}
