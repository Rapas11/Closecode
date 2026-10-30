package com.closecode.ui.project

import android.content.Context
import com.closecode.ai.OpenCodeSessions
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import java.io.File
import org.json.JSONObject

private val ProcessStartedAt = System.currentTimeMillis()

data class ProjectRecord(
    val id: String,
    val slug: String,
    val name: String,
    val tab: TemplateTab,
    val templateIndex: Int,
    val templateName: String,
    val config: ProjectConfig,
    val createdAt: Long,
    val lastOpened: Long,
    /**
     * A project that came from somewhere else (a clone, an import). Its package and
     * settings are read from its own build files and are never rewritten.
     */
    val imported: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("tab", tab.name)
        put("templateIndex", templateIndex)
        put("templateName", templateName)
        put("config", config.encode())
        put("createdAt", createdAt)
        put("lastOpened", lastOpened)
        put("imported", imported)
    }.toString()

    companion object {
        fun fromJson(slug: String, raw: String): ProjectRecord? = runCatching {
            val json = JSONObject(raw)
            ProjectRecord(
                id = json.optString("id").takeIf { it.isNotBlank() }
                    ?: ProjectIdentity.legacyId(slug),
                slug = slug,
                name = json.getString("name"),
                tab = TemplateTab.valueOf(json.getString("tab")),
                templateIndex = json.getInt("templateIndex"),
                templateName = json.getString("templateName"),
                config = ProjectConfig.decode(json.getString("config")),
                createdAt = json.getLong("createdAt"),
                lastOpened = json.getLong("lastOpened"),
                imported = json.optBoolean("imported", false),
            )
        }.getOrNull()
    }
}

class ProjectRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = ProjectStore(context)
    private val registry = ProjectRegistry(File(context.filesDir, "project-registry.json"))

    init {
        registry.discardPendingBefore(ProcessStartedAt)
    }

    fun listProjects(): List<ProjectRecord> =
        store.listSlugs()
            .mapNotNull { slug ->
                val raw = runCatching {
                    store.readText(slug, ProjectStore.METADATA)
                }.getOrNull() ?: return@mapNotNull null
                val record = ProjectRecord.fromJson(slug, raw) ?: return@mapNotNull null
                val packageName = runCatching { packageOf(record) }.getOrNull()
                registry.ensure(record.id, record.slug, record.name, packageName)
                if (!hasStoredId(raw)) {
                    runCatching { store.createFile(slug, ProjectStore.METADATA, record.toJson()) }
                }
                record
            }
            .sortedByDescending { it.lastOpened }

    fun find(slug: String): ProjectRecord? = listProjects().firstOrNull { it.slug == slug }

    fun findById(id: String): ProjectRecord? = listProjects().firstOrNull { it.id == id }

    fun create(
        name: String,
        selection: SelectedTemplate,
        config: ProjectConfig,
        now: Long = System.currentTimeMillis(),
    ): ProjectRecord {
        store.ensureRoot()
        val cleanName = cleanName(name)
        val id = ProjectIdentity.newId()
        val slug = uniqueSlug(cleanName, id)
        val templateName = templateNameFor(selection)
        val packageName = config.packageName.takeIf { it.isNotBlank() }
        registry.begin(id, slug, cleanName, packageName)
        try {
            Scaffold.filesFor(cleanName, selection, config).forEach { file ->
                store.createFile(slug, file.path, file.content)
            }

            val record = ProjectRecord(
                id = id,
                slug = slug,
                name = cleanName,
                tab = selection.tab,
                templateIndex = selection.index,
                templateName = templateName,
                config = config,
                createdAt = now,
                lastOpened = now,
            )
            store.createFile(slug, ProjectStore.METADATA, record.toJson())
            registry.commit(id)
            return record
        } catch (failure: Throwable) {
            registry.cancel(id)
            runCatching { store.deleteProject(slug) }
            throw failure
        }
    }

    fun delete(slug: String): Boolean {
        if (!store.exists(slug)) return false
        val record = find(slug)
        if (!store.deleteProject(slug)) return false
        record?.let {
            OpenCodeSessions.forget(appContext, it.id, store.projectDir(slug).absolutePath)
            registry.markDeleted(it.id)
        }
        return true
    }

    fun deleteById(id: String): Boolean {
        val record = findById(id) ?: return false
        return delete(record.slug)
    }

    /**
     * Clones a repository into a fresh project folder and registers the result, in that order.
     *
     * JGit refuses to clone into a non-empty directory, so the folder is claimed and left empty
     * while [clone] fills it, and only then is the record written — which is what puts the project
     * on the start screen. A failed clone removes the folder again, so a half-written repository
     * never shows up as a project or gets cloned into on the next attempt.
     */
    fun importCloned(
        name: String,
        now: Long = System.currentTimeMillis(),
        clone: (target: java.io.File) -> Unit,
    ): ProjectRecord {
        store.ensureRoot()
        val cleanName = cleanName(name)
        val id = ProjectIdentity.newId()
        val slug = uniqueSlug(cleanName, id)
        val target = store.projectDir(slug)
        registry.begin(id, slug, cleanName, packageName = null)
        target.mkdirs()
        try {
            clone(target)
            val record = ProjectRecord(
                id = id,
                slug = slug,
                name = cleanName,
                tab = TemplateTab.Web,
                templateIndex = 0,
                templateName = "",
                config = ProjectConfig(),
                createdAt = now,
                lastOpened = now,
                imported = true,
            )
            store.createFile(slug, ProjectStore.METADATA, record.toJson())
            packageOf(record)?.takeIf { it.isNotBlank() }?.let { registry.setPackage(id, it) }
            registry.commit(id)
            return record
        } catch (failure: Throwable) {
            registry.cancel(id)
            target.deleteRecursively()
            throw failure
        }
    }

    /** Rewrites the stored config (project settings); the files are the caller's job. */
    fun updateConfig(slug: String, config: ProjectConfig): ProjectRecord? {
        val record = find(slug) ?: return null
        config.packageName.takeIf { it.isNotBlank() }?.let { packageName ->
            if (packageName != record.config.packageName) {
                registry.setPackage(record.id, packageName)
            }
        }
        val updated = record.copy(config = config)
        store.createFile(slug, ProjectStore.METADATA, updated.toJson())
        return updated
    }

    /**
     * Changes the label only. The folder, package, UUID, build cache and terminal history are
     * deliberately untouched; the old name stays reserved so a different project cannot take it.
     */
    fun renameById(id: String, newName: String): ProjectRecord? {
        val record = findById(id) ?: return null
        val clean = cleanName(newName)
        if (clean == record.name) return record
        registry.rename(id, clean)
        val updated = record.copy(name = clean)
        store.createFile(record.slug, ProjectStore.METADATA, updated.toJson())
        return updated
    }

    fun rename(slug: String, newName: String): ProjectRecord? {
        val record = find(slug) ?: return null
        return renameById(record.id, newName)
    }

    /**
     * The package a project builds under. A CloseCode-created project says it in its
     * record; an imported one is read from its own files, because the record's default
     * package is not its name.
     */
    fun packageOf(record: ProjectRecord): String? {
        if (!record.imported && record.tab == TemplateTab.App) {
            record.config.packageName.takeIf { it.isNotBlank() }?.let { return it }
        }
        val dir = store.projectDir(record.slug)
        val manifest = File(dir, "app/src/main/AndroidManifest.xml")
        if (manifest.isFile) {
            val text = runCatching { manifest.readText() }.getOrNull()
            if (text != null) {
                com.closecode.build.TemplateContract.manifestPackage(text)?.let { return it }
            }
        }
        return com.closecode.build.GradleReader.read(dir).packageName
    }

    fun touch(slug: String, now: Long = System.currentTimeMillis()): ProjectRecord? {
        val record = find(slug) ?: return null
        return touch(record, now)
    }

    fun touchById(id: String, now: Long = System.currentTimeMillis()): ProjectRecord? {
        val record = findById(id) ?: return null
        return touch(record, now)
    }

    private fun touch(record: ProjectRecord, now: Long): ProjectRecord {
        val updated = record.copy(lastOpened = now)
        store.createFile(record.slug, ProjectStore.METADATA, updated.toJson())
        return updated
    }

    fun projectDir(slug: String): java.io.File = store.projectDir(slug)

    fun nameTaken(name: String, exceptId: String? = null): Boolean =
        registry.nameTaken(name, exceptId)

    fun packageTaken(packageName: String, exceptId: String? = null): Boolean =
        registry.packageTaken(packageName, exceptId)

    /** Every package ever reserved, including projects that have since been deleted. */
    fun reservedPackages(exceptId: String? = null): Set<String> =
        registry.reservedPackages(exceptId)

    /**
     * True when a project folder already exists for [name]. The clone screen asks before
     * cloning into it, because landing in "name-2" by silent rename is a surprise. Reserved
     * names and slugs count too: a deleted project's folder must not be silently reused.
     */
    fun folderTaken(name: String): Boolean {
        val slug = slugify(name)
        return store.projectDir(slug).exists() ||
            registry.nameTaken(name) ||
            registry.slugTaken(slug)
    }

    /** Where every project on this device lives. Shown, not edited, on the settings screen. */
    fun projectsDir(): java.io.File = store.projectsDir()

    fun readFile(slug: String, relative: String): String = store.readText(slug, relative)

    /** Raw bytes, used by Preview to decode images. Must be called off the main thread. */
    fun readFileBytes(slug: String, relative: String): ByteArray = store.readBytes(slug, relative)

    fun writeFile(slug: String, relative: String, content: String) =
        store.writeText(slug, relative, content)

    fun createFile(slug: String, relative: String, content: String) =
        store.createFile(slug, relative, content)

    fun createFolder(slug: String, relative: String) = store.createFolder(slug, relative)

    fun deletePath(slug: String, relative: String) = store.deletePath(slug, relative)

    /** Renames the leaf of [relative]. Returns the new path so callers can follow the file. */
    fun renamePath(slug: String, relative: String, newName: String): String =
        store.renamePath(slug, relative, newName)

    /** Moves [relative] to a project-relative [destination]. Returns where it landed. */
    fun movePath(slug: String, relative: String, destination: String): String =
        store.movePath(slug, relative, destination)

    /** Copies [relative] to a project-relative [destination]. Returns where the copy is. */
    fun copyPath(slug: String, relative: String, destination: String): String =
        store.copyPath(slug, relative, destination)

    fun tree(slug: String): List<ProjectFile> = store.readTree(slug)

    /** The record for a path just created, without walking the tree to rediscover it. */
    fun entryFor(slug: String, relative: String, isFolder: Boolean): ProjectFile =
        store.entryFor(slug, relative, isFolder)

    private fun templateNameFor(selection: SelectedTemplate): String =
        com.closecode.ui.model.Templates[selection.tab]
            .orEmpty()
            .getOrNull(selection.index)
            ?.name
            .orEmpty()

    private fun uniqueSlug(name: String, id: String): String {
        val base = slugify(name)
        var candidate = base
        var counter = 2
        // Checks the folder as well as the record: a clone that fails leaves a directory behind
        // with no `.closecode.json` in it, and the next project must not be created inside it.
        // Registry slugs are permanent too, so a deleted project's folder is never reused.
        while (store.exists(candidate) ||
            store.projectDir(candidate).exists() ||
            registry.slugTaken(candidate, id)
        ) {
            candidate = "$base-$counter"
            counter++
        }
        return candidate
    }

    private fun cleanName(value: String): String {
        val name = value.trim()
        if (name.isEmpty()) throw ProjectConflictException("Enter a project name.")
        if (name.length > 80) throw ProjectConflictException("Project names are limited to 80 characters.")
        if (name.any { it.isISOControl() }) {
            throw ProjectConflictException("That project name contains an illegal character.")
        }
        return name
    }

    private fun hasStoredId(raw: String): Boolean =
        runCatching { JSONObject(raw).has("id") }.getOrDefault(false)

    private fun slugify(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "project" }
}