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
     * The registry's rule: a deleted project keeps its identity reserved, so a name it held
     * is not handed to another project. The screen and the creation path both follow it.
     */
    @Test
    fun aDeletedProjectKeepsItsNameReserved() {
        val registry = registry()
        registry.ensure("id-1", "one", "One", "com.closecode.one")
        registry.markDeleted("id-1")

        assertTrue(registry.packageTaken("com.closecode.one"))
        assertEquals("One", registry.packageHolder("com.closecode.one"))
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
