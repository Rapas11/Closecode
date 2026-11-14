package com.closecode.ui.project

import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class ProjectConflictException(message: String) : IllegalArgumentException(message)

/**
 * Permanent reservations for project names, package names and folder slugs.
 *
 * The registry deliberately lives outside `filesDir/projects`: deleting a project removes its
 * folder, but the name and package it used must stay reserved so a later project cannot
 * silently inherit its identity. Entries are written atomically and are rebuilt from the
 * project metadata by [ProjectRepository] when the file is missing or corrupt.
 */
class ProjectRegistry(private val file: File) {

    data class Entry(
        val id: String,
        var slug: String,
        var name: String,
        var packageName: String?,
        val names: MutableSet<String>,
        val packages: MutableSet<String>,
        var deleted: Boolean,
        var pending: Boolean,
        var updatedAt: Long,
    )

    fun entries(): List<Entry> = synchronized(lock) {
        load().toList()
    }

    fun entry(id: String): Entry? = synchronized(lock) {
        load().firstOrNull { it.id == id }
    }

    fun findBySlug(slug: String): Entry? = synchronized(lock) {
        load().firstOrNull { it.slug == slug }
    }

    /**
     * Makes sure an existing project is represented. This is the migration path: it never
     * rejects a duplicate that already exists on disk, it only records it so future creates
     * and renames can see every reservation.
     */
    fun ensure(
        id: String,
        slug: String,
        name: String,
        packageName: String?,
        deleted: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ) {
        synchronized(lock) {
            val entries = load()
            val existing = entries.firstOrNull { it.id == id }
            var changed = false
            if (existing == null) {
                entries += newEntry(id, slug, name, packageName, deleted, pending = false, now)
                changed = true
            } else {
                if (existing.slug != slug) {
                    existing.slug = slug
                    changed = true
                }
                if (name.isNotBlank()) {
                    val key = ProjectIdentity.nameKey(name)
                    if (existing.name != name || key !in existing.names) {
                        existing.name = name
                        existing.names += key
                        changed = true
                    }
                }
                packageName?.takeIf { it.isNotBlank() }?.let {
                    val key = ProjectIdentity.packageKey(it)
                    if (existing.packageName != it || key !in existing.packages) {
                        existing.packageName = it
                        existing.packages += key
                        changed = true
                    }
                }
                if (existing.deleted != deleted) {
                    existing.deleted = deleted
                    changed = true
                }
                if (existing.pending) {
                    existing.pending = false
                    changed = true
                }
                if (changed) existing.updatedAt = now
            }
            if (changed) save(entries)
        }
    }

    /**
     * Claims a new project's identity before its folder is written. A failure while scaffolding
     * calls [cancel], so a half-created project leaves no reservation behind.
     */
    fun begin(
        id: String,
        slug: String,
        name: String,
        packageName: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        synchronized(lock) {
            val entries = load()
            val existing = entries.firstOrNull { it.id == id }
            if (existing != null && !existing.pending) {
                throw ProjectConflictException("That project already exists.")
            }
            requireNameAvailable(entries, name, id)
            requireSlugAvailable(entries, slug, id)
            packageName?.takeIf { it.isNotBlank() }?.let { requirePackageAvailable(entries, it, id) }
            entries.removeAll { it.id == id }
            entries += newEntry(id, slug, name, packageName, deleted = false, pending = true, now)
            save(entries)
        }
    }

    fun commit(id: String) {
        synchronized(lock) {
            val entries = load()
            val entry = entries.firstOrNull { it.id == id } ?: return
            if (!entry.pending) return
            entry.pending = false
            entry.updatedAt = System.currentTimeMillis()
            save(entries)
        }
    }

    fun cancel(id: String) {
        synchronized(lock) {
            val entries = load()
            if (entries.removeAll { it.id == id && it.pending }) save(entries)
        }
    }

    fun discardPendingBefore(cutoff: Long) {
        synchronized(lock) {
            val entries = load()
            if (entries.removeAll { it.pending && it.updatedAt < cutoff }) save(entries)
        }
    }

    fun rename(id: String, newName: String) {
        synchronized(lock) {
            val entries = load()
            val entry = entries.firstOrNull { it.id == id }
                ?: throw ProjectConflictException("That project no longer exists.")
            requireNameAvailable(entries, newName, id)
            entry.name = newName.trim()
            entry.names += ProjectIdentity.nameKey(newName)
            entry.updatedAt = System.currentTimeMillis()
            save(entries)
        }
    }

    fun setPackage(id: String, packageName: String) {
        synchronized(lock) {
            val entries = load()
            val entry = entries.firstOrNull { it.id == id }
                ?: throw ProjectConflictException("That project no longer exists.")
            requirePackageAvailable(entries, packageName, id)
            entry.packageName = packageName.trim()
            entry.packages += ProjectIdentity.packageKey(packageName)
            entry.updatedAt = System.currentTimeMillis()
            save(entries)
        }
    }

    /**
     * Marks a project as deleted.
     *
     * Its **name and folder stay reserved**, so a project with the same name is never
     * silently put in the old folder. Its **package name is released**: a package belongs to
     * a project that exists, and a name that is used nowhere must be accepted.
     */
    fun markDeleted(id: String) {
        synchronized(lock) {
            val entries = load()
            val entry = entries.firstOrNull { it.id == id } ?: return
            if (entry.deleted) return
            entry.deleted = true
            // The package is released with the project. The record must not keep the old
            // package name either, or the migration in load() would hand it straight back.
            entry.packages.clear()
            entry.packageName = null
            entry.updatedAt = System.currentTimeMillis()
            save(entries)
        }
    }

    fun nameTaken(name: String, exceptId: String? = null): Boolean = synchronized(lock) {
        val key = ProjectIdentity.nameKey(name)
        load().any { it.id != exceptId && key in it.names }
    }

    /**
     * The whole package name is compared — never a segment, a fragment or a substring.
     *
     * `com` matches every package that starts with `com`, so a comparison on anything
     * shorter than the whole name refuses every name on the phone. The comparison is
     * case-insensitive, because Android package names are.
     */
    fun packageTaken(packageName: String, exceptId: String? = null): Boolean =
        packageHolder(packageName, exceptId) != null

    /**
     * The project that holds this package name, so the user can be told which one.
     *
     * A deleted project keeps its identity reserved — that is the registry's rule, and the
     * screen and the creation path both follow it, so the two can never disagree about
     * whether a name is free.
     */
    fun packageHolder(packageName: String, exceptId: String? = null): String? = synchronized(lock) {
        val key = ProjectIdentity.packageKey(packageName)
        if (key.isBlank()) return null
        load().firstOrNull { it.id != exceptId && key in it.packages }
            ?.name?.takeIf { it.isNotBlank() }
    }

    /** Every package name in use, and the project that holds it. */
    fun packagesInUse(exceptId: String? = null): Map<String, String> = synchronized(lock) {
        val out = linkedMapOf<String, String>()
        load().forEach { entry ->
            if (entry.id == exceptId) return@forEach
            entry.packages.forEach { key ->
                if (key.isNotBlank() && key !in out) out[key] = entry.name
            }
        }
        out
    }

    fun slugTaken(slug: String, exceptId: String? = null): Boolean = synchronized(lock) {
        load().any { it.id != exceptId && it.slug == slug }
    }

    fun reservedPackages(exceptId: String? = null): Set<String> = synchronized(lock) {
        load()
            .filter { it.id != exceptId }
            .flatMap { it.packages }
            .toSet()
    }

    private fun requireNameAvailable(entries: List<Entry>, name: String, exceptId: String?) {
        val key = ProjectIdentity.nameKey(name)
        if (key.isBlank()) throw ProjectConflictException("Enter a project name.")
        if (entries.any { it.id != exceptId && key in it.names }) {
            throw ProjectConflictException("That project name is already taken.")
        }
    }

    private fun requirePackageAvailable(
        entries: List<Entry>,
        packageName: String,
        exceptId: String?,
    ) {
        val key = ProjectIdentity.packageKey(packageName)
        val holder = entries.firstOrNull { it.id != exceptId && key in it.packages }
        if (holder != null) {
            throw ProjectConflictException(
                "That package name is already used by \"${holder.name}\". Pick a unique one.",
            )
        }
    }

    private fun requireSlugAvailable(entries: List<Entry>, slug: String, exceptId: String?) {
        if (entries.any { it.id != exceptId && it.slug == slug }) {
            throw ProjectConflictException("A project already uses that folder.")
        }
    }

    private fun newEntry(
        id: String,
        slug: String,
        name: String,
        packageName: String?,
        deleted: Boolean,
        pending: Boolean,
        now: Long,
    ): Entry = Entry(
        id = id,
        slug = slug,
        name = name.trim(),
        packageName = packageName?.takeIf { it.isNotBlank() }?.trim(),
        names = linkedSetOf(ProjectIdentity.nameKey(name)),
        packages = linkedSetOfNotNull(packageName?.takeIf { it.isNotBlank() }
            ?.let { ProjectIdentity.packageKey(it) }),
        deleted = deleted,
        pending = pending,
        updatedAt = now,
    )

    private fun load(): MutableList<Entry> {
        if (!file.isFile) return mutableListOf()
        return runCatching {
            val json = JSONObject(file.readText())
            val array = json.optJSONArray(KEY_PROJECTS) ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString(KEY_ID).takeIf { it.isNotBlank() } ?: continue
                    val slug = item.optString(KEY_SLUG).takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString(KEY_NAME).takeIf { it.isNotBlank() } ?: slug
                    val packageName = item.optString(KEY_PACKAGE)
                        .takeIf { it.isNotBlank() }
                    val names = item.optJSONArray(KEY_NAMES)
                        .toStringSet()
                        .ifEmpty { setOf(ProjectIdentity.nameKey(name)) }
                        .toMutableSet()
                    val packages = item.optJSONArray(KEY_PACKAGES)
                        .toStringSet()
                        .ifEmpty {
                            packageName?.let { setOf(ProjectIdentity.packageKey(it)) }.orEmpty()
                        }
                        .toMutableSet()
                    add(
                        Entry(
                            id = id,
                            slug = slug,
                            name = name,
                            packageName = packageName,
                            names = names,
                            packages = packages,
                            deleted = item.optBoolean(KEY_DELETED, false),
                            pending = item.optBoolean(KEY_PENDING, false),
                            updatedAt = item.optLong(KEY_UPDATED, 0L),
                        ),
                    )
                }
            }.toMutableList()
        }.getOrElse {
            quarantine()
            mutableListOf()
        }
    }

    private fun save(entries: List<Entry>) {
        val projects = JSONArray()
        entries.forEach { entry ->
            projects.put(
                JSONObject().apply {
                    put(KEY_ID, entry.id)
                    put(KEY_SLUG, entry.slug)
                    put(KEY_NAME, entry.name)
                    entry.packageName?.let { put(KEY_PACKAGE, it) }
                    put(KEY_NAMES, JSONArray(entry.names.toList()))
                    put(KEY_PACKAGES, JSONArray(entry.packages.toList()))
                    put(KEY_DELETED, entry.deleted)
                    put(KEY_PENDING, entry.pending)
                    put(KEY_UPDATED, entry.updatedAt)
                },
            )
        }
        val json = JSONObject().apply {
            put(KEY_VERSION, VERSION)
            put(KEY_PROJECTS, projects)
        }
        try {
            ProjectSafety.writeAtomic(file, json.toString())
        } catch (error: IOException) {
            throw IOException("Could not save project reservations: ${error.message}", error)
        }
    }

    private fun quarantine() {
        runCatching {
            val backup = File(file.parentFile, "${file.name}.corrupt")
            if (backup.exists()) backup.delete()
            file.copyTo(backup, overwrite = true)
            file.delete()
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun linkedSetOfNotNull(value: String?): MutableSet<String> =
        if (value == null) linkedSetOf() else linkedSetOf(value)

    private companion object {
        val lock = Any()
        const val VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_PROJECTS = "projects"
        const val KEY_ID = "id"
        const val KEY_SLUG = "slug"
        const val KEY_NAME = "name"
        const val KEY_PACKAGE = "packageName"
        const val KEY_NAMES = "names"
        const val KEY_PACKAGES = "packages"
        const val KEY_DELETED = "deleted"
        const val KEY_PENDING = "pending"
        const val KEY_UPDATED = "updatedAt"
    }
}
