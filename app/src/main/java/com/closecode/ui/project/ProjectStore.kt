package com.closecode.ui.project

import android.content.Context
import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import java.io.File

class ProjectStore(context: Context) {

    private val projectsRoot: File = File(context.filesDir, "projects")

    fun projectsDir(): File = projectsRoot

    fun projectDir(slug: String): File = File(projectsRoot, slug)

    fun ensureRoot() {
        if (!projectsRoot.exists()) projectsRoot.mkdirs()
    }

    fun exists(slug: String): Boolean = File(projectDir(slug), METADATA).isFile

    fun listSlugs(): List<String> =
        projectsRoot.listFiles()?.filter { File(it, METADATA).isFile }?.map { it.name } ?: emptyList()

    fun readText(slug: String, relative: String): String {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        if (!file.isFile) throw ProjectPathException("Not a file: $relative")
        return ProjectSafety.readTextOrThrow(file)
    }

    fun writeText(slug: String, relative: String, content: String) {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        ProjectSafety.writeAtomic(file, content)
    }

    fun deletePath(slug: String, relative: String): Boolean {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun createFile(slug: String, relative: String, content: String) {
        val file = ProjectSafety.resolveInProject(projectDir(slug), relative)
        ProjectSafety.ensureWritable(file)
        ProjectSafety.writeAtomic(file, content)
    }

    fun readTree(slug: String): List<ProjectFile> {
        val root = projectDir(slug)
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<ProjectFile>()
        walk(root, depth = 0, into = out)
        return out
    }

    private fun walk(dir: File, depth: Int, into: MutableList<ProjectFile>, prefix: String = "") {
        val children = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: return

        for (child in children) {
            if (ProjectSafety.isHiddenName(child.name)) continue
            val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                into.add(
                    ProjectFile(
                        name = child.name,
                        kind = FileKind.Folder,
                        language = "",
                        depth = depth,
                        expandable = true,
                        path = relative,
                    )
                )
                walk(child, depth + 1, into, relative)
            } else {
                into.add(
                    ProjectFile(
                        name = child.name,
                        kind = kindOf(child.name),
                        language = languageOf(child.name),
                        depth = depth,
                        path = relative,
                    )
                )
            }
        }
    }

    private fun kindOf(name: String): FileKind = when {
        name.endsWith(".kt") || name.endsWith(".kts") -> FileKind.Kotlin
        name.endsWith(".xml") -> FileKind.Xml
        name.endsWith(".html") -> FileKind.Html
        name.endsWith(".css") -> FileKind.Css
        name.endsWith(".js") -> FileKind.Js
        name.endsWith(".json") -> FileKind.Json
        name.endsWith(".csv") -> FileKind.Csv
        name.endsWith(".md") -> FileKind.Markdown
        else -> FileKind.Gradle
    }

    private fun languageOf(name: String): String = when {
        name.endsWith(".kt") -> "Kotlin"
        name.endsWith(".kts") -> "Gradle Kotlin"
        name.endsWith(".xml") -> "XML"
        name.endsWith(".html") -> "HTML"
        name.endsWith(".css") -> "CSS"
        name.endsWith(".js") -> "JavaScript"
        name.endsWith(".json") -> "JSON"
        name.endsWith(".csv") -> "CSV"
        name.endsWith(".md") -> "Markdown"
        name.endsWith(".properties") -> "Properties"
        else -> "Text"
    }

    fun deleteProject(slug: String): Boolean {
        val dir = projectDir(slug)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    companion object {
        const val METADATA = ".closecode.json"
    }
}