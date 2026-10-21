package org.jetbrains.kotlin.com.intellij.util.containers

/**
 * Binary-compatible stand-in for the interface of the same name inside the Kotlin
 * compiler. The compiler runs from dex inside this app; its copy of
 * `ConcurrentLongObjectHashMap` probes `sun.misc.Unsafe.getAndAddInt` through
 * `MethodHandles`, which Android refuses, and the class initializer aborts the compiler.
 *
 * The app's class loader is the parent of the compiler's DexClassLoader, so these public
 * types win the parent-first lookup and the compiler never loads its Unsafe version.
 * Only the five methods the compiler calls are provided; a ConcurrentHashMap backs them.
 */
interface ConcurrentLongObjectMap<V> {
    fun put(key: Long, value: V): V?
    fun get(key: Long): V?
    fun remove(key: Long): V?
    fun entries(): Iterable<LongEntry<V>>
    fun putIfAbsent(key: Long, value: V): V?

    interface LongEntry<V> {
        fun getKey(): Long
        fun getValue(): V
    }
}
