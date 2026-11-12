package android.annotation

/** Lint markers; nothing reads them, they only need to resolve. */
@Retention(AnnotationRetention.SOURCE)
annotation class SuppressLint(vararg val value: String)

@Retention(AnnotationRetention.SOURCE)
annotation class TargetApi(val value: Int)
