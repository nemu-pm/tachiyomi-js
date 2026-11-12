package java.io

/** Extends [AutoCloseable] so the stdlib's `use` applies without an import, as on the JVM. */
interface Closeable : AutoCloseable {
    override fun close()
}
