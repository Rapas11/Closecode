package org.jetbrains.kotlin.com.intellij.util.containers

import java.util.concurrent.ConcurrentHashMap

/**
 * See [ConcurrentLongObjectMap]: a safe replacement for the Kotlin compiler's map, which
 * cannot initialize on Android because its Unsafe reflection is blocked. Public so the
 * compiler's `ContainerUtil` (a different runtime package) can construct it.
 */
class ConcurrentLongObjectHashMap<V> : ConcurrentLongObjectMap<V> {
    private val map = ConcurrentHashMap<Long, V>()

    override fun put(key: Long, value: V): V? = map.put(key, value)

    override fun get(key: Long): V? = map[key]

    override fun remove(key: Long): V? = map.remove(key)

    override fun putIfAbsent(key: Long, value: V): V? = map.putIfAbsent(key, value)

    override fun entries(): Iterable<ConcurrentLongObjectMap.LongEntry<V>> {
        val snapshot = ArrayList<ConcurrentLongObjectMap.LongEntry<V>>(map.size)
        for ((key, value) in map) {
            snapshot.add(Entry(key, value))
        }
        return snapshot
    }

    private class Entry<V>(private val key: Long, private val value: V) :
        ConcurrentLongObjectMap.LongEntry<V> {
        override fun getKey(): Long = key

        override fun getValue(): V = value
    }
}
