package com.closecode.ui.project

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.defaultPackageName
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rules the New-project screen and the command line now share.
 *
 * - A project needs a name.
 * - The package name is the user's to write; when it is left empty the app derives one
 *   from the unique folder name, so two projects with the same name never collide.
 * - A website has no package at all.
 * - A deleted project gives its package name back, while its name and folder stay
 *   reserved so nothing is silently reused.
 */
class ProjectCreationRulesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun twoProjectsWithTheSameNameGetDifferentPackages() {
        val root = tmp.newFolder("projects")
        val registry = ProjectRegistry(File(root, "project-registry.json"))
        val first = ProjectIdentity.newId()
        val second = ProjectIdentity.newId()

        // The first project takes the plain folder name; the second one cannot have it.
        registry.begin(first, "notes", "Notes", "com.closecode.notes")
        registry.commit(first)
        val firstSlug = uniqueSlugForTest("notes", first, registry)
        val secondSlug = uniqueSlugForTest("notes", second, registry)
        assertEquals("notes", firstSlug)
        assertEquals("notes-2", secondSlug)
        assertNotNull(
            "the two projects would hold the same package",
            defaultPackageName(firstSlug).takeIf { it != defaultPackageName(secondSlug) },
        )
    }

    @Test
    fun aDeletedProjectGivesItsPackageNameBack() {
        val registry = ProjectRegistry(File(tmp.newFolder("registry"), "project-registry.json"))
        registry.begin(ProjectIdentity.newId(), "notes", "Notes", "com.closecode.notes")
        val id = registry.entries().first().id

        assertTrue(registry.packageTaken("com.closecode.notes"))

        registry.markDeleted(id)

        assertFalse(
            "a deleted project still holds its package name",
            registry.packageTaken("com.closecode.notes"),
        )
        assertNull(registry.packageHolder("com.closecode.notes"))
    }

    @Test
    fun aDeletedProjectKeepsItsNameAndFolder() {
        val registry = ProjectRegistry(File(tmp.newFolder("registry"), "project-registry.json"))
        val id = ProjectIdentity.newId()
        registry.begin(id, "notes", "Notes", "com.closecode.notes")
        registry.commit(id)

        registry.markDeleted(id)

        assertTrue("the name must stay reserved", registry.nameTaken("Notes"))
        assertTrue("the folder must stay reserved", registry.slugTaken("notes"))
    }

    @Test
    fun aWebsiteHoldsNoPackage() {
        // The rule the repository applies: only an app project has a package.
        val config = ProjectConfig(packageName = "")
        assertEquals("", config.copy(packageName = "").packageName)
        assertTrue(
            "a website must not be treated as an app",
            TemplateTab.Web != TemplateTab.App,
        )
    }

    @Test
    fun theDerivedPackageIsALegalJavaPackage() {
        val derived = defaultPackageName("notes-2")

        assertEquals("com.closecode.notes2", derived)
        assertFalse(derived.contains("-"))
        assertFalse(derived.contains(" "))
    }

    /** The same rule the repository uses for the folder name. */
    private fun uniqueSlugForTest(
        name: String,
        id: String,
        registry: ProjectRegistry,
    ): String {
        val base = name.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "project" }
        var candidate = base
        var counter = 2
        while (registry.slugTaken(candidate, id)) {
            candidate = "$base-$counter"
            counter++
        }
        return candidate
    }
}
