package com.closecode.debug.probe

import android.content.Context
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.project.ProjectIdentity
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.project.ProjectStore

internal object BenchProject {

    const val NAME = "SORA-PROBE-DELETE-ME"

    fun ensure(context: Context, repository: ProjectRepository): ProjectRecord {
        val existing = repository.listProjects().firstOrNull { it.name == NAME }
        val record = existing ?: create(context, repository)
        ProbeSize.values().forEach { size ->
            repository.createFile(record.slug, size.fileName, ProbeContent.text(size))
        }
        return repository.touch(record.slug) ?: record
    }

    fun delete(repository: ProjectRepository): Int {
        val matches = repository.listProjects().filter { it.name == NAME }
        matches.forEach { repository.delete(it.slug) }
        return matches.size
    }

    private fun create(context: Context, repository: ProjectRepository): ProjectRecord {
        val created = runCatching {
            repository.create(
                name = NAME,
                selection = SelectedTemplate(TemplateTab.App, 0),
                config = ProjectConfig(packageName = "com.closecode.soraprobe"),
            )
        }.getOrNull()
        if (created != null) return created
        return createManually(context)
    }

    private fun createManually(context: Context): ProjectRecord {
        val store = ProjectStore(context)
        store.ensureRoot()
        val id = ProjectIdentity.newId()
        val base = "sora-probe-delete-me"
        val slug = if (store.projectDir(base).exists()) "$base-${id.take(8)}" else base
        val now = System.currentTimeMillis()
        val record = ProjectRecord(
            id = id,
            slug = slug,
            name = NAME,
            tab = TemplateTab.App,
            templateIndex = 0,
            templateName = "Blank app",
            config = ProjectConfig(packageName = "com.closecode.soraprobe.${id.take(8)}"),
            createdAt = now,
            lastOpened = now,
        )
        ProbeSize.values().forEach { size ->
            store.createFile(slug, size.fileName, ProbeContent.text(size))
        }
        store.createFile(slug, ProjectStore.METADATA, record.toJson())
        return record
    }
}
