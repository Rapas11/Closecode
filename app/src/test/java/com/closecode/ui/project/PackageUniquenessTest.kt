package com.closecode.ui.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Bug 2 of the build-90 brief: a package name that is used nowhere must be accepted, and
 * one that is used must be refused with the project that holds it named.
 *
 * The comparison is on the **whole** package name. A comparison on anything shorter — a
 * segment, a fragment, a substring — matches every package that starts with `com`, and
 * refuses every name on the phone.
 */
class PackageUniquenessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun registry(): ProjectRegistry =
        ProjectRegistry(File(tmp.newFolder("registry"), "project-registry.json"))

    @Test
    fun aNameUsedNowhereIsFree() {
        val registry = registry()
        registry.ensure("id-1", "one", "One", "com.closecode.one")
        registry.ensure("id-2", "two", "Two", "com.closecode.two")

        assertFalse(registry.packageTaken("com.rrbndbbdhdh.dibsh"))
        assertNull(registry.packageHolder("com.rrbndbbdhdh.dibsh"))
    }

    @Test
    fun aNameThatIsUsedIsTakenAndTheHolderIsNamed() {
        val registry = registry()
        registry.ensure("id-1", "one", "My app", "com.closecode.one")

        assertTrue(registry.packageTaken("com.closecode.one"))
        assertEquals("My app", registry.packageHolder("com.closecode.one"))
    }

    @Test
    fun theWholeNameIsComparedAndNotAPartOfIt() {
        val registry = registry()
        registry.ensure("id-1", "one", "One", "com.closecode.one")

        // A name that starts the same way is a different name.
        assertFalse(registry.packageTaken("com.closecode.one.two"))
        assertFalse(registry.packageTaken("com.closecode.on"))
        assertFalse(registry.packageTaken("com.closecode"))
        assertFalse(registry.packageTaken("com"))
    }

    /**
     * The rule changed with build 92: a deleted project keeps its **name** reserved, so a
     * project with the same name is never silently put in the old folder, but it **releases
     * its package name**, because a package belongs to a project that exists and a name that
     * is used nowhere must be accepted.
     */
    @Test
    fun aDeletedProjectKeepsItsNameButReleasesItsPackage() {
        val registry = registry()
        registry.ensure("id-1", "one", "One", "com.closecode.one")
        registry.markDeleted("id-1")

        assertTrue(registry.nameTaken("One"))
        assertFalse(registry.packageTaken("com.closecode.one"))
        assertNull(registry.packageHolder("com.closecode.one"))
    }

    @Test
    fun theProjectBeingCreatedIsNotComparedWithItself() {
        val registry = registry()
        registry.ensure("id-1", "one", "One", "com.closecode.one")

        assertFalse(registry.packageTaken("com.closecode.one", exceptId = "id-1"))
    }

    /**
     * The creation path refuses a name another project holds — the same rule the screen
     * applies, so the two front ends cannot disagree.
     */
    @Test
    fun creatingAProjectWithANameInUseIsRefusedAndNamesTheHolder() {
        val registry = registry()
        registry.begin("id-1", "one", "One", "com.closecode.same")
        registry.commit("id-1")

        val failure = runCatching {
            registry.begin("id-2", "two", "Two", "com.closecode.same")
        }.exceptionOrNull()

        assertTrue(failure is ProjectConflictException)
        assertEquals(
            "That package name is already used by \"One\". Pick a unique one.",
            failure?.message,
        )
    }
}
