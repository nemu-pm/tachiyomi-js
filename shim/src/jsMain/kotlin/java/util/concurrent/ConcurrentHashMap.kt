package java.util.concurrent

// JS is single-threaded; a plain HashMap has the same observable behaviour.
typealias ConcurrentHashMap<K, V> = HashMap<K, V>
