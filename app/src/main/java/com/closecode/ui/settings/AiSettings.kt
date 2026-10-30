package com.closecode.ui.settings

import com.closecode.ai.OpenCodeAiConfig
import java.io.File

/**
 * The brain of the AI settings page, kept free of Compose so the decisions are testable:
 * which action a picker writes next, what a skill file must look like, and where skills live.
 *
 * The configuration file itself is read and written by [OpenCodeAiConfig]; the page composes
 * these same rules around it.
 */
object AiSettings {

    /**
     * The action a picker puts next for a tool. OpenCode's own default is `allow`, so the
     * walk is: untouched → ask → deny → untouched, and the key disappears each time the
     * walk arrives back at OpenCode's default.
     */
    fun actionAfter(current: String): String = when (current) {
        OpenCodeAiConfig.ASK -> OpenCodeAiConfig.DENY
        OpenCodeAiConfig.DENY -> ""
        else -> OpenCodeAiConfig.ASK
    }

    /** The words the page shows for a tool with [action], where "" is OpenCode's own default. */
    fun actionLabel(action: String, default: String = "Default"): String = when (action) {
        OpenCodeAiConfig.ALLOW -> "Allow"
        OpenCodeAiConfig.ASK -> "Ask"
        OpenCodeAiConfig.DENY -> "Deny"
        else -> default
    }

    /**
     * What is wrong with the skill text a reader is about to save, or null when it is fine.
     *
     * OpenCode documents the rules: the frontmatter needs `name` and `description`, the name
     * must be lowercase with single hyphens, and its length rules apply to the description.
     */
    fun skillProblem(text: String): String? {
        val name = frontValue(text, "name")
            ?: return "The skill has no name in its frontmatter."
        if (!NAME.matches(name)) {
            return "The name must be lowercase and single hyphens only (like closecode)."
        }
        val description = frontValue(text, "description")
            ?: return "The skill has no description in its frontmatter."
        if (description.isBlank()) return "The description is empty."
        if (description.length > MAX_DESCRIPTION) {
            return "The description is longer than $MAX_DESCRIPTION characters."
        }
        return null
    }

    /** The skill id [text] names, which must be the folder the file lives in. */
    fun skillName(text: String): String = frontValue(text, "name").orEmpty()

    /** The `description` frontmatter value, with folded lines back to single spaces. */
    fun skillDescription(text: String): String = frontValue(text, "description").orEmpty()

    /**
     * Lists the project skill files in [projects] and the global skill directory OpenCode
     * reads from [configHome]. Sorted by id; a project's files come before global ones.
     */
    fun skillFiles(projects: List<File>, configHome: File?): List<SkillFile> =
        projects.asSequence()
            .flatMap { project -> listSkills(File(project, ".opencode/skills")) }
            .plus(configHome?.let { listSkills(File(it, "opencode/skills")) }.orEmpty())
            .sortedBy { it.id.lowercase() }
            .toList()

    data class SkillFile(
        /** The skill id, which is the folder name the file lives in. */
        val id: String,
        val file: File,
    )

    /** The folder a skill with [id] belongs in, under OpenCode's own location. */
    fun skillDirectory(project: File, id: String): File =
        File(project, ".opencode/skills/$id")

    /** The text a brand new skill starts with: frontmatter only, nothing filled in. */
    fun skillSkeleton(): String = """
        ---
        name:
        description:
        ---

        What should the agent do, and when should it do it?
    """.trimIndent() + "\n"

    /** Regex for the names OpenCode accepts for a skill. */
    private val NAME = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /** The description length OpenCode documents for a skill. */
    private const val MAX_DESCRIPTION = 1024

    /** Every skill folder OpenCode reads from one directory. */
    private fun listSkills(skillsDirectory: File): List<SkillFile> {
        val folders = skillsDirectory.takeIf { it.isDirectory }
            ?.listFiles { file: File -> file.isDirectory }
            .orEmpty()
        return folders.mapNotNull { folder ->
            val file = File(folder, "SKILL.md").takeIf { it.isFile }
                ?: return@mapNotNull null
            SkillFile(folder.name, file)
        }
    }

    /** One frontmatter line's value, folding indented continuation lines into single spaces. */
    private fun frontValue(text: String, key: String): String? {
        val lines = text.split("\n")
        var inBlock = false
        var value: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "---") {
                if (inBlock) break
                inBlock = true
                continue
            }
            if (!inBlock) continue
            val first = value
            if (first == null) {
                if (line.startsWith("$key:") || line.startsWith(" $key:")) {
                    value = line.substringAfter(":").trim()
                }
            } else if (line.startsWith(" ")) {
                value = "$first $trimmed"
            } else break
        }
        return value
    }
}
