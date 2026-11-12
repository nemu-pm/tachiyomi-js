package java.io

/** No filesystem in the sandbox. Paths are kept so `Cache(File, Long)` and friends compile. */
class File(val path: String) {
    constructor(parent: File?, child: String) : this(if (parent == null) child else "${parent.path}/$child")
    constructor(parent: String, child: String) : this("$parent/$child")

    val name: String get() = path.substringAfterLast('/')
    val absolutePath: String get() = path
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let(::File)
    val isDirectory: Boolean get() = false
    val isFile: Boolean get() = false

    fun getName(): String = name
    fun getPath(): String = path
    fun getAbsolutePath(): String = absolutePath
    fun exists(): Boolean = false
    fun mkdirs(): Boolean = false
    fun mkdir(): Boolean = false
    fun delete(): Boolean = false
    fun length(): Long = 0L
    fun listFiles(): Array<File>? = null
    fun readBytes(): ByteArray = throw FileNotFoundException(path)
    fun readText(): String = throw FileNotFoundException(path)
    fun writeBytes(bytes: ByteArray): Unit = throw IOException("Read-only sandbox: $path")
    fun writeText(text: String): Unit = throw IOException("Read-only sandbox: $path")

    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
}

class FileNotFoundException(message: String? = null) : IOException(message)
