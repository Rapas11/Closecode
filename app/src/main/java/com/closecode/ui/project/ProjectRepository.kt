package com.closecode.ui.project

import android.content.Context
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.ProjectFile
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import org.json.JSONObject

data class ProjectRecord(
    val slug: String,
    val name: String,
    val tab: TemplateTab,
    val templateIndex: Int,
    val templateName: String,
    val config: ProjectConfig,
    val createdAt: Long,
    val lastOpened: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("name", name)
        put("tab", tab.name)
        put("templateIndex", templateIndex)
        put("templateName", templateName)
        put("config", config.encode())
        put("createdAt", createdAt)
        put("lastOpened", lastOpened)
    }.toString()

    companion object {
        fun fromJson(slug: String, raw: String): ProjectRecord? = runCatching {
            val json = JSONObject(raw)
            ProjectRecord(
                slug = slug,
                name = json.getString("name"),
                tab = TemplateTab.valueOf(json.getString("tab")),
                templateIndex = json.getInt("templateIndex"),
                templateName = json.getString("templateName"),
                config = ProjectConfig.decode(json.getString("config")),
                createdAt = json.getLong("createdAt"),
                lastOpened = json.getLong("lastOpened"),
            )
        }.getOrNull()
    }
}

class ProjectRepository(context: Context) {

    private val store = ProjectStore(context)

    fun listProjects(): List<ProjectRecord> =
        store.listSlugs()
            .mapNotNull { slug ->
                val raw = runCatching {
                    store.readText(slug, ProjectStore.METADATA)
                }.getOrNull() ?: return@mapNotNull null
                ProjectRecord.fromJson(slug, raw)
            }
            .sortedByDescending { it.lastOpened }

    fun find(slug: String): ProjectRecord? = listProjects().firstOrNull { it.slug == slug }

    fun create(
        name: String,
        selection: SelectedTemplate,
        config: ProjectConfig,
        now: Long = System.currentTimeMillis(),
    ): ProjectRecord {
        store.ensureRoot()
        val slug = uniqueSlug(name)
        val templateName = templateNameFor(selection)

        Scaffold.filesFor(name, selection, config).forEach { file ->
            store.createFile(slug, file.path, file.content)
        }

        val record = ProjectRecord(
            slug = slug,
            name = name,
            tab = selection.tab,
            templateIndex = selection.index,
            templateName = templateName,
            config = config,
            createdAt = now,
            lastOpened = now,
        )
        store.createFile(slug, ProjectStore.METADATA, record.toJson())
        return record
    }

    fun delete(slug: String): Boolean {
        if (!store.exists(slug)) return false
        return store.deleteProject(slug)
    }

    fun touch(slug: String, now: Long = System.currentTimeMillis()): ProjectRecord? {
        val record = find(slug) ?: return null
        val updated = record.copy(lastOpened = now)
        store.createFile(slug, ProjectStore.METADATA, updated.toJson())
        return updated
    }

    fun readFile(slug: String, relative: String): String = store.readText(slug, relative)

    fun writeFile(slug: String, relative: String, content: String) =
        store.writeText(slug, relative, content)

    fun tree(slug: String): List<ProjectFile> = store.readTree(slug)

    private fun templateNameFor(selection: SelectedTemplate): String =
        com.closecode.ui.model.Templates[selection.tab]
            .orEmpty()
            .getOrNull(selection.index)
            ?.name
            .orEmpty()

    private fun uniqueSlug(name: String): String {
        val base = slugify(name)
        var candidate = base
        var counter = 2
        while (store.exists(candidate)) {
            candidate = "$base-$counter"
            counter++
        }
        return candidate
    }

    private fun slugify(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "project" }
}